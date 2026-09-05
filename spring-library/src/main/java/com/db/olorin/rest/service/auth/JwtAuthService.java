package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.auth.AuthRequest;
import com.db.olorin.rest.auth.AuthTokens;
import com.db.olorin.rest.auth.AuthzRequest;
import com.db.olorin.rest.auth.AuthzTokens;
import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.AuthResponseMappingException;
import com.db.olorin.rest.exception.AuthServiceException;
import com.db.olorin.rest.exception.AuthenticationRequiredException;
import com.db.olorin.rest.exception.AuthorizationDeniedException;
import com.db.olorin.rest.model.ProxyRequestContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** JWT/authz flow using the legacy auth-request and authz-request settings. */
public final class JwtAuthService implements AuthService {
    private final AuthServiceCallerFactory callers; private final AuthzTokenCache cache;
    private final ProxyProperties.BackendDefinition backend;
    public JwtAuthService(ProxyProperties.BackendDefinition backend, AuthServiceCallerFactory callers, AuthzTokenCache cache){this.backend=backend;this.callers=callers;this.cache=cache;}
    @Override public void enrichHeaders(ProxyProperties.BackendDefinition ignored,ProxyRequestContext request,Map<String,String> headers,String body) {
        AuthTokens auth=backend.authRequest().map(config->auth(request,config)).orElse(null);
        AuthzTokens authz=backend.authzRequest().map(config->authz(request,config)).orElse(null);
        if(auth==null&&authz==null)throw new AuthServiceException("JWT authentication requires auth-request or authz-request");
        if(auth!=null&&auth.getDisallowedPss()!=null&&!auth.getDisallowedPss().isEmpty())throw new AuthorizationDeniedException("Auth service disallowed requested pss: "+auth.getDisallowedPss());
        apply(auth,authz,headers);
    }
    @Override public void enrichHeaders(ProxyRequestContext request,Map<String,String> headers,String body){enrichHeaders(backend,request,headers,body);}
    private AuthTokens auth(ProxyRequestContext request,ProxyProperties.AuthRequestConfig config) {
        String service=config.service().orElse(AuthServiceConfigRegistry.DEFAULT_JWT_SERVICE); ResolvedAuthServiceConfig serviceConfig=callers.getConfig(service);
        if(!serviceConfig.enabled())return null;
        String session=session(request,serviceConfig);
        AuthTokens tokens=callers.get(service).postJson(path(config.path().orElse("/auth/tokens/{sessionId}"),session),new AuthRequest(config.sparteGvo().orElse(null),config.btx().orElse(null),config.pss().orElse(null)),AuthTokens.class);
        if(tokens==null||(blank(tokens.getGlueToken())&&blank(tokens.getAuthZToken())&&blank(tokens.getCustomerAccessToken())))throw new AuthResponseMappingException("Authentication response did not contain any usable token");
        return tokens;
    }
    private AuthzTokens authz(ProxyRequestContext request,ProxyProperties.AuthzRequestConfig config) {
        String service=config.service().orElse(AuthServiceConfigRegistry.DEFAULT_JWT_SERVICE); ResolvedAuthServiceConfig serviceConfig=callers.getConfig(service);
        if(!serviceConfig.enabled())return null;
        String session=session(request,serviceConfig); String branch=config.branchCustomerNumber().map(mapping->scalar(mapping,request)).orElseThrow(()->new AuthenticationRequiredException("Missing branchCustomerNumber for JWT authz flow"));
        Optional<AuthzTokens> existing=serviceConfig.cache().enabled()?cache.get(session,backend.name()):Optional.empty();
        if(existing.isPresent())return existing.get();
        AuthzTokens tokens=callers.get(service).postJson(path(config.path(),session),new AuthzRequest(branch,config.gvoEntitlementsList().orElse(null),config.businessTransactions().orElse(null),config.serviceShopTransactions().orElse(null)),AuthzTokens.class);
        if(tokens==null || noUsableAuthzResult(tokens))throw new AuthResponseMappingException("Authorization response did not contain any usable token or authorization decision");
        List<String> requested=config.serviceShopTransactions().orElse(List.of());
        if(!requested.isEmpty()&&(tokens.getAllowedServiceShopTransactions()==null||!tokens.getAllowedServiceShopTransactions().containsAll(requested)))throw new AuthorizationDeniedException("Authz service disallowed requested serviceShopTransactions");
        if(serviceConfig.cache().enabled()) expiry(tokens, serviceConfig.cache().expirySkew())
            .ifPresent(expiresAt -> cache.put(session,backend.name(),tokens,expiresAt,serviceConfig.cache().maxSize()));
        return tokens;
    }
    private void apply(AuthTokens auth,AuthzTokens authz,Map<String,String> headers) {
        Map<String,String> config=backend.securityConfig(); String source=config.get("bearer-source");
        if(source!=null){String token=token(auth,authz,source);if(blank(token))throw new AuthResponseMappingException("Configured bearer-source '"+source+"' did not resolve to a token");headers.put(config.getOrDefault("bearer-header","Authorization"),config.getOrDefault("bearer-prefix","Bearer")+" "+token);}
        config.forEach((key,value)->{if(key.startsWith("token-headers.")){String token=token(auth,authz,value);if(blank(token))throw new AuthResponseMappingException("Configured token source '"+value+"' did not resolve");headers.put(key.substring("token-headers.".length()),token);}else if(key.startsWith("static-headers."))headers.put(key.substring("static-headers.".length()),value);});
        if(source==null&&config.keySet().stream().noneMatch(key->key.startsWith("token-headers."))&&auth!=null){if(!blank(auth.getGlueToken()))headers.put("X-Glue-Token",auth.getGlueToken());if(!blank(auth.getAuthZToken()))headers.put("X-Auth-Z-Token",auth.getAuthZToken());if(!blank(auth.getCustomerAccessToken()))headers.put("X-Customer-Access-Token",auth.getCustomerAccessToken());}
    }
    private String token(AuthTokens auth,AuthzTokens authz,String source){return switch(source.toLowerCase()){case "gluetoken","glue_token"->auth==null?null:auth.getGlueToken();case "authztoken","auth_z_token"->auth==null?null:auth.getAuthZToken();case "customeraccesstoken","customer_access_token"->auth==null?null:auth.getCustomerAccessToken();case "authorizationtoken","authorization_token"->authz==null?null:authz.getAuthorizationToken();case "glueaccesstoken","glue_access_token","eidpaccesstoken","eidp_access_token"->authz==null?null:authz.getGlueAccessToken();case "authzcustomeraccesstoken","authz_customer_access_token"->authz==null?null:authz.getCustomerAccessToken();default->null;};}
    private String session(ProxyRequestContext request,ResolvedAuthServiceConfig config){String value=request.header(config.sessionIdHeader());if(blank(value))value=request.cookie(config.sessionIdCookie());if(blank(value))throw new AuthenticationRequiredException("Missing session identifier for JWT-authenticated backend");return value;}
    private String scalar(String mapping,ProxyRequestContext request){if(mapping.startsWith("header:")){String value=request.header(mapping.substring(7));if(blank(value))throw new AuthenticationRequiredException("Missing required header for JWT authz flow");return value;}if(mapping.startsWith("cookie:")){String value=request.cookie(mapping.substring(7));if(blank(value))throw new AuthenticationRequiredException("Missing required cookie for JWT authz flow");return value;}return mapping.startsWith("literal:")?mapping.substring(8):mapping;}
    private String path(String path,String session){return path.replace("{sessionId}",java.net.URLEncoder.encode(session,java.nio.charset.StandardCharsets.UTF_8));}
    private Optional<Instant> expiry(AuthzTokens tokens, java.time.Duration skew) {
        return java.util.stream.Stream.of(tokens.getAuthorizationToken(), tokens.getGlueAccessToken(), tokens.getCustomerAccessToken())
            .filter(token -> !blank(token)).map(this::jwtExpiry).flatMap(Optional::stream).min(Instant::compareTo)
            .map(value -> value.minus(skew));
    }
    private boolean noUsableAuthzResult(AuthzTokens tokens) {
        return blank(tokens.getAuthorizationToken())
            && blank(tokens.getGlueAccessToken())
            && blank(tokens.getCustomerAccessToken())
            && (tokens.getAllowedServiceShopTransactions() == null || tokens.getAllowedServiceShopTransactions().isEmpty());
    }
    private Optional<Instant> jwtExpiry(String token) {
        try {
            String[] parts=token.split("\\."); if(parts.length<2)return Optional.empty();
            JsonNode payload=new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
            return payload.has("exp") ? Optional.of(Instant.ofEpochSecond(payload.get("exp").asLong())) : Optional.empty();
        } catch (RuntimeException | java.io.IOException ignored) { return Optional.empty(); }
    }
    private boolean blank(String value){return value==null||value.isBlank();}
}
