package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Path("/api/v1/order-summaries")
@ApplicationScoped
@Slf4j
public class OrderSummaryResource extends BaseResource {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GET
    @Path("/{id}")
    public Response getOrderSummary(
        @PathParam("id") String id,
        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Context ContainerRequestContext requestContext) {
        ProxyProperties.OrderSummaryResourceConfig resourceConfig = proxyProperties.resources()
            .flatMap(ProxyProperties.ResourceConfig::orderSummary)
            .orElseThrow(() -> new IllegalStateException("Order summary resource config is not available"));
        ProxyRequestContext incomingRequest = toRequestContext(uriInfo, httpHeaders, requestContext);
        String contractPath = extractContractPath(incomingRequest.requestUri(), "/api/v1");

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
            JsonNode order = parseEntity(ordersResponse);
            JsonNode payment = parseEntity(paymentsResponse);
            return Response.ok(Map.of(
                "order", order,
                "payment", payment
            )).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Failed to compose order summary response", e);
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "Failed to compose order summary response"))
                .build();
        }
    }

    private JsonNode parseEntity(Response response) throws Exception {
        Object entity = response.getEntity();
        if (entity == null) {
            return objectMapper.nullNode();
        }
        if (entity instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.readTree(String.valueOf(entity));
    }

    private String backendProxyPath(String backendName, String downstreamPath) {
        return "/api/v1/" + backendName + downstreamPath;
    }

    private String materializePath(String template, String id) {
        return template.replace("{id}", id);
    }
}
