package com.agent.gateway.proxy;

import com.agent.gateway.proxy.test.ProxyTestResource;
import com.agent.gateway.proxy.service.auth.GoogleCloudRunIdTokenProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(ProxyTestResource.class)
class ProxyResourceTest {

    @BeforeAll
    static void installCloudRunTokenProviderMock() {
        QuarkusMock.installMockForType(
            new GoogleCloudRunIdTokenProvider() {
                @Override
                public String getIdToken(String audience) {
                    return "test-id-token-for:" + audience;
                }
            },
            GoogleCloudRunIdTokenProvider.class
        );
    }

    @Test
    void loadsConfiguredSchemasAndForwardsRequests() {
        given()
            .when()
            .get("/api/v1/secondary-service/ping")
            .then()
            .statusCode(200)
            .body("status", equalTo("secondary-ok"));
    }

    @Test
    void validatesRequestBodiesForTemplatedPaths() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .put("/api/v1/templated-service/items/123")
            .then()
            .statusCode(400)
            .body("error", equalTo("Request validation failed"))
            .body("details.toString()", containsString("is missing but it is required"));
    }

    @Test
    void rejectsUnsupportedAuthTypes() {
        given()
            .when()
            .get("/api/v1/unsupported-auth-service/ping")
            .then()
            .statusCode(500)
            .body("error", containsString("Unsupported securityType"));
    }

    @Test
    void rejectsMisconfiguredBasicAuth() {
        given()
            .when()
            .get("/api/v1/misconfigured-basic-service/ping")
            .then()
            .statusCode(500)
            .body("error", containsString("username/password missing"));
    }

    @Test
    void requiresSessionForJwtBackends() {
        given()
            .when()
            .get("/api/v1/jwt-service/ping")
            .then()
            .statusCode(401)
            .body("error", equalTo("Missing session identifier for JWT-authenticated backend"));
    }

    @Test
    void retriesTransientAuthServiceFailures() {
        given()
            .header("X-Session-Id", "retry-session")
            .when()
            .get("/api/v1/jwt-service/ping")
            .then()
            .statusCode(200)
            .body("status", equalTo("jwt-ok"));

        assertEquals(2, ProxyTestResource.getRetrySessionCalls());
    }

    @Test
    void authenticatesToPrivateCloudRunAuthServiceForJwtBackends() {
        given()
            .header("X-Session-Id", "cloudrun-auth-session")
            .when()
            .get("/api/v1/jwt-service/ping")
            .then()
            .statusCode(200)
            .body("status", equalTo("jwt-ok"));

        assertEquals(1, ProxyTestResource.getCloudRunAuthSessionCalls());
    }

    @Test
    void attachesCloudRunIdTokenToServerlessAuthorizationHeader() {
        given()
            .header("Authorization", "Bearer original-user-token")
            .when()
            .get("/api/v1/cloudrun-service/ping")
            .then()
            .statusCode(200)
            .body(
                "serverlessAuthorization",
                equalTo("Bearer test-id-token-for:https://orders-service-ew.a.run.app/")
            );
    }
}
