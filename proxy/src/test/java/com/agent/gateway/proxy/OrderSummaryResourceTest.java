package com.agent.gateway.proxy;

import com.agent.gateway.proxy.test.ProxyTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(ProxyTestResource.class)
class OrderSummaryResourceTest extends AbstractProxyQuarkusTest {

    @Test
    void aggregatesTwoBackendsBehindTheConcreteResourceContract() {
        int orderCallsBefore = ProxyTestResource.getOrderDetailsCalls();
        int paymentCallsBefore = ProxyTestResource.getPaymentOrderCalls();

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

        assertEquals(orderCallsBefore + 1, ProxyTestResource.getOrderDetailsCalls());
        assertEquals(paymentCallsBefore + 1, ProxyTestResource.getPaymentOrderCalls());
    }

    @Test
    void exposesTheConcreteResourceContractInOpenApi() {
        given()
            .queryParam("format", "json")
            .when()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .body(
                "paths.'/api/v1/order-summaries/{id}'.get.summary",
                equalTo("Get a combined order and payment summary")
            )
            .body(
                "paths.'/api/v1/order-summaries/{id}'.get.responses.'200'.description",
                equalTo("Combined order and payment summary")
            )
            .body(
                "paths.'/api/v1/order-summaries/{id}'.get.tags[0]",
                equalTo("Order Summaries")
            )
            .body(
                "paths.'/api/v1/order-summaries/{id}/compose'.post.summary",
                equalTo("Compose a chained order and payment summary")
            )
            .body(
                "paths.'/api/v1/order-summaries/{id}/compose'.post.requestBody.required",
                equalTo(true)
            );
    }

    @Test
    void propagatesDownstreamFailureFromOneOfTheComposedBackendCalls() {
        int orderCallsBefore = ProxyTestResource.getOrderDetailsCalls();
        int paymentCallsBefore = ProxyTestResource.getPaymentOrderCalls();

        given()
            .when()
            .get("/api/v1/order-summaries/500")
            .then()
            .statusCode(502)
            .body("error", containsString("payments-down"));

        assertEquals(orderCallsBefore + 1, ProxyTestResource.getOrderDetailsCalls());
        assertEquals(paymentCallsBefore + 1, ProxyTestResource.getPaymentOrderCalls());
    }

    @Test
    void propagatesHeadersAndPayloadAttributesAcrossChainedBackendCalls() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-Id", "corr-321")
            .header("X-Tenant-Id", "tenant-77")
            .body("""
                {
                  "channel": "mobile",
                  "includeHistory": true
                }
                """)
            .when()
            .post("/api/v1/order-summaries/321/compose")
            .then()
            .statusCode(200)
            .body("order.id", equalTo("321"))
            .body("order.status", equalTo("READY"))
            .body("payment.orderId", equalTo("321"))
            .body("payment.paymentStatus", equalTo("PAID"))
            .body("order", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("customerId")))
            .body("order", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("paymentToken")))
            .body("payment", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("internal")));

        assertEquals("corr-321", ProxyTestResource.getLastChainedOrderCorrelationId());
        assertEquals("tenant-77", ProxyTestResource.getLastChainedOrderTenantId());
        assertEquals("mobile", ProxyTestResource.getLastChainedOrderChannel());
        assertEquals("corr-321", ProxyTestResource.getLastChainedPaymentCorrelationId());
        assertEquals("tenant-77", ProxyTestResource.getLastChainedPaymentTenantId());
        assertEquals("mobile", ProxyTestResource.getLastChainedPaymentChannel());
        assertEquals("pay-321", ProxyTestResource.getLastChainedPaymentToken());
        assertEquals("cust-321", ProxyTestResource.getLastChainedPaymentCustomerId());
        assertEquals("includeHistory=true", ProxyTestResource.getLastChainedPaymentQuery());
    }
}
