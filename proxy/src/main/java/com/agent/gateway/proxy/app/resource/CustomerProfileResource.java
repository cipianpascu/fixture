package com.agent.gateway.proxy.app.resource;

import com.agent.gateway.proxy.app.config.CustomerProfileResourceConfig;
import com.agent.gateway.proxy.app.soap.generated.customerprofile.GetCustomerProfileRequest;
import com.agent.gateway.proxy.app.soap.generated.customerprofile.GetCustomerProfileResponse;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.resource.BaseResource;
import com.agent.gateway.proxy.service.SoapRestBridgeService;
import com.agent.gateway.proxy.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Path("/api/v1/customer-profiles")
@ApplicationScoped
@Slf4j
public class CustomerProfileResource extends BaseResource {

    @Inject
    CustomerProfileResourceConfig resourceConfig;

    @Inject
    SoapRestBridgeService soapRestBridgeService;

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCustomerProfile(
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

        return soapRestBridgeService.invoke(
            resourceConfig.backend(),
            incomingRequest,
            id,
            customerId -> {
                GetCustomerProfileRequest soapRequest = new GetCustomerProfileRequest();
                soapRequest.setCustomerId(customerId);
                return new SoapRestBridgeService.SoapInvocation<>(
                    soapRequest,
                    resourceConfig.soapAction().orElse(null),
                    GetCustomerProfileResponse.class
                );
            },
            soapResponse -> applyResponseContract(
                resourceConfig.schema(),
                contractPath,
                incomingRequest,
                Response.ok(Map.of(
                    "id", soapResponse.getCustomerId(),
                    "fullName", soapResponse.getFullName(),
                    "segment", soapResponse.getSegment()
                )).type(MediaType.APPLICATION_JSON).build()
            )
        );
    }
}
