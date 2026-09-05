package com.db.olorin.rest.client;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import com.db.olorin.rest.exception.SoapFaultException;
import com.db.olorin.rest.exception.UpstreamProxyException;
import com.db.olorin.rest.history.HistoryService;
import com.db.olorin.rest.history.HistoryStatus;
import com.db.olorin.rest.model.ProxyRequestContext;
import com.db.olorin.rest.service.TlsContextFactory;
import com.db.olorin.rest.service.auth.AuthServiceFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JAXB-based SOAP 1.1/1.2 backend client.
 *
 * <p>The client applies the same backend security, TLS, proxy, current-request
 * forwarding and history configuration as {@link SpringTypedRestClient}.</p>
 */
public final class SpringTypedSoapClient implements TypedSoapClient {
    private static final String SOAP_11 = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_12 = "http://www.w3.org/2003/05/soap-envelope";
    private static final int MAX_ATTEMPTS = 3;

    private final ProxyProperties properties;
    private final TlsContextFactory tls;
    private final AuthServiceFactory auth;
    private final HistoryService history;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public SpringTypedSoapClient(ProxyProperties properties, TlsContextFactory tls) {
        this(properties, tls, null, null);
    }

    public SpringTypedSoapClient(ProxyProperties properties, TlsContextFactory tls, AuthServiceFactory auth) {
        this(properties, tls, auth, null);
    }

    public SpringTypedSoapClient(ProxyProperties properties, TlsContextFactory tls, AuthServiceFactory auth,
                                 HistoryService history) {
        this.properties = properties;
        this.tls = tls;
        this.auth = auth;
        this.history = history;
    }

    @Override
    public <I, O> O exchange(String backendName, I requestBody, Class<O> responseType) {
        return exchange(backendName, requestBody, null, responseType);
    }

    @Override
    public <I, O> O exchange(String backendName, I requestBody, String soapActionOverride, Class<O> responseType) {
        ProxyProperties.BackendDefinition backend = backend(backendName);
        if (!"soap".equalsIgnoreCase(backend.protocol())) {
            throw new ProxyConfigurationException("Backend '" + backendName + "' is not configured for SOAP invocation");
        }
        String version = backend.soap().map(ProxyProperties.SoapConfig::version)
            .filter(value -> value.equals("1.1") || value.equals("1.2")).orElse("1.1");
        String action = soapActionOverride != null && !soapActionOverride.isBlank() ? soapActionOverride
            : backend.soap().flatMap(ProxyProperties.SoapConfig::soapAction).orElse(null);
        String envelope = null;
        ProxyRequestContext context = new ProxyRequestContext("POST", backend.path(), null, Map.of(), Map.of());
        Map<String, String> outbound = new LinkedHashMap<>();
        Map<String, List<String>> historyHeaders = Map.of();
        boolean submitted = false;

        try {
            envelope = envelope(requestBody, version);
            if (auth != null) {
                auth.createAuthService(backend).enrichHeaders(context, outbound, envelope);
            }
            forwardFromCurrentRequest(backend, outbound);
            historyHeaders = historyHeaders(outbound);
            if (history != null) {
                history.enrichOutboundHeaders(backend, context, historyHeaders);
                historyHeaders.forEach((key, values) -> outbound.putIfAbsent(key, values.getFirst()));
                history.emit(backend, context, envelope, historyHeaders, envelope, HistoryStatus.SUBMITTED, null, null);
                submitted = true;
            }

            HttpResponse<byte[]> response = send(backend, soapRequest(backend, version, action, envelope, outbound));
            O result = unmarshal(response.body(), version, responseType);
            if (history != null && submitted) {
                history.emit(backend, context, envelope, historyHeaders, envelope, HistoryStatus.FULFILLED,
                    response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            }
            return result;
        } catch (SoapFaultException | ProxyConfigurationException error) {
            emitFailure(backend, context, submitted, envelope, historyHeaders);
            throw error;
        } catch (Exception error) {
            emitFailure(backend, context, submitted, envelope, historyHeaders);
            throw new UpstreamProxyException("Failed SOAP invocation for backend '" + backendName + "'", error);
        }
    }

    private ProxyProperties.BackendDefinition backend(String name) {
        return properties.backends().stream().filter(value -> value.enabled() && value.name().equals(name)).findFirst()
            .orElseThrow(() -> new ProxyConfigurationException("No enabled backend named '" + name + "' is configured"));
    }

    private String envelope(Object value, String version) throws Exception {
        return "<soapenv:Envelope xmlns:soapenv=\"" + (version.equals("1.2") ? SOAP_12 : SOAP_11)
            + "\"><soapenv:Body>" + marshal(value) + "</soapenv:Body></soapenv:Envelope>";
    }

    private HttpRequest soapRequest(ProxyProperties.BackendDefinition backend, String version, String action,
                                    String envelope, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(java.net.URI.create(join(backend.baseUrl(), backend.path())))
            .timeout(backend.timeout()).POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8));
        if (version.equals("1.2")) {
            request.header(HttpHeaders.CONTENT_TYPE, "application/soap+xml; charset=utf-8"
                + (action == null ? "" : "; action=\"" + action + "\""));
        } else {
            request.header(HttpHeaders.CONTENT_TYPE, "text/xml; charset=utf-8");
            if (action != null) request.header("SOAPAction", "\"" + action + "\"");
        }
        headers.forEach(request::header);
        return request.build();
    }

    private HttpResponse<byte[]> send(ProxyProperties.BackendDefinition backend, HttpRequest request) throws Exception {
        CircuitState circuit = circuits.computeIfAbsent(backend.name(), ignored -> new CircuitState());
        circuit.assertAvailable(backend.name());
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(backend.timeout())
            .version(SpringTypedRestClient.resolveHttpVersion(backend));
        tls.createBackendSslContext(backend).ifPresent(builder::sslContext);
        SpringTypedRestClient.createProxySelector(backend).ifPresent(builder::proxy);
        HttpClient client = builder.build();
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                circuit.success();
                return response;
            } catch (IOException error) {
                lastFailure = error;
                if (attempt < MAX_ATTEMPTS) pause();
            }
        }
        circuit.failure();
        throw lastFailure;
    }

    private void forwardFromCurrentRequest(ProxyProperties.BackendDefinition backend, Map<String, String> outbound) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        jakarta.servlet.http.HttpServletRequest current = attributes.getRequest();
        backend.forwardHeaders().forEach(name -> {
            if (name == null || name.isBlank() || containsHeader(outbound, name)) return;
            String value = current.getHeader(name);
            if (value != null && !value.isBlank()) outbound.put(name, value);
        });
        if (backend.forwardCookies().isEmpty() || containsHeader(outbound, HttpHeaders.COOKIE)) return;
        Map<String, String> cookies = new LinkedHashMap<>();
        if (current.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : current.getCookies()) cookies.put(cookie.getName(), cookie.getValue());
        }
        List<String> selected = backend.forwardCookies().stream().filter(cookies::containsKey)
            .map(name -> name + "=" + cookies.get(name)).toList();
        if (!selected.isEmpty()) outbound.put(HttpHeaders.COOKIE, String.join("; ", selected));
    }

    private void emitFailure(ProxyProperties.BackendDefinition backend, ProxyRequestContext context, boolean submitted,
                             String envelope, Map<String, List<String>> headers) {
        if (history == null || !submitted) return;
        try {
            history.emit(backend, context, envelope, headers, envelope, HistoryStatus.FAILED, null, null);
        } catch (RuntimeException ignored) {
            // Do not replace the invocation failure with a secondary history delivery failure.
        }
    }

    private Map<String, List<String>> historyHeaders(Map<String, String> headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), List.of(value)));
        return result;
    }

    private boolean containsHeader(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
    }

    private String join(String base, String path) {
        return base.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);
    }

    private void pause() throws InterruptedException {
        Thread.sleep(200);
    }

    private String marshal(Object value) throws Exception {
        JAXBContext context = JAXBContext.newInstance(value.getClass());
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        StringWriter output = new StringWriter();
        marshaller.marshal(value, output);
        return output.toString();
    }

    private <O> O unmarshal(byte[] xml, String version, Class<O> type) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
        Element body = child(root, version.equals("1.2") ? SOAP_12 : SOAP_11, "Body");
        if (body == null) throw new UpstreamProxyException("SOAP response did not contain a Body element");
        Element payload = first(body);
        if (payload != null && "Fault".equals(payload.getLocalName())) {
            throw new SoapFaultException("SOAP Fault from backend: " + payload.getTextContent());
        }
        if (payload == null) throw new UpstreamProxyException("SOAP response body did not contain an operation payload");
        Unmarshaller unmarshaller = JAXBContext.newInstance(type).createUnmarshaller();
        JAXBElement<O> result = unmarshaller.unmarshal(payload, type);
        return result.getValue();
    }

    private Element child(Element parent, String namespace, String name) {
        if (parent == null) return null;
        for (org.w3c.dom.Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName()) && namespace.equals(element.getNamespaceURI())) {
                return element;
            }
        }
        return null;
    }

    private Element first(Element parent) {
        if (parent == null) return null;
        for (org.w3c.dom.Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) return element;
        }
        return null;
    }

    /** Mirrors the lightweight circuit policy used by the JSON client. */
    private static final class CircuitState {
        private final java.util.ArrayList<Boolean> recent = new java.util.ArrayList<>();
        private long until;

        synchronized void assertAvailable(String backend) {
            if (until > System.currentTimeMillis()) {
                throw new UpstreamProxyException("Circuit breaker is open for backend '" + backend + "'");
            }
            if (until != 0) {
                until = 0;
                recent.clear();
            }
        }

        synchronized void success() {
            recent.add(Boolean.TRUE);
            trim();
        }

        synchronized void failure() {
            recent.add(Boolean.FALSE);
            trim();
            if (recent.size() >= 4 && recent.stream().filter(value -> !value).count() * 2 >= recent.size()) {
                until = System.currentTimeMillis() + 5000;
            }
        }

        private void trim() {
            while (recent.size() > 4) recent.removeFirst();
        }
    }
}
