package com.agent.gateway.proxy.openapi;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.service.SchemaLoader;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractSchemaOpenApiFilterTest {

    private final ContractSchemaOpenApiFilter filter = new ContractSchemaOpenApiFilter();

    @Test
    void preservesScannedParametersWhenContractOperationIsSparse() {
        Operation scannedOperation = OASFactory.createOperation()
            .summary("scanned summary")
            .parameters(List.of(
                OASFactory.createParameter()
                    .name("id")
                    .in(Parameter.In.PATH)
                    .required(true)
            ));

        Operation contractOperation = OASFactory.createOperation()
            .summary("contract summary");

        filter.applyContractOperation(scannedOperation, contractOperation);

        assertEquals("contract summary", scannedOperation.getSummary());
        assertNotNull(scannedOperation.getParameters());
        assertEquals(1, scannedOperation.getParameters().size());
        assertEquals("id", scannedOperation.getParameters().get(0).getName());
        assertEquals(Parameter.In.PATH, scannedOperation.getParameters().get(0).getIn());
    }

    @Test
    void matchesContractPathsByTrailingSegmentsWhenPublicPrefixIsPresent() {
        OpenAPI contractDocument = OASFactory.createOpenAPI();
        contractDocument.setPaths(OASFactory.createPaths());
        PathItem contractPathItem = OASFactory.createPathItem();
        contractDocument.getPaths().addPathItem("/order-summaries/{id}", contractPathItem);

        PathItem matched = filter.findMatchingPathItem(contractDocument, "/api/v1/order-summaries/{id}");

        assertEquals(contractPathItem, matched);
    }

    @Test
    void prefersTheMostSpecificTrailingPathMatch() {
        OpenAPI contractDocument = OASFactory.createOpenAPI();
        contractDocument.setPaths(OASFactory.createPaths());
        PathItem genericTransactions = OASFactory.createPathItem();
        PathItem productTransactions = OASFactory.createPathItem();
        contractDocument.getPaths().addPathItem("/transactions", genericTransactions);
        contractDocument.getPaths().addPathItem("/products/{productId}/transactions", productTransactions);

        PathItem matched = filter.findMatchingPathItem(
            contractDocument,
            "/api/v1/products/{productId}/transactions"
        );

        assertEquals(productTransactions, matched);
    }

    @Test
    void doesNotAddOperationsThatWereNotScanned() {
        PathItem scannedPathItem = OASFactory.createPathItem();
        scannedPathItem.setGET(OASFactory.createOperation().summary("scanned get"));

        PathItem contractPathItem = OASFactory.createPathItem();
        contractPathItem.setGET(OASFactory.createOperation().summary("contract get"));
        contractPathItem.setPOST(OASFactory.createOperation().summary("contract post"));

        filter.applyContractPathItem(scannedPathItem, contractPathItem);

        assertEquals("contract get", scannedPathItem.getGET().getSummary());
        assertEquals(null, scannedPathItem.getPOST());
    }

    @Test
    void extractsLastStaticSegmentBeforeTemplateWithoutRelyingOnApiPrefix() {
        assertEquals(Optional.of("order-summaries"), filter.bindingSegment("/api/v1/order-summaries/{id}/compose"));
        assertEquals(Optional.of("customer-profiles"), filter.bindingSegment("/api/v1/customer-profiles/{id}"));
        assertEquals(Optional.of("order-summaries"), filter.bindingSegment("/public/order-summaries/{id}/compose"));
    }

    @Test
    void prefersSchemaBoundByBackendNameWhenAvailable() {
        OpenAPI boundDocument = OASFactory.createOpenAPI();
        SchemaLoader schemaLoader = new SchemaLoader() {
            @Override
            public Optional<org.eclipse.microprofile.openapi.models.OpenAPI> getDocumentationSchema(String schemaName) {
                return "order-summary.yaml".equals(schemaName)
                    ? Optional.of(boundDocument)
                    : Optional.empty();
            }
        };

        Optional<org.eclipse.microprofile.openapi.models.OpenAPI> result = filter.findBoundContractDocument(
            "/api/v1/order-summaries/{id}/compose",
            proxyProperties("order-summaries", "order-summary.yaml"),
            schemaLoader
        );

        assertTrue(result.isPresent());
        assertEquals(boundDocument, result.get());
    }

    private ProxyProperties proxyProperties(String backendName, String schemaName) {
        return new ProxyProperties() {
            @Override
            public SchemaConfig schemas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TlsConfig> tls() {
                return Optional.empty();
            }

            @Override
            public List<BackendDefinition> backends() {
                return List.of(backend(backendName, schemaName));
            }
        };
    }

    private ProxyProperties.BackendDefinition backend(String name, String schema) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String baseUrl() {
                return "http://backend.example.com";
            }

            @Override
            public String path() {
                return "/api";
            }

            @Override
            public Optional<String> schema() {
                return Optional.of(schema);
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(5);
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String protocol() {
                return "rest";
            }

            @Override
            public String httpVersion() {
                return "http1_1";
            }

            @Override
            public Optional<String> securityType() {
                return Optional.empty();
            }

            @Override
            public Map<String, String> securityConfig() {
                return Map.of();
            }

            @Override
            public Optional<ProxyProperties.AuthRequestConfig> authRequest() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.AuthzRequestConfig> authzRequest() {
                return Optional.empty();
            }

            @Override
            public Optional<String> tlsProfile() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.SoapConfig> soap() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.ProxyConfig> proxy() {
                return Optional.empty();
            }
        };
    }
}
