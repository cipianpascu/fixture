package com.agent.gateway.proxy.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyTestResource implements QuarkusTestResourceLifecycleManager {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer backendServer;
    private HttpServer authServer;

    private static final AtomicInteger RETRY_SESSION_CALLS = new AtomicInteger();
    private static final AtomicInteger CLOUD_RUN_AUTH_SESSION_CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_APIGEE_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> LAST_APIGEE_API_KEY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_GLUE_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> LAST_GLUE_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_EIDP_AUTHORIZATION_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_EIDP_ACCESS_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CIAM_CUSTOMER_ACCESS_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_FORM_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> LAST_FORM_TENANT_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_INLINE_FORM_BODY = new AtomicReference<>();
    private static final AtomicInteger FORM_AUTH_CALLS = new AtomicInteger();
    private static final AtomicInteger ORDER_DETAILS_CALLS = new AtomicInteger();
    private static final AtomicInteger PAYMENT_ORDER_CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_CHAINED_ORDER_CORRELATION_ID = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_ORDER_TENANT_ID = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_ORDER_CHANNEL = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_PAYMENT_CORRELATION_ID = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_PAYMENT_TENANT_ID = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_PAYMENT_CHANNEL = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_PAYMENT_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_PAYMENT_CUSTOMER_ID = new AtomicReference<>();
    private static final AtomicReference<String> LAST_CHAINED_PAYMENT_QUERY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_SOAP_AUTH_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> LAST_SOAP_ACTION = new AtomicReference<>();
    private static final AtomicReference<String> LAST_SOAP_METHOD = new AtomicReference<>();
    private static final AtomicReference<String> LAST_COMPRESSION_SENSITIVE_ACCEPT_ENCODING = new AtomicReference<>();

    @Override
    public Map<String, String> start() {
        try {
            backendServer = HttpServer.create(new InetSocketAddress(0), 0);
            authServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start test servers", e);
        }

        RETRY_SESSION_CALLS.set(0);
        CLOUD_RUN_AUTH_SESSION_CALLS.set(0);
        LAST_APIGEE_AUTHORIZATION.set(null);
        LAST_APIGEE_API_KEY.set(null);
        LAST_GLUE_AUTHORIZATION.set(null);
        LAST_GLUE_TOKEN.set(null);
        LAST_EIDP_AUTHORIZATION_TOKEN.set(null);
        LAST_EIDP_ACCESS_TOKEN.set(null);
        LAST_CIAM_CUSTOMER_ACCESS_TOKEN.set(null);
        LAST_FORM_AUTHORIZATION.set(null);
        LAST_FORM_TENANT_TOKEN.set(null);
        LAST_INLINE_FORM_BODY.set(null);
        FORM_AUTH_CALLS.set(0);
        ORDER_DETAILS_CALLS.set(0);
        PAYMENT_ORDER_CALLS.set(0);
        LAST_CHAINED_ORDER_CORRELATION_ID.set(null);
        LAST_CHAINED_ORDER_TENANT_ID.set(null);
        LAST_CHAINED_ORDER_CHANNEL.set(null);
        LAST_CHAINED_PAYMENT_CORRELATION_ID.set(null);
        LAST_CHAINED_PAYMENT_TENANT_ID.set(null);
        LAST_CHAINED_PAYMENT_CHANNEL.set(null);
        LAST_CHAINED_PAYMENT_TOKEN.set(null);
        LAST_CHAINED_PAYMENT_CUSTOMER_ID.set(null);
        LAST_CHAINED_PAYMENT_QUERY.set(null);
        LAST_SOAP_AUTH_TOKEN.set(null);
        LAST_SOAP_ACTION.set(null);
        LAST_SOAP_METHOD.set(null);
        LAST_COMPRESSION_SENSITIVE_ACCEPT_ENCODING.set(null);
        registerBackendHandlers();
        registerAuthHandlers();

        backendServer.start();
        authServer.start();

        String backendBaseUrl = "http://127.0.0.1:" + backendServer.getAddress().getPort();
        String authBaseUrl = "http://127.0.0.1:" + authServer.getAddress().getPort();

        Map<String, String> config = new LinkedHashMap<>();
        config.put("gateway.schemas.directory", "classpath:schemas/");
        config.put("gateway.schemas.validate-requests", "true");
        config.put("gateway.schemas.validate-bodies", "true");
        config.put("gateway.schemas.validate-responses", "true");
        config.put("gateway.schemas.strict-mode", "true");
        config.put("gateway.auth.enabled", "true");
        config.put("gateway.auth.service-url", authBaseUrl);
        config.put("gateway.auth.security-type", "cloudrun");
        config.put("gateway.auth.security-config.audience", "https://oauth-service-ew.a.run.app/");
        config.put("gateway.authz.enabled", "true");
        config.put("gateway.authz.service-url", authBaseUrl);
        config.put("gateway.authz.security-type", "cloudrun");
        config.put("gateway.authz.security-config.audience", "https://oauthz-service-ew.a.run.app/");

        config.put("gateway.backends[0].name", "secondary-service");
        config.put("gateway.backends[0].baseUrl", backendBaseUrl);
        config.put("gateway.backends[0].path", "/internal/secondary");
        config.put("gateway.backends[0].schema", "secondary-service.yaml");
        config.put("gateway.backends[0].enabled", "true");
        config.put("gateway.backends[0].securityType", "none");

        config.put("gateway.backends[1].name", "templated-service");
        config.put("gateway.backends[1].baseUrl", backendBaseUrl);
        config.put("gateway.backends[1].path", "/templated");
        config.put("gateway.backends[1].schema", "templated-service.yaml");
        config.put("gateway.backends[1].enabled", "true");
        config.put("gateway.backends[1].securityType", "none");

        config.put("gateway.backends[2].name", "unsupported-auth-service");
        config.put("gateway.backends[2].baseUrl", backendBaseUrl);
        config.put("gateway.backends[2].path", "/unsupported");
        config.put("gateway.backends[2].schema", "secondary-service.yaml");
        config.put("gateway.backends[2].enabled", "true");
        config.put("gateway.backends[2].securityType", "oauth2");

        config.put("gateway.backends[3].name", "misconfigured-basic-service");
        config.put("gateway.backends[3].baseUrl", backendBaseUrl);
        config.put("gateway.backends[3].path", "/basic");
        config.put("gateway.backends[3].schema", "secondary-service.yaml");
        config.put("gateway.backends[3].enabled", "true");
        config.put("gateway.backends[3].securityType", "basic");

        config.put("gateway.backends[4].name", "jwt-service");
        config.put("gateway.backends[4].baseUrl", backendBaseUrl);
        config.put("gateway.backends[4].path", "/jwt");
        config.put("gateway.backends[4].schema", "secondary-service.yaml");
        config.put("gateway.backends[4].enabled", "true");
        config.put("gateway.backends[4].securityType", "jwt");
        config.put("gateway.backends[4].auth-request.service", "auth");
        config.put("gateway.backends[4].auth-request.sparte-gvo[0]", "a");
        config.put("gateway.backends[4].auth-request.sparte-gvo[1]", "b");
        config.put("gateway.backends[4].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[4].auth-request.pss[0]", "SecondFunction");

        config.put("gateway.backends[5].name", "jwt-apigee-service");
        config.put("gateway.backends[5].baseUrl", backendBaseUrl);
        config.put("gateway.backends[5].path", "/jwt-apigee");
        config.put("gateway.backends[5].schema", "secondary-service.yaml");
        config.put("gateway.backends[5].enabled", "true");
        config.put("gateway.backends[5].securityType", "jwt");
        config.put("gateway.backends[5].auth-request.service", "auth");
        config.put("gateway.backends[5].auth-request.sparte-gvo[0]", "a");
        config.put("gateway.backends[5].auth-request.sparte-gvo[1]", "b");
        config.put("gateway.backends[5].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[5].auth-request.pss[0]", "SecondFunction");
        config.put("gateway.backends[5].securityConfig.bearer-source", "customer_access_token");
        config.put("gateway.backends[5].securityConfig.static-headers.x-api-key", "test-apigee-key");

        config.put("gateway.backends[6].name", "jwt-glue-service");
        config.put("gateway.backends[6].baseUrl", backendBaseUrl);
        config.put("gateway.backends[6].path", "/jwt-glue");
        config.put("gateway.backends[6].schema", "secondary-service.yaml");
        config.put("gateway.backends[6].enabled", "true");
        config.put("gateway.backends[6].securityType", "jwt");
        config.put("gateway.backends[6].auth-request.service", "auth");
        config.put("gateway.backends[6].auth-request.sparte-gvo[0]", "a");
        config.put("gateway.backends[6].auth-request.sparte-gvo[1]", "b");
        config.put("gateway.backends[6].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[6].auth-request.pss[0]", "SecondFunction");
        config.put("gateway.backends[6].securityConfig.bearer-source", "auth_z_token");
        config.put("gateway.backends[6].securityConfig.token-headers.X-Glue-Token", "glue_token");

        config.put("gateway.backends[7].name", "jwt-optional-auth-request-service");
        config.put("gateway.backends[7].baseUrl", backendBaseUrl);
        config.put("gateway.backends[7].path", "/jwt-optional");
        config.put("gateway.backends[7].schema", "secondary-service.yaml");
        config.put("gateway.backends[7].enabled", "true");
        config.put("gateway.backends[7].securityType", "jwt");
        config.put("gateway.backends[7].auth-request.btx[0]", "FirstFunction");

        config.put("gateway.backends[8].name", "jwt-authz-eidp-service");
        config.put("gateway.backends[8].baseUrl", backendBaseUrl);
        config.put("gateway.backends[8].path", "/jwt-authz-eidp");
        config.put("gateway.backends[8].schema", "secondary-service.yaml");
        config.put("gateway.backends[8].enabled", "true");
        config.put("gateway.backends[8].securityType", "jwt");
        config.put("gateway.backends[8].authz-request.service", "authz");
        config.put("gateway.backends[8].authz-request.path", "/auth/authz/eidp/{sessionId}");
        config.put("gateway.backends[8].authz-request.branch-customer-number", "header:Branch-Customer-Number");
        config.put("gateway.backends[8].authz-request.gvo-entitlements-list[0]", "entitlement-a");
        config.put("gateway.backends[8].authz-request.service-shop-transactions[0]", "shop-a");
        config.put("gateway.backends[8].securityConfig.token-headers.X-Authorization-Token", "authorization_token");
        config.put("gateway.backends[8].securityConfig.token-headers.X-Eidp-Access-Token", "eidp_access_token");

        config.put("gateway.backends[9].name", "jwt-authz-ciam-service");
        config.put("gateway.backends[9].baseUrl", backendBaseUrl);
        config.put("gateway.backends[9].path", "/jwt-authz-ciam");
        config.put("gateway.backends[9].schema", "secondary-service.yaml");
        config.put("gateway.backends[9].enabled", "true");
        config.put("gateway.backends[9].securityType", "jwt");
        config.put("gateway.backends[9].authz-request.service", "authz");
        config.put("gateway.backends[9].authz-request.path", "/auth/authz/ciam/{sessionId}");
        config.put("gateway.backends[9].authz-request.branch-customer-number", "header:Branch-Customer-Number");
        config.put("gateway.backends[9].authz-request.business-transactions[0]", "business-a");
        config.put("gateway.backends[9].authz-request.service-shop-transactions[0]", "shop-b");
        config.put("gateway.backends[9].securityConfig.bearer-source", "authz_customer_access_token");

        config.put("gateway.backends[10].name", "cloudrun-service");
        config.put("gateway.backends[10].baseUrl", backendBaseUrl);
        config.put("gateway.backends[10].path", "/cloudrun");
        config.put("gateway.backends[10].schema", "cloudrun-service.yaml");
        config.put("gateway.backends[10].enabled", "true");
        config.put("gateway.backends[10].securityType", "cloudrun");
        config.put("gateway.backends[10].securityConfig.audience", "https://orders-service-ew.a.run.app/");

        config.put("gateway.backends[11].name", "orders-service");
        config.put("gateway.backends[11].baseUrl", backendBaseUrl);
        config.put("gateway.backends[11].path", "/orders");
        config.put("gateway.backends[11].schema", "secondary-service.yaml");
        config.put("gateway.backends[11].enabled", "true");
        config.put("gateway.backends[11].securityType", "none");

        config.put("gateway.backends[12].name", "payments-service");
        config.put("gateway.backends[12].baseUrl", backendBaseUrl);
        config.put("gateway.backends[12].path", "/payments");
        config.put("gateway.backends[12].schema", "secondary-service.yaml");
        config.put("gateway.backends[12].enabled", "true");
        config.put("gateway.backends[12].securityType", "none");

        config.put("gateway.backends[13].name", "parameter-service");
        config.put("gateway.backends[13].baseUrl", backendBaseUrl);
        config.put("gateway.backends[13].path", "/params");
        config.put("gateway.backends[13].schema", "parameter-service.yaml");
        config.put("gateway.backends[13].enabled", "true");
        config.put("gateway.backends[13].securityType", "none");

        config.put("gateway.backends[14].name", "recursive-service");
        config.put("gateway.backends[14].baseUrl", backendBaseUrl);
        config.put("gateway.backends[14].path", "/recursive");
        config.put("gateway.backends[14].schema", "recursive-service.yaml");
        config.put("gateway.backends[14].enabled", "true");
        config.put("gateway.backends[14].securityType", "none");

        config.put("gateway.backends[15].name", "form-service");
        config.put("gateway.backends[15].baseUrl", backendBaseUrl);
        config.put("gateway.backends[15].path", "/form-auth");
        config.put("gateway.backends[15].schema", "secondary-service.yaml");
        config.put("gateway.backends[15].enabled", "true");
        config.put("gateway.backends[15].securityType", "form");
        config.put("gateway.backends[15].securityConfig.service", "auth");
        config.put("gateway.backends[15].securityConfig.auth-path", "/auth/form");
        config.put("gateway.backends[15].securityConfig.form-params.grant_type", "literal:client_credentials");
        config.put("gateway.backends[15].securityConfig.form-params.client_id", "header:Client-Id");
        config.put("gateway.backends[15].securityConfig.form-params.client_secret", "cookie:clientSecret");
        config.put("gateway.backends[15].securityConfig.form-params.scope", "literal:appointments.read");
        config.put("gateway.backends[15].securityConfig.response-headers.Authorization", "access_token");
        config.put("gateway.backends[15].securityConfig.response-header-prefixes.Authorization", "Bearer");
        config.put("gateway.backends[15].securityConfig.response-headers.X-Tenant-Token", "tenant_token");

        config.put("gateway.backends[16].name", "form-inline-service");
        config.put("gateway.backends[16].baseUrl", backendBaseUrl);
        config.put("gateway.backends[16].path", "/form-inline");
        config.put("gateway.backends[16].schema", "form-inline-service.yaml");
        config.put("gateway.backends[16].enabled", "true");
        config.put("gateway.backends[16].securityType", "form");
        config.put("gateway.backends[16].securityConfig.service", "inline");
        config.put("gateway.backends[16].securityConfig.form-params.grant_type", "literal:client_credentials");
        config.put("gateway.backends[16].securityConfig.form-params.client_id", "header:Client-Id");
        config.put("gateway.backends[16].securityConfig.form-params.client_secret", "cookie:clientSecret");

        config.put("gateway.backends[17].name", "jwt-custom-auth-path-service");
        config.put("gateway.backends[17].baseUrl", backendBaseUrl);
        config.put("gateway.backends[17].path", "/jwt-custom-auth-path");
        config.put("gateway.backends[17].schema", "secondary-service.yaml");
        config.put("gateway.backends[17].enabled", "true");
        config.put("gateway.backends[17].securityType", "jwt");
        config.put("gateway.backends[17].auth-request.service", "auth");
        config.put("gateway.backends[17].auth-request.path", "/custom-auth/tokens/{sessionId}");
        config.put("gateway.backends[17].auth-request.btx[0]", "FirstFunction");

        config.put("gateway.backends[18].name", "customer-profile-soap-service");
        config.put("gateway.backends[18].baseUrl", backendBaseUrl);
        config.put("gateway.backends[18].path", "/soap/customer-profile");
        config.put("gateway.backends[18].schema", "customer-profile.yaml");
        config.put("gateway.backends[18].enabled", "true");
        config.put("gateway.backends[18].protocol", "soap");
        config.put("gateway.backends[18].securityType", "jwt");
        config.put("gateway.backends[18].auth-request.service", "auth");
        config.put("gateway.backends[18].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[18].soap.version", "1.1");
        config.put("gateway.backends[18].soap.soap-action", "urn:GetCustomerProfile");

        config.put("gateway.backends[19].name", "compression-sensitive-service");
        config.put("gateway.backends[19].baseUrl", backendBaseUrl);
        config.put("gateway.backends[19].path", "/compression");
        config.put("gateway.backends[19].schema", "secondary-service.yaml");
        config.put("gateway.backends[19].enabled", "true");
        config.put("gateway.backends[19].securityType", "none");

        return config;
    }

    @Override
    public void stop() {
        if (backendServer != null) {
            backendServer.stop(0);
        }
        if (authServer != null) {
            authServer.stop(0);
        }
    }

    public static int getRetrySessionCalls() {
        return RETRY_SESSION_CALLS.get();
    }

    public static int getCloudRunAuthSessionCalls() {
        return CLOUD_RUN_AUTH_SESSION_CALLS.get();
    }

    public static String getLastApigeeAuthorization() {
        return LAST_APIGEE_AUTHORIZATION.get();
    }

    public static String getLastApigeeApiKey() {
        return LAST_APIGEE_API_KEY.get();
    }

    public static String getLastGlueAuthorization() {
        return LAST_GLUE_AUTHORIZATION.get();
    }

    public static String getLastGlueToken() {
        return LAST_GLUE_TOKEN.get();
    }

    public static String getLastEidpAuthorizationToken() {
        return LAST_EIDP_AUTHORIZATION_TOKEN.get();
    }

    public static String getLastEidpAccessToken() {
        return LAST_EIDP_ACCESS_TOKEN.get();
    }

    public static String getLastCiamCustomerAccessToken() {
        return LAST_CIAM_CUSTOMER_ACCESS_TOKEN.get();
    }

    public static String getLastFormAuthorization() {
        return LAST_FORM_AUTHORIZATION.get();
    }

    public static String getLastFormTenantToken() {
        return LAST_FORM_TENANT_TOKEN.get();
    }

    public static String getLastInlineFormBody() {
        return LAST_INLINE_FORM_BODY.get();
    }

    public static int getFormAuthCalls() {
        return FORM_AUTH_CALLS.get();
    }

    public static int getOrderDetailsCalls() {
        return ORDER_DETAILS_CALLS.get();
    }

    public static int getPaymentOrderCalls() {
        return PAYMENT_ORDER_CALLS.get();
    }

    public static String getLastChainedOrderCorrelationId() {
        return LAST_CHAINED_ORDER_CORRELATION_ID.get();
    }

    public static String getLastChainedOrderTenantId() {
        return LAST_CHAINED_ORDER_TENANT_ID.get();
    }

    public static String getLastChainedOrderChannel() {
        return LAST_CHAINED_ORDER_CHANNEL.get();
    }

    public static String getLastChainedPaymentCorrelationId() {
        return LAST_CHAINED_PAYMENT_CORRELATION_ID.get();
    }

    public static String getLastChainedPaymentTenantId() {
        return LAST_CHAINED_PAYMENT_TENANT_ID.get();
    }

    public static String getLastChainedPaymentChannel() {
        return LAST_CHAINED_PAYMENT_CHANNEL.get();
    }

    public static String getLastChainedPaymentToken() {
        return LAST_CHAINED_PAYMENT_TOKEN.get();
    }

    public static String getLastChainedPaymentCustomerId() {
        return LAST_CHAINED_PAYMENT_CUSTOMER_ID.get();
    }

    public static String getLastChainedPaymentQuery() {
        return LAST_CHAINED_PAYMENT_QUERY.get();
    }

    public static String getLastSoapAuthToken() {
        return LAST_SOAP_AUTH_TOKEN.get();
    }

    public static String getLastSoapAction() {
        return LAST_SOAP_ACTION.get();
    }

    public static String getLastSoapMethod() {
        return LAST_SOAP_METHOD.get();
    }

    public static String getLastCompressionSensitiveAcceptEncoding() {
        return LAST_COMPRESSION_SENSITIVE_ACCEPT_ENCODING.get();
    }

    private void registerBackendHandlers() {
        backendServer.createContext("/internal/secondary/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"secondary-ok\",\"internal\":\"discard-me\"}"));
        backendServer.createContext("/templated/items/123", exchange ->
            respond(exchange, 200, "{\"ok\":true,\"debug\":true}"));
        backendServer.createContext("/jwt/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"jwt-ok\",\"internal\":\"discard-me\"}"));
        backendServer.createContext("/jwt-optional/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"jwt-optional-ok\",\"internal\":\"discard-me\"}"));
        backendServer.createContext("/jwt-custom-auth-path/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"jwt-custom-auth-path-ok\",\"internal\":\"discard-me\"}"));
        backendServer.createContext("/jwt-apigee/ping", exchange -> {
            LAST_APIGEE_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            LAST_APIGEE_API_KEY.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            exchange.getResponseHeaders().add("Authorization", "Bearer should-not-leak");
            exchange.getResponseHeaders().add("x-api-key", "should-not-leak");
            exchange.getResponseHeaders().add("Set-Cookie", "session=should-not-leak");
            respond(exchange, 200, "{\"status\":\"jwt-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/jwt-glue/ping", exchange -> {
            LAST_GLUE_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            LAST_GLUE_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Glue-Token"));
            exchange.getResponseHeaders().add("Authorization", "Bearer should-not-leak");
            exchange.getResponseHeaders().add("X-Glue-Token", "should-not-leak");
            respond(exchange, 200, "{\"status\":\"jwt-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/jwt-authz-eidp/ping", exchange -> {
            LAST_EIDP_AUTHORIZATION_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Authorization-Token"));
            LAST_EIDP_ACCESS_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Eidp-Access-Token"));
            respond(exchange, 200, "{\"status\":\"jwt-authz-eidp-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/jwt-authz-ciam/ping", exchange -> {
            LAST_CIAM_CUSTOMER_ACCESS_TOKEN.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"status\":\"jwt-authz-ciam-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/cloudrun/ping", exchange ->
            respond(exchange, 200,
                "{\"serverlessAuthorization\":\"%s\",\"internal\":\"discard-me\"}".formatted(
                    exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"))));
        backendServer.createContext("/form-auth/ping", exchange -> {
            LAST_FORM_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            LAST_FORM_TENANT_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Tenant-Token"));
            exchange.getResponseHeaders().add("Authorization", "Bearer should-not-leak");
            exchange.getResponseHeaders().add("X-Tenant-Token", "should-not-leak");
            respond(exchange, 200, "{\"status\":\"form-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/form-inline/submit", exchange -> {
            LAST_INLINE_FORM_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"status\":\"inline-form-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/orders/details/123", exchange -> {
            ORDER_DETAILS_CALLS.incrementAndGet();
            respond(exchange, 200, "{\"id\":\"123\",\"status\":\"READY\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/orders/details/500", exchange -> {
            ORDER_DETAILS_CALLS.incrementAndGet();
            respond(exchange, 200, "{\"id\":\"500\",\"status\":\"READY\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/orders/details/321", exchange -> {
            ORDER_DETAILS_CALLS.incrementAndGet();
            LAST_CHAINED_ORDER_CORRELATION_ID.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            LAST_CHAINED_ORDER_TENANT_ID.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            LAST_CHAINED_ORDER_CHANNEL.set(exchange.getRequestHeaders().getFirst("X-Client-Channel"));
            respond(
                exchange,
                200,
                "{\"id\":\"321\",\"status\":\"READY\",\"customerId\":\"cust-321\",\"paymentToken\":\"pay-321\",\"internal\":\"discard-me\"}"
            );
        });
        backendServer.createContext("/payments/orders/123", exchange -> {
            PAYMENT_ORDER_CALLS.incrementAndGet();
            respond(exchange, 200, "{\"orderId\":\"123\",\"paymentStatus\":\"PAID\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/payments/orders/500", exchange -> {
            PAYMENT_ORDER_CALLS.incrementAndGet();
            respond(exchange, 502, "{\"error\":\"payments-down\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/payments/orders/321", exchange -> {
            PAYMENT_ORDER_CALLS.incrementAndGet();
            LAST_CHAINED_PAYMENT_CORRELATION_ID.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            LAST_CHAINED_PAYMENT_TENANT_ID.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            LAST_CHAINED_PAYMENT_CHANNEL.set(exchange.getRequestHeaders().getFirst("X-Client-Channel"));
            LAST_CHAINED_PAYMENT_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Payment-Token"));
            LAST_CHAINED_PAYMENT_CUSTOMER_ID.set(exchange.getRequestHeaders().getFirst("X-Customer-Id"));
            LAST_CHAINED_PAYMENT_QUERY.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"orderId\":\"321\",\"paymentStatus\":\"PAID\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/soap/customer-profile", exchange -> {
            LAST_SOAP_AUTH_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Customer-Access-Token"));
            LAST_SOAP_ACTION.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
            LAST_SOAP_METHOD.set(exchange.getRequestMethod());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("<customerId>fault</customerId>")) {
                respondXml(
                    exchange,
                    500,
                    """
                        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                          <soapenv:Body>
                            <soapenv:Fault>
                              <faultcode>soapenv:Server</faultcode>
                              <faultstring>customer profile unavailable</faultstring>
                            </soapenv:Fault>
                          </soapenv:Body>
                        </soapenv:Envelope>
                        """
                );
                return;
            }
            if (!body.contains("<customerId>321</customerId>")) {
                respondXml(
                    exchange,
                    400,
                    """
                        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                          <soapenv:Body>
                            <soapenv:Fault>
                              <faultcode>soapenv:Client</faultcode>
                              <faultstring>unexpected request</faultstring>
                            </soapenv:Fault>
                          </soapenv:Body>
                        </soapenv:Envelope>
                        """
                );
                return;
            }
            respondXml(
                exchange,
                200,
                """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cp="http://agent.com/customerprofile">
                      <soapenv:Body>
                        <cp:GetCustomerProfileResponse>
                          <cp:customerId>321</cp:customerId>
                          <cp:fullName>Jane Doe</cp:fullName>
                          <cp:segment>GOLD</cp:segment>
                        </cp:GetCustomerProfileResponse>
                      </soapenv:Body>
                    </soapenv:Envelope>
                    """
            );
        });
        backendServer.createContext("/params/search/123", exchange ->
            respond(exchange, 200, "{\"ok\":true,\"debug\":\"discard-me\"}"));
        backendServer.createContext("/recursive/tree", exchange ->
            respond(exchange, 200, "{\"ok\":true}"));
        backendServer.createContext("/compression/ping", exchange -> {
            LAST_COMPRESSION_SENSITIVE_ACCEPT_ENCODING.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            if (LAST_COMPRESSION_SENSITIVE_ACCEPT_ENCODING.get() != null) {
                respond(exchange, 415, "{\"error\":\"unsupported accept-encoding\"}");
                return;
            }
            respond(exchange, 200, "{\"status\":\"compression-ok\",\"internal\":\"discard-me\"}");
        });
    }

    private void registerAuthHandlers() {
        authServer.createContext("/auth/tokens", exchange -> {
            String sessionId = exchange.getRequestURI().getPath().substring("/auth/tokens/".length());
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            JsonNode requestBody = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            assertAuthRequestShape(requestBody);
            if ("retry-session".equals(sessionId)) {
                int callNumber = RETRY_SESSION_CALLS.incrementAndGet();
                if (callNumber == 1) {
                    respond(exchange, 503, "{\"error\":\"temporary auth outage\"}");
                    return;
                }
                respond(
                    exchange,
                    200,
                    "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
                );
                return;
            }

            if ("cloudrun-auth-session".equals(sessionId)) {
                CLOUD_RUN_AUTH_SESSION_CALLS.incrementAndGet();
                if (!"Bearer test-id-token-for:https://oauth-service-ew.a.run.app/".equals(serverlessAuthorization)) {
                    respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                    return;
                }
                respond(
                    exchange,
                    200,
                    "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
                );
                return;
            }

            if ("fatal-session".equals(sessionId)) {
                respond(exchange, 500, "{\"error\":\"auth-service-down\"}");
                return;
            }

            if ("missing-customer-token-session".equals(sessionId)) {
                respond(
                    exchange,
                    200,
                    "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"disallowed_pss\":[]}"
                );
                return;
            }

            if ("forbidden-session".equals(sessionId)) {
                respond(
                    exchange,
                    200,
                    "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[\"SecondFunction\"]}"
                );
                return;
            }

            if ("auth-denied-session".equals(sessionId)) {
                respond(exchange, 401, "{\"error\":\"not authenticated\"}");
                return;
            }

            respond(
                exchange,
                200,
                "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
            );
        });

        authServer.createContext("/custom-auth/tokens", exchange -> {
            String sessionId = exchange.getRequestURI().getPath().substring("/custom-auth/tokens/".length());
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            if (proxyCloudRunAuthMissing(serverlessAuthorization)) {
                respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                return;
            }
            if (!"custom-path-session".equals(sessionId)) {
                respond(exchange, 400, "{\"error\":\"unexpected sessionId\"}");
                return;
            }
            JsonNode requestBody = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            if (!requestBody.path("btx").isArray()
                || !"FirstFunction".equals(requestBody.path("btx").get(0).asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected auth request payload\"}");
                return;
            }
            respond(
                exchange,
                200,
                "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
            );
        });

        authServer.createContext("/auth/authz/eidp", exchange -> {
            String sessionId = exchange.getRequestURI().getPath().substring("/auth/authz/eidp/".length());
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            if (proxyCloudRunAuthzMissing(serverlessAuthorization)) {
                respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                return;
            }
            JsonNode requestBody = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            if (!"Branch-01".equals(requestBody.path("branchCustomerNumber").asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected branchCustomerNumber\"}");
                return;
            }
            if (!requestBody.path("gvoEntitlementsList").isArray()
                || !"entitlement-a".equals(requestBody.path("gvoEntitlementsList").get(0).asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected gvoEntitlementsList\"}");
                return;
            }
            if (!requestBody.path("serviceShopTransactions").isArray()
                || !"shop-a".equals(requestBody.path("serviceShopTransactions").get(0).asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected serviceShopTransactions\"}");
                return;
            }
            if ("eidp-forbidden-session".equals(sessionId)) {
                respond(exchange, 200, "{\"authorizationToken\":\"authorization-token\",\"glueAccessToken\":\"glue-access-token\",\"allowedServiceShopTransactions\":[]}");
                return;
            }
            respond(
                exchange,
                200,
                "{\"authorizationToken\":\"authorization-token\",\"glueAccessToken\":\"glue-access-token\",\"allowedServiceShopTransactions\":[\"shop-a\"]}"
            );
        });

        authServer.createContext("/auth/authz/ciam", exchange -> {
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            if (proxyCloudRunAuthzMissing(serverlessAuthorization)) {
                respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                return;
            }
            JsonNode requestBody = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            if (!"Branch-02".equals(requestBody.path("branchCustomerNumber").asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected branchCustomerNumber\"}");
                return;
            }
            if (!requestBody.path("businessTransactions").isArray()
                || !"business-a".equals(requestBody.path("businessTransactions").get(0).asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected businessTransactions\"}");
                return;
            }
            if (!requestBody.path("serviceShopTransactions").isArray()
                || !"shop-b".equals(requestBody.path("serviceShopTransactions").get(0).asText())) {
                respond(exchange, 400, "{\"error\":\"unexpected serviceShopTransactions\"}");
                return;
            }
            respond(
                exchange,
                200,
                "{\"customerAccessToken\":\"ciam-customer-token\",\"allowedServiceShopTransactions\":[\"shop-b\"]}"
            );
        });

        authServer.createContext("/auth/form", exchange -> {
            FORM_AUTH_CALLS.incrementAndGet();
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            if (proxyCloudRunAuthMissing(serverlessAuthorization)) {
                respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
                respond(exchange, 400, "{\"error\":\"wrong content type\"}");
                return;
            }
            Map<String, String> form = parseFormBody(exchange);
            if (!"client_credentials".equals(form.get("grant_type"))
                || !"appointments-client".equals(form.get("client_id"))
                || !"cookie-secret".equals(form.get("client_secret"))
                || !"appointments.read".equals(form.get("scope"))) {
                respond(exchange, 400, "{\"error\":\"unexpected form payload\"}");
                return;
            }
            respond(exchange, 200, "{\"access_token\":\"form-access-token\",\"tenant_token\":\"tenant-42\"}");
        });
    }

    private boolean proxyCloudRunAuthMissing(String serverlessAuthorization) {
        return !"Bearer test-id-token-for:https://oauth-service-ew.a.run.app/".equals(serverlessAuthorization);
    }

    private boolean proxyCloudRunAuthzMissing(String serverlessAuthorization) {
        return !"Bearer test-id-token-for:https://oauthz-service-ew.a.run.app/".equals(serverlessAuthorization);
    }

    private static void assertAuthRequestShape(JsonNode requestBody) {
        if (requestBody.has("sparteGvo") && !requestBody.path("sparteGvo").isArray()) {
            throw new IllegalStateException("Auth request sparteGvo is not an array: " + requestBody);
        }
        if (requestBody.has("btx") && !requestBody.path("btx").isArray()) {
            throw new IllegalStateException("Auth request btx is not an array: " + requestBody);
        }
        if (requestBody.has("pss") && !requestBody.path("pss").isArray()) {
            throw new IllegalStateException("Auth request pss is not an array: " + requestBody);
        }
        if (!requestBody.has("sparteGvo") && !requestBody.has("btx") && !requestBody.has("pss")) {
            throw new IllegalStateException("Auth request does not contain any expected fields: " + requestBody);
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        } finally {
            exchange.close();
        }
    }

    private static void respondXml(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
        String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : rawBody.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                : "";
            values.put(key, value);
        }
        return values;
    }
}
