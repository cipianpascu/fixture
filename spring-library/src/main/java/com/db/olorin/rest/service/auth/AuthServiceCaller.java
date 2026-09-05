package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.exception.AuthServiceException;
import com.db.olorin.rest.exception.AuthenticationDeniedException;
import com.db.olorin.rest.exception.AuthorizationDeniedException;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import com.db.olorin.rest.service.TlsContextFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/** Blocking auth-service caller with the same TLS and Cloud Run behavior as backend calls. */
public final class AuthServiceCaller {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ResolvedAuthServiceConfig config;
    private final CloudRunIdTokenProvider tokens;
    private final HttpClient client;
    private final String audience;
    public AuthServiceCaller(ResolvedAuthServiceConfig config, TlsContextFactory tls, CloudRunIdTokenProvider tokens) {
        this.config=config; this.tokens=tokens;
        HttpClient.Builder builder=HttpClient.newBuilder().connectTimeout(config.timeout()).version(HttpClient.Version.HTTP_1_1);
        tls.createServiceSslContext(config.tlsProfile()).ifPresent(builder::sslContext);
        this.client=builder.build();
        String type=config.securityType().orElse("none");
        this.audience=("cloudrun".equalsIgnoreCase(type)||"cloud_run".equalsIgnoreCase(type))
            ? CloudRunAudienceResolver.resolveAudience(config.serviceUrl(),config.securityConfig(),"auth service '"+config.name()+"'") : null;
        if (audience == null && !type.isBlank() && !"none".equalsIgnoreCase(type)) throw new ProxyConfigurationException("Unsupported security type '"+type+"' for auth service '"+config.name()+"'");
    }
    public <T>T postJson(String path,Object body,Class<T> type){try{return send(path,JSON.writeValueAsString(body),"application/json",type);}catch(IOException e){throw new AuthServiceException("Cannot serialize auth request",e);}}
    public <T>T postForm(String path,Map<String,String> form,Class<T> type){return send(path,form.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(Collectors.joining("&")),"application/x-www-form-urlencoded",type);}
    private <T>T send(String path,String body,String contentType,Class<T> type) {
        try {
            HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(url(path))).timeout(config.timeout()).header("Accept","application/json").header("Content-Type",contentType).POST(HttpRequest.BodyPublishers.ofString(body));
            if(audience!=null)request.header("X-Serverless-Authorization","Bearer "+tokens.getIdToken(audience));
            HttpResponse<String> response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==401)throw new AuthenticationDeniedException(description(response));
            if(response.statusCode()==403)throw new AuthorizationDeniedException(description(response));
            if(response.statusCode()<200||response.statusCode()>=300)throw new AuthServiceException(description(response));
            if(type==String.class)return type.cast(response.body());
            if(response.body()==null||response.body().isBlank())throw new AuthServiceException("Auth service returned an empty response body");
            if(type==JsonNode.class)return type.cast(JSON.readTree(response.body()));
            return JSON.readValue(response.body(),type);
        } catch(AuthServiceException e){throw e;} catch(IOException e){throw new AuthServiceException("Failed to call auth service '"+config.name()+"'",e);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AuthServiceException("Auth service request was interrupted",e);}
    }
    private String url(String path){if(path==null||path.isBlank())throw new AuthServiceException("Auth service path must not be blank");if(path.startsWith("http://")||path.startsWith("https://"))return path;return config.serviceUrl().replaceAll("/$","")+"/"+path.replaceFirst("^/","");}
    private String description(HttpResponse<String> response){return "Auth service '%s' returned HTTP %d%s".formatted(config.name(),response.statusCode(),response.body()==null||response.body().isBlank()?"":": "+response.body());}
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
