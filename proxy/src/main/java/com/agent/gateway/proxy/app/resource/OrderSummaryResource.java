package com.agent.gateway.proxy.app.resource;

import com.agent.gateway.proxy.app.config.OrderSummaryResourceConfig;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.resource.BaseResource;
import com.agent.gateway.proxy.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Path("/api/v1/order-summaries")
@ApplicationScoped
@Slf4j
public class OrderSummaryResource extends BaseResource {
    private static final String HEADER_CORRELATION_ID = "x-correlation-id";
    private static final String HEADER_TENANT_ID = "x-tenant-id";
    private static final String HEADER_CLIENT_CHANNEL = "x-client-channel";
    private static final String HEADER_PAYMENT_TOKEN = "x-payment-token";
    private static final String HEADER_CUSTOMER_ID = "x-customer-id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    OrderSummaryResourceConfig resourceConfig;

    @GET
    @Path("/{id}")
    public Response getOrderSummary(
        @PathParam("id") String id,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Context ContainerRequestContext requestContext) {
        ProxyRequestContext incomingRequest = toRequestContext(uriInfo, httpHeaders, requestContext);
        String contractPath = incomingRequest.requestUri();

        if (proxyProperties.schemas().validateRequests()) {
            ValidationResult validation = validateContract(resourceConfig.schema(), contractPath, incomingRequest, null);
            if (!validation.isValid()) {
                return requestValidationFailed(incomingRequest, contractPath, validation);
            }
        }

        ProxyProperties.BackendDefinition ordersBackend = findBackend(resourceConfig.ordersBackend());
        if (ordersBackend == null) {
            return backendNotFound(resourceConfig.ordersBackend());
        }
        if (!isBackendEnabled(ordersBackend)) {
            return backendDisabled(resourceConfig.ordersBackend());
        }

        ProxyProperties.BackendDefinition paymentsBackend = findBackend(resourceConfig.paymentsBackend());
        if (paymentsBackend == null) {
            return backendNotFound(resourceConfig.paymentsBackend());
        }
        if (!isBackendEnabled(paymentsBackend)) {
            return backendDisabled(resourceConfig.paymentsBackend());
        }

        Response ordersResponse = forward(
            ordersBackend,
            deriveRequestContext(
                incomingRequest,
                "GET",
                backendProxyPath(resourceConfig.ordersBackend(), materializePath(resourceConfig.ordersPathTemplate(), id)),
                null
            ),
            null
        );
        if (ordersResponse.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            return ordersResponse;
        }

        Response paymentsResponse = forward(
            paymentsBackend,
            deriveRequestContext(
                incomingRequest,
                "GET",
                backendProxyPath(
                    resourceConfig.paymentsBackend(),
                    materializePath(resourceConfig.paymentsPathTemplate(), id)
                ),
                null
            ),
            null
        );
        if (paymentsResponse.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            return paymentsResponse;
        }

        try {
            Optional<JsonNode> order = parseSuccessfulJsonResponse(ordersResponse);
            if (order.isEmpty()) {
                return ordersResponse;
            }

            Optional<JsonNode> payment = parseSuccessfulJsonResponse(paymentsResponse);
            if (payment.isEmpty()) {
                return paymentsResponse;
            }

            Response response = Response.ok(Map.of(
                "order", order.get(),
                "payment", payment.get()
            )).type(MediaType.APPLICATION_JSON).build();
            return applyResponseContract(resourceConfig.schema(), contractPath, incomingRequest, response);
        } catch (Exception e) {
            log.error("Failed to compose order summary response", e);
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "Failed to compose order summary response"))
                .build();
        }
    }

    @POST
    @Path("/{id}/compose")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response composeOrderSummary(
        @PathParam("id") String id,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Context ContainerRequestContext requestContext,
        String requestBody) {
        ProxyRequestContext incomingRequest = toRequestContext(uriInfo, httpHeaders, requestContext);
        String contractPath = incomingRequest.requestUri();

        if (proxyProperties.schemas().validateRequests()) {
            ValidationResult validation = validateContract(resourceConfig.schema(), contractPath, incomingRequest, requestBody);
            if (!validation.isValid()) {
                return requestValidationFailed(incomingRequest, contractPath, validation);
            }
        }

        ProxyProperties.BackendDefinition ordersBackend = findBackend(resourceConfig.ordersBackend());
        if (ordersBackend == null) {
            return backendNotFound(resourceConfig.ordersBackend());
        }
        if (!isBackendEnabled(ordersBackend)) {
            return backendDisabled(resourceConfig.ordersBackend());
        }

        ProxyProperties.BackendDefinition paymentsBackend = findBackend(resourceConfig.paymentsBackend());
        if (paymentsBackend == null) {
            return backendNotFound(resourceConfig.paymentsBackend());
        }
        if (!isBackendEnabled(paymentsBackend)) {
            return backendDisabled(resourceConfig.paymentsBackend());
        }

        try {
            JsonNode requestJson = objectMapper.readTree(requestBody);
            ProxyRequestContext ordersRequest = derivedRequestWithHeaders(
                incomingRequest,
                "GET",
                backendProxyPath(resourceConfig.ordersBackend(), materializePath(resourceConfig.ordersPathTemplate(), id)),
                null,
                Map.of(
                    HEADER_CORRELATION_ID, incomingRequest.header(HEADER_CORRELATION_ID),
                    HEADER_TENANT_ID, incomingRequest.header(HEADER_TENANT_ID),
                    HEADER_CLIENT_CHANNEL, textOrNull(requestJson, "channel")
                )
            );

            Response ordersResponse = forward(ordersBackend, ordersRequest, null);
            if (ordersResponse.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                return ordersResponse;
            }

            Optional<JsonNode> order = parseSuccessfulJsonResponse(ordersResponse);
            if (order.isEmpty()) {
                return ordersResponse;
            }

            ProxyRequestContext paymentsRequest = derivedRequestWithHeaders(
                incomingRequest,
                "GET",
                backendProxyPath(
                    resourceConfig.paymentsBackend(),
                    materializePath(resourceConfig.paymentsPathTemplate(), id)
                ),
                paymentsQueryString(requestJson),
                Map.of(
                    HEADER_CORRELATION_ID, incomingRequest.header(HEADER_CORRELATION_ID),
                    HEADER_TENANT_ID, incomingRequest.header(HEADER_TENANT_ID),
                    HEADER_CLIENT_CHANNEL, textOrNull(requestJson, "channel"),
                    HEADER_PAYMENT_TOKEN, textAtOrNull(order.get(), "/paymentToken"),
                    HEADER_CUSTOMER_ID, textAtOrNull(order.get(), "/customerId")
                )
            );

            Response paymentsResponse = forward(paymentsBackend, paymentsRequest, null);
            if (paymentsResponse.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                return paymentsResponse;
            }

            Optional<JsonNode> payment = parseSuccessfulJsonResponse(paymentsResponse);
            if (payment.isEmpty()) {
                return paymentsResponse;
            }

            Response response = Response.ok(Map.of(
                "order", order.get(),
                "payment", payment.get()
            )).type(MediaType.APPLICATION_JSON).build();
            return applyResponseContract(resourceConfig.schema(), contractPath, incomingRequest, response);
        } catch (Exception e) {
            log.error("Failed to compose chained order summary response", e);
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "Failed to compose chained order summary response"))
                .build();
        }
    }

    private String backendProxyPath(String backendName, String downstreamPath) {
        return "/api/v1/" + backendName + downstreamPath;
    }

    private String materializePath(String template, String id) {
        return template.replace("{id}", id);
    }

    private ProxyRequestContext derivedRequestWithHeaders(
        ProxyRequestContext source,
        String method,
        String requestUri,
        String queryString,
        Map<String, String> additionalHeaders) {
        Map<String, List<String>> headers = new LinkedHashMap<>(source.headers());
        additionalHeaders.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                return;
            }
            headers.put(name.toLowerCase(Locale.ROOT), List.of(value));
        });
        return buildRequestContextWithHeaderLists(
            method,
            requestUri,
            queryString,
            headers,
            source.cookies()
        );
    }

    private String paymentsQueryString(JsonNode requestJson) {
        JsonNode includeHistory = requestJson.path("includeHistory");
        if (includeHistory.isMissingNode() || includeHistory.isNull()) {
            return null;
        }
        return "includeHistory=" + URLEncoder.encode(includeHistory.asText(), StandardCharsets.UTF_8);
    }

    private String textOrNull(JsonNode jsonNode, String fieldName) {
        JsonNode value = jsonNode.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String textAtOrNull(JsonNode jsonNode, String jsonPointer) {
        JsonNode value = jsonNode.at(jsonPointer);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
