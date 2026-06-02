package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationDeniedException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.AuthorizationDeniedException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.exception.SoapFaultException;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.auth.AuthService;
import com.agent.gateway.proxy.service.auth.AuthServiceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class SoapBackendService {

    private static final String SOAP_11_ENV_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_12_ENV_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String PROTOCOL_SOAP = "soap";
    private static final String AUTH_TYPE_FORM = "form";
    private static final String FORM_MODE_INLINE = "inline";

    @Inject
    AuthServiceFactory authServiceFactory;

    @Inject
    TlsContextFactory tlsContextFactory;

    private final Map<String, HttpClient> httpClients = new ConcurrentHashMap<>();
    private final Map<String, JAXBContext> jaxbContexts = new ConcurrentHashMap<>();

    @Retry(
        maxRetries = 2,
        delay = 200,
        retryOn = {UpstreamProxyException.class, AuthServiceException.class},
        abortOn = {
            ProxyConfigurationException.class,
            AuthenticationRequiredException.class,
            AuthenticationDeniedException.class,
            AuthorizationDeniedException.class,
            SoapFaultException.class
        }
    )
    @CircuitBreaker(
        requestVolumeThreshold = 4,
        failureRatio = 0.5,
        delay = 5000,
        failOn = {UpstreamProxyException.class, AuthServiceException.class},
        skipOn = {
            ProxyConfigurationException.class,
            AuthenticationRequiredException.class,
            AuthenticationDeniedException.class,
            AuthorizationDeniedException.class,
            SoapFaultException.class
        }
    )
    public <T> T invoke(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        Object soapRequest,
        String soapActionOverride,
        Class<T> responseType) {

        validateSoapBackend(backend);

        try {
            String endpoint = backend.baseUrl() + backend.path();
            String version = soapVersion(backend);
            String payloadXml = marshalPayload(soapRequest, responseType);
            String envelope = soapEnvelope(version, payloadXml);

            Map<String, List<String>> headers = buildHeaders(request);
            Map<String, String> authHeaders = ProxyService.flattenHeaders(headers);
            applySoapHeaders(authHeaders, version, resolveSoapAction(backend, soapActionOverride));

            AuthService authService = authServiceFactory.createAuthService(backend);
            authService.enrichHeaders(request, authHeaders, envelope);
            String outboundEnvelope = authService.transformRequestBody(request, authHeaders, envelope);
            if (!envelope.equals(outboundEnvelope)) {
                throw new ProxyConfigurationException(
                    "SOAP backend '%s' does not support auth strategies that transform the outbound body"
                        .formatted(backend.name()));
            }

            headers = ProxyService.mergeAuthHeaders(headers, authHeaders);
            logBackendHeaders(backend, endpoint, headers);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(backend.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(outboundEnvelope, StandardCharsets.UTF_8));
            headers.forEach((name, values) -> values.forEach(value -> requestBuilder.header(name, value)));

            HttpResponse<byte[]> response = getHttpClient(backend).send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );

            log.info("Received SOAP response: {} from {}", response.statusCode(), endpoint);
            ProxyService.DecodedResponse decodedResponse = ProxyService.decodeResponse(response);
            return unmarshalResponse(version, decodedResponse.body(), responseType, response.statusCode());
        } catch (IOException e) {
            throw new UpstreamProxyException("Failed to reach backend '%s'".formatted(backend.name()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamProxyException("Request to backend '%s' was interrupted".formatted(backend.name()), e);
        }
    }

    private void validateSoapBackend(ProxyProperties.BackendDefinition backend) {
        if (!PROTOCOL_SOAP.equalsIgnoreCase(backend.protocol())) {
            throw new ProxyConfigurationException(
                "Backend '%s' is not configured for SOAP invocation".formatted(backend.name()));
        }
        if (AUTH_TYPE_FORM.equalsIgnoreCase(backend.securityType().orElse(""))
            && FORM_MODE_INLINE.equalsIgnoreCase(backend.securityConfig().getOrDefault("service", ""))) {
            throw new ProxyConfigurationException(
                "SOAP backend '%s' does not support form auth inline mode".formatted(backend.name()));
        }
    }

    private Map<String, List<String>> buildHeaders(ProxyRequestContext request) {
        Map<String, List<String>> headers = new LinkedHashMap<>(request.headers());
        headers.entrySet().removeIf(entry ->
            ProxyService.HOP_BY_HOP_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))
                || ProxyService.NON_FORWARDED_REQUEST_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT)));
        return headers;
    }

    private void applySoapHeaders(Map<String, String> headers, String version, Optional<String> soapAction) {
        if ("1.2".equals(version)) {
            String contentType = "application/soap+xml; charset=utf-8";
            if (soapAction.isPresent()) {
                contentType += "; action=\"" + soapAction.get() + "\"";
            }
            headers.put("content-type", contentType);
            return;
        }

        headers.put("content-type", "text/xml; charset=utf-8");
        soapAction.ifPresent(action -> headers.put("SOAPAction", "\"" + action + "\""));
    }

    private Optional<String> resolveSoapAction(
        ProxyProperties.BackendDefinition backend,
        String soapActionOverride) {
        if (soapActionOverride != null && !soapActionOverride.isBlank()) {
            return Optional.of(soapActionOverride);
        }
        return backend.soap().flatMap(ProxyProperties.SoapConfig::soapAction);
    }

    private String soapVersion(ProxyProperties.BackendDefinition backend) {
        return backend.soap()
            .map(ProxyProperties.SoapConfig::version)
            .filter(version -> "1.1".equals(version) || "1.2".equals(version))
            .orElse("1.1");
    }

    private String soapEnvelope(String version, String payloadXml) {
        String namespace = "1.2".equals(version) ? SOAP_12_ENV_NS : SOAP_11_ENV_NS;
        return """
            <soapenv:Envelope xmlns:soapenv="%s">
              <soapenv:Body>%s</soapenv:Body>
            </soapenv:Envelope>
            """.formatted(namespace, payloadXml);
    }

    private String marshalPayload(Object requestObject, Class<?> responseType) {
        try {
            JAXBContext jaxbContext = jaxbContext(requestObject.getClass(), responseType);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
            StringWriter writer = new StringWriter();
            marshaller.marshal(requestObject, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new ProxyConfigurationException("Failed to marshal SOAP request payload", e);
        }
    }

    private <T> T unmarshalResponse(String version, byte[] body, Class<T> responseType, int httpStatus) {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            org.w3c.dom.Document document = documentBuilderFactory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(body));
            document.getDocumentElement().normalize();
            org.w3c.dom.Element bodyElement = firstChildElementByName(
                document.getDocumentElement(),
                soapNamespace(version),
                "Body"
            );
            if (bodyElement == null) {
                throw new UpstreamProxyException("SOAP response did not contain a Body element");
            }

            org.w3c.dom.Element faultElement = firstChildElementByName(bodyElement, soapNamespace(version), "Fault");
            if (faultElement != null) {
                throw new SoapFaultException("SOAP Fault from backend: " + extractFaultSummary(faultElement, version));
            }

            org.w3c.dom.Element payloadElement = firstElementChild(bodyElement);
            if (payloadElement == null) {
                throw new UpstreamProxyException("SOAP response body did not contain an operation payload");
            }

            JAXBContext jaxbContext = jaxbContext(responseType);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<T> payload = unmarshaller.unmarshal(payloadElement, responseType);
            return payload.getValue();
        } catch (SoapFaultException e) {
            throw e;
        } catch (UpstreamProxyException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamProxyException(
                "Failed to parse SOAP response with HTTP status %d".formatted(httpStatus), e);
        }
    }

    private String extractFaultSummary(org.w3c.dom.Element faultElement, String version) {
        if ("1.2".equals(version)) {
            String code = childText(firstChildElementByName(faultElement, soapNamespace(version), "Code"));
            String reason = childText(firstChildElementByName(faultElement, soapNamespace(version), "Reason"));
            return (code == null ? "" : code + " ") + (reason == null ? "" : reason);
        }
        String code = childText(firstChildElementByName(faultElement, null, "faultcode"));
        String reason = childText(firstChildElementByName(faultElement, null, "faultstring"));
        return (code == null ? "" : code + " ") + (reason == null ? "" : reason);
    }

    private String childText(org.w3c.dom.Element element) {
        if (element == null) {
            return null;
        }
        org.w3c.dom.Element child = firstElementChild(element);
        return child != null ? child.getTextContent() : element.getTextContent();
    }

    private String soapNamespace(String version) {
        return "1.2".equals(version) ? SOAP_12_ENV_NS : SOAP_11_ENV_NS;
    }

    private org.w3c.dom.Element firstElementChild(org.w3c.dom.Element element) {
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                return (org.w3c.dom.Element) child;
            }
        }
        return null;
    }

    private org.w3c.dom.Element firstChildElementByName(
        org.w3c.dom.Element parent,
        String namespace,
        String localName) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element element = (org.w3c.dom.Element) child;
            String elementLocalName = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
            boolean namespaceMatches = namespace == null || namespace.equals(element.getNamespaceURI());
            if (namespaceMatches && localName.equals(elementLocalName)) {
                return element;
            }
        }
        return null;
    }

    private JAXBContext jaxbContext(Class<?>... classes) {
        StringBuilder keyBuilder = new StringBuilder();
        for (Class<?> clazz : classes) {
            keyBuilder.append(clazz.getName()).append('|');
        }
        return jaxbContexts.computeIfAbsent(keyBuilder.toString(), ignored -> buildJaxbContext(classes));
    }

    private JAXBContext buildJaxbContext(Class<?>... classes) {
        try {
            return JAXBContext.newInstance(classes);
        } catch (Exception e) {
            throw new ProxyConfigurationException("Failed to initialize JAXB context for SOAP invocation", e);
        }
    }

    private HttpClient getHttpClient(ProxyProperties.BackendDefinition backend) {
        String clientKey = ProxyService.clientKey(backend);
        return httpClients.computeIfAbsent(clientKey, ignored -> buildHttpClient(backend));
    }

    private HttpClient buildHttpClient(ProxyProperties.BackendDefinition backend) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(ProxyService.resolveHttpVersion(backend));
        tlsContextFactory.createBackendSslContext(backend).ifPresent(builder::sslContext);
        ProxyService.createProxySelector(backend).ifPresent(builder::proxy);
        return builder.build();
    }

    private void logBackendHeaders(
        ProxyProperties.BackendDefinition backend,
        String endpoint,
        Map<String, List<String>> headers) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
            "Outbound SOAP headers for backend {} to {}: {}",
            backend.name(),
            endpoint,
            ProxyService.sanitizeHeadersForLogging(backend, headers)
        );
    }
}
