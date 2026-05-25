package com.agent.gateway.proxy;

import com.agent.gateway.proxy.test.ProxyTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(ProxyTestResource.class)
class CustomerProfileResourceTest extends AbstractProxyQuarkusTest {

    @Test
    void mapsRestResourceToSoapBackendUsingJwtAuth() {
        given()
            .header("X-Session-Id", "soap-session")
            .when()
            .get("/api/v1/customer-profiles/321")
            .then()
            .statusCode(200)
            .body("id", equalTo("321"))
            .body("fullName", equalTo("Jane Doe"))
            .body("segment", equalTo("GOLD"));

        assertEquals("customer-token", ProxyTestResource.getLastSoapAuthToken());
        assertEquals("\"urn:GetCustomerProfile\"", ProxyTestResource.getLastSoapAction());
        assertEquals("POST", ProxyTestResource.getLastSoapMethod());
    }

    @Test
    void returnsBadGatewayWhenSoapBackendRespondsWithFault() {
        given()
            .header("X-Session-Id", "soap-session")
            .when()
            .get("/api/v1/customer-profiles/fault")
            .then()
            .statusCode(502)
            .body("error", containsString("customer profile unavailable"));
    }

    @Test
    void rejectsSoapBackendsOnTheGenericProxyRoute() {
        given()
            .when()
            .get("/api/v1/customer-profile-soap-service/anything")
            .then()
            .statusCode(400)
            .body("error", containsString("must be handled by a concrete resource"));
    }
}
