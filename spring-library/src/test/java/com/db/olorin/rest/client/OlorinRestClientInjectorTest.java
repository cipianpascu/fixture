package com.db.olorin.rest.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class OlorinRestClientInjectorTest {
    @Test
    void injectsAConfiguredGeneratedStyleInterface() {
        TypedRestClient client = mock(TypedRestClient.class);
        when(client.exchange(eq("products"), any())).thenReturn(new ProductResponse("p-1"));
        Consumer consumer = new Consumer();

        new OlorinRestClientInjector(client).postProcessAfterInitialization(consumer, "consumer");

        assertThat(consumer.backend.create(new ProductRequest("book")).id()).isEqualTo("p-1");
        verify(client).exchange(eq("products"), any());
    }

    @Test
    void mapsGeneratedInterfaceArgumentsToTheirHttpLocations() {
        TypedRestClient client = mock(TypedRestClient.class);
        when(client.exchange(eq("products"), any())).thenReturn(new ProductResponse("p-1"));
        Consumer consumer = new Consumer();
        new OlorinRestClientInjector(client).postProcessAfterInitialization(consumer, "consumer");

        consumer.backend.update("p 1", true, "trace", "session", new ProductRequest("book"));

        verify(client).exchange(eq("products"), argThat((RestRequest<?, ?> request) ->
            request.path().equals("/products/p+1") && request.queryParameters().get("dryRun").equals("true")
                && request.headers().get("X-Trace").equals("trace") && request.cookies().get("session").equals("session")
                && request.body() instanceof ProductRequest));
    }

    static final class Consumer {
        @OlorinRestClient("products")
        ProductBackend backend;
    }
    interface ProductBackend {
        @RestOperation(method = "POST", path = "/products")
        ProductResponse create(ProductRequest request);
        @RestOperation(method = "POST", path = "/products/{id}")
        ProductResponse update(@RestPath("id") String id, @RestQuery("dryRun") boolean dryRun,
            @RestHeader("X-Trace") String trace, @RestCookie("session") String session, @RestBody ProductRequest request);
    }
    record ProductRequest(String name) { }
    record ProductResponse(String id) { }
}
