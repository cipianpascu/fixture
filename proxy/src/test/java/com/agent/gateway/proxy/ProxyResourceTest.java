package com.agent.gateway.proxy;

import com.agent.gateway.proxy.test.ProxyTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(ProxyTestResource.class)
class ProxyResourceTest extends AbstractProxyQuarkusTest {

    @Test
    void loadsConfiguredSchemasAndForwardsRequests() {
        given()
            .when()
            .get("/api/v1/secondary-service/ping")
            .then()
            .statusCode(200)
            .body("status", equalTo("secondary-ok"))
            .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("internal")));
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
    void handlesRecursiveRequestSchemasWithoutFailingStartup() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "name": "root",
                  "children": [
                    {
                      "name": "child",
                      "children": []
                    }
                  ]
                }
                """)
            .when()
            .post("/api/v1/recursive-service/tree")
            .then()
            .statusCode(200)
            .body("ok", equalTo(true));
    }

    @Test
    void validatesPathParametersAgainstTheirDeclaredSchema() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"valid\"}")
            .when()
            .put("/api/v1/templated-service/items/not-a-number")
            .then()
            .statusCode(400)
            .body("error", equalTo("Request validation failed"))
            .body("details.toString()", containsString("path parameter 'id'"));
    }

    @Test
    void validatesQueryHeaderAndCookieParametersAgainstTheirDeclaredSchema() {
        given()
            .queryParam("limit", "100")
            .header("X-Tenant", "tenant-acme")
            .cookie("mode", "full")
            .when()
            .get("/api/v1/parameter-service/search/123")
            .then()
            .statusCode(400)
            .body("error", equalTo("Request validation failed"))
            .body("details.toString()", containsString("query parameter 'limit'"));

        given()
            .queryParam("limit", "10")
            .header("X-Tenant", "wrong")
            .cookie("mode", "full")
            .when()
            .get("/api/v1/parameter-service/search/123")
            .then()
            .statusCode(400)
            .body("details.toString()", containsString("header parameter 'X-Tenant'"));

        given()
            .queryParam("limit", "10")
            .header("X-Tenant", "tenant-acme")
            .cookie("mode", "broken")
            .when()
            .get("/api/v1/parameter-service/search/123")
            .then()
            .statusCode(400)
            .body("details.toString()", containsString("cookie parameter 'mode'"));
    }

    @Test
    void allowsRequestsWhenAllDeclaredParametersAreValid() {
        given()
            .queryParam("limit", "10")
            .header("X-Tenant", "tenant-acme")
            .cookie("mode", "full")
            .when()
            .get("/api/v1/parameter-service/search/123")
            .then()
            .statusCode(200)
            .body("ok", equalTo(true))
            .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("debug")));
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
    void mapsJwtTokensToApigeeStyleHeaders() {
        Response response = given()
            .header("X-Session-Id", "apigee-session")
            .when()
            .get("/api/v1/jwt-apigee-service/ping")
            .then()
            .statusCode(200)
            .body("status", equalTo("jwt-ok"))
            .extract()
            .response();

        assertEquals("Bearer customer-token", ProxyTestResource.getLastApigeeAuthorization());
        assertEquals("test-apigee-key", ProxyTestResource.getLastApigeeApiKey());
        assertNull(response.getHeader("Authorization"));
        assertNull(response.getHeader("x-api-key"));
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void mapsJwtTokensToGlueStyleHeaders() {
        Response response = given()
            .header("X-Session-Id", "glue-session")
            .when()
            .get("/api/v1/jwt-glue-service/ping")
            .then()
            .statusCode(200)
            .body("status", equalTo("jwt-ok"))
            .extract()
            .response();

        assertEquals("Bearer authz-token", ProxyTestResource.getLastGlueAuthorization());
        assertEquals("glue-token", ProxyTestResource.getLastGlueToken());
        assertNull(response.getHeader("Authorization"));
        assertNull(response.getHeader("X-Glue-Token"));
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
            )
            .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("internal")));
    }

    @Test
    void aggregatesMultipleBackendCallsBehindASingleResourceContract() {
        given()
            .when()
            .get("/api/v1/order-summaries/123")
            .then()
            .statusCode(200)
            .body("order.id", equalTo("123"))
            .body("order.status", equalTo("READY"))
            .body("payment.orderId", equalTo("123"))
            .body("payment.paymentStatus", equalTo("PAID"))
            .body("order", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("internal")))
            .body("payment", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("internal")));
    }

    @Test
    void decoratesConcreteResourceOperationsFromTheirContractSchemas() {
        given()
            .queryParam("format", "json")
            .when()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .body("paths.'/api/v1/order-summaries/{id}'.get.summary",
                equalTo("Get a combined order and payment summary"))
            .body("paths.'/api/v1/order-summaries/{id}'.get.responses.'200'.description",
                equalTo("Combined order and payment summary"))
            .body("paths.'/api/v1/order-summaries/{id}'.get.tags[0]",
                equalTo("Order Summaries"));
    }
}
