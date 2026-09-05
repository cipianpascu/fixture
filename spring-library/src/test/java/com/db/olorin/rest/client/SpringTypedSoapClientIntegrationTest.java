package com.db.olorin.rest.client;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.SoapFaultException;
import com.db.olorin.rest.service.TlsContextFactory;
import com.sun.net.httpserver.HttpServer;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringTypedSoapClientIntegrationTest {
    private HttpServer server;
    @AfterEach void stop(){if(server!=null)server.stop(0);RequestContextHolder.resetRequestAttributes();}
    @Test void invokesSoap11WithConfiguredActionAndUnmarshalsResponse() throws Exception {
        AtomicReference<String> action=new AtomicReference<>(), body=new AtomicReference<>(); start(exchange->{action.set(exchange.getRequestHeaders().getFirst("SOAPAction"));body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));respond(exchange,200,"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><response><name>Jane</name></response></s:Body></s:Envelope>");});
        Response result=client("1.1","urn:test").exchange("soap",new Request("42"),Response.class);
        assertThat(result.name).isEqualTo("Jane");assertThat(action.get()).isEqualTo("\"urn:test\"");assertThat(body.get()).contains("<id>42</id>");
    }
    @Test void mapsSoapFault() throws Exception {start(exchange->respond(exchange,500,"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><faultstring>broken</faultstring></s:Fault></s:Body></s:Envelope>"));assertThatThrownBy(()->client("1.1",null).exchange("soap",new Request("42"),Response.class)).isInstanceOf(SoapFaultException.class).hasMessageContaining("broken");}
    @Test void invokesSoap12WithActionContentType() throws Exception {AtomicReference<String> contentType=new AtomicReference<>();start(exchange->{contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));respond(exchange,200,"<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body><response><name>Jane</name></response></s:Body></s:Envelope>");});assertThat(client("1.2","urn:soap12").exchange("soap",new Request("42"),Response.class).name).isEqualTo("Jane");assertThat(contentType.get()).contains("application/soap+xml").contains("action=\"urn:soap12\"");}
    @Test void forwardsConfiguredCurrentRequestHeadersAndCookies() throws Exception {
        AtomicReference<String> token=new AtomicReference<>(), cookie=new AtomicReference<>();
        start(exchange->{token.set(exchange.getRequestHeaders().getFirst("x-asm-rctoken"));cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));respond(exchange,200,"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><response><name>Jane</name></response></s:Body></s:Envelope>");});
        MockHttpServletRequest current=new MockHttpServletRequest();current.addHeader("x-asm-rctoken","inbound-token");current.setCookies(new jakarta.servlet.http.Cookie("session","abc"),new jakarta.servlet.http.Cookie("ignore","no"));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(current));
        assertThat(client("1.1",null).exchange("soap",new Request("42"),Response.class).name).isEqualTo("Jane");
        assertThat(token.get()).isEqualTo("inbound-token");assertThat(cookie.get()).isEqualTo("session=abc");
    }
    private void start(com.sun.net.httpserver.HttpHandler handler)throws Exception{server=HttpServer.create(new InetSocketAddress(0),0);server.createContext("/soap",handler);server.start();}
    private SpringTypedSoapClient client(String version,String action){ProxyProperties properties=mock(ProxyProperties.class);ProxyProperties.BackendDefinition backend=mock(ProxyProperties.BackendDefinition.class);ProxyProperties.SoapConfig soap=mock(ProxyProperties.SoapConfig.class);when(properties.backends()).thenReturn(List.of(backend));when(properties.tls()).thenReturn(Optional.empty());when(backend.enabled()).thenReturn(true);when(backend.name()).thenReturn("soap");when(backend.protocol()).thenReturn("soap");when(backend.baseUrl()).thenReturn("http://127.0.0.1:"+server.getAddress().getPort());when(backend.path()).thenReturn("/soap");when(backend.timeout()).thenReturn(Duration.ofSeconds(5));when(backend.httpVersion()).thenReturn("http1.1");when(backend.tlsProfile()).thenReturn(Optional.empty());when(backend.proxy()).thenReturn(Optional.empty());when(backend.forwardHeaders()).thenReturn(List.of("x-asm-rctoken"));when(backend.forwardCookies()).thenReturn(List.of("session"));when(backend.soap()).thenReturn(Optional.of(soap));when(soap.version()).thenReturn(version);when(soap.soapAction()).thenReturn(Optional.ofNullable(action));return new SpringTypedSoapClient(properties,new TlsContextFactory(properties));}
    private void respond(com.sun.net.httpserver.HttpExchange e,int status,String value)throws java.io.IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/xml");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();}
    @XmlRootElement(name="request") @XmlAccessorType(XmlAccessType.FIELD) static class Request{ @XmlElement String id; Request(){} Request(String id){this.id=id;} }
    @XmlRootElement(name="response") @XmlAccessorType(XmlAccessType.FIELD) static class Response{ @XmlElement String name; }
}
