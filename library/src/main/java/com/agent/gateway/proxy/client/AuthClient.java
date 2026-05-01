package com.agent.gateway.proxy.client;

import com.agent.gateway.proxy.auth.AuthRequest;
import com.agent.gateway.proxy.auth.AuthTokens;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Auth Service REST Client (Quarkus)
 */
@Path("/auth/tokens")
@RegisterRestClient(configKey = "auth-service")
public interface AuthClient {
    
    @POST
    @Path("/{sessionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    AuthTokens getTokens(
        @PathParam("sessionId") String sessionId,
        AuthRequest request
    );
}
