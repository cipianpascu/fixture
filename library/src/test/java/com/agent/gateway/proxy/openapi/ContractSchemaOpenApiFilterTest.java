package com.agent.gateway.proxy.openapi;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
