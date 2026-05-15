package com.agent.gateway.proxy.openapi;

import com.agent.gateway.proxy.service.SchemaLoader;
import io.quarkus.arc.Arc;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import java.util.Map;

@OpenApiFilter(OpenApiFilter.RunStage.RUN)
@Slf4j
public class ContractSchemaOpenApiFilter implements OASFilter {

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        SchemaLoader schemaLoader = Arc.container().instance(SchemaLoader.class).get();
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().getPathItems().entrySet()) {
            String publicPath = pathEntry.getKey();
            if (isGenericProxyPath(publicPath)) {
                continue;
            }

            PathItem targetPathItem = pathEntry.getValue();
            for (org.eclipse.microprofile.openapi.models.OpenAPI contractDocument : schemaLoader.getDocumentationSchemas().values()) {
                if (contractDocument.getPaths() == null || contractDocument.getPaths().getPathItems() == null) {
                    continue;
                }

                PathItem contractPathItem = findMatchingPathItem(contractDocument, publicPath);
                if (contractPathItem == null) {
                    continue;
                }

                applyContractPathItem(targetPathItem, contractPathItem);
                mergeComponents(openAPI, contractDocument);
                break;
            }
        }
    }

    private boolean isGenericProxyPath(String publicPath) {
        return publicPath.contains("{backendName}") || publicPath.contains("{path}");
    }

    PathItem findMatchingPathItem(
        org.eclipse.microprofile.openapi.models.OpenAPI contractDocument,
        String publicPath) {

        PathItem exact = contractDocument.getPaths().getPathItem(publicPath);
        if (exact != null) {
            return exact;
        }

        String[] publicSegments = pathSegments(publicPath);
        PathItem bestMatch = null;
        int bestMatchLength = -1;
        for (Map.Entry<String, PathItem> entry : contractDocument.getPaths().getPathItems().entrySet()) {
            String[] contractSegments = pathSegments(entry.getKey());
            if (sameTrailingSegments(publicSegments, contractSegments) && contractSegments.length > bestMatchLength) {
                bestMatch = entry.getValue();
                bestMatchLength = contractSegments.length;
            }
        }

        return bestMatch;
    }

    private boolean sameTrailingSegments(String[] publicSegments, String[] contractSegments) {
        if (contractSegments.length > publicSegments.length) {
            return false;
        }

        int offset = publicSegments.length - contractSegments.length;
        for (int i = 0; i < contractSegments.length; i++) {
            if (!publicSegments[offset + i].equals(contractSegments[i])) {
                return false;
            }
        }
        return true;
    }

    private String[] pathSegments(String path) {
        return java.util.Arrays.stream(path.split("/"))
            .filter(segment -> !segment.isBlank())
            .toArray(String[]::new);
    }

    void applyContractPathItem(PathItem target, PathItem contract) {
        if (contract.getSummary() != null) {
            target.setSummary(contract.getSummary());
        }
        if (contract.getDescription() != null) {
            target.setDescription(contract.getDescription());
        }
        if (contract.getParameters() != null && !contract.getParameters().isEmpty()) {
            target.setParameters(contract.getParameters());
        }

        for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : contract.getOperations().entrySet()) {
            PathItem.HttpMethod method = operationEntry.getKey();
            Operation contractOperation = operationEntry.getValue();
            Operation targetOperation = operationFor(target, method);
            if (targetOperation == null) {
                continue;
            }
            applyContractOperation(targetOperation, contractOperation);
        }
    }

    private Operation operationFor(PathItem pathItem, PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> pathItem.getGET();
            case PUT -> pathItem.getPUT();
            case POST -> pathItem.getPOST();
            case DELETE -> pathItem.getDELETE();
            case OPTIONS -> pathItem.getOPTIONS();
            case HEAD -> pathItem.getHEAD();
            case PATCH -> pathItem.getPATCH();
            case TRACE -> pathItem.getTRACE();
        };
    }

    void applyContractOperation(Operation target, Operation contract) {
        if (contract.getTags() != null && !contract.getTags().isEmpty()) {
            target.setTags(contract.getTags());
        }
        if (contract.getSummary() != null) {
            target.setSummary(contract.getSummary());
        }
        if (contract.getDescription() != null) {
            target.setDescription(contract.getDescription());
        }
        if (contract.getExternalDocs() != null) {
            target.setExternalDocs(contract.getExternalDocs());
        }
        if (contract.getOperationId() != null) {
            target.setOperationId(contract.getOperationId());
        }
        if (contract.getParameters() != null && !contract.getParameters().isEmpty()) {
            target.setParameters(contract.getParameters());
        }
        if (contract.getRequestBody() != null) {
            target.setRequestBody(contract.getRequestBody());
        }
        if (contract.getResponses() != null && !contract.getResponses().getAPIResponses().isEmpty()) {
            target.setResponses(contract.getResponses());
        }
        if (contract.getCallbacks() != null && !contract.getCallbacks().isEmpty()) {
            target.setCallbacks(contract.getCallbacks());
        }
        if (contract.getDeprecated() != null) {
            target.setDeprecated(contract.getDeprecated());
        }
        if (contract.getSecurity() != null && !contract.getSecurity().isEmpty()) {
            target.setSecurity(contract.getSecurity());
        }
        if (contract.getServers() != null && !contract.getServers().isEmpty()) {
            target.setServers(contract.getServers());
        }
        if (contract.getExtensions() != null && !contract.getExtensions().isEmpty()) {
            target.setExtensions(contract.getExtensions());
        }
    }

    private void mergeComponents(OpenAPI target, org.eclipse.microprofile.openapi.models.OpenAPI contractDocument) {
        Components contractComponents = contractDocument.getComponents();
        if (contractComponents == null) {
            return;
        }

        if (target.getComponents() == null) {
            target.setComponents(OASFactory.createComponents());
        }

        Components targetComponents = target.getComponents();
        mergeComponentMap(contractComponents.getSchemas(), targetComponents::addSchema);
        mergeComponentMap(contractComponents.getResponses(), targetComponents::addResponse);
        mergeComponentMap(contractComponents.getParameters(), targetComponents::addParameter);
        mergeComponentMap(contractComponents.getExamples(), targetComponents::addExample);
        mergeComponentMap(contractComponents.getRequestBodies(), targetComponents::addRequestBody);
        mergeComponentMap(contractComponents.getHeaders(), targetComponents::addHeader);
        mergeComponentMap(contractComponents.getSecuritySchemes(), targetComponents::addSecurityScheme);
        mergeComponentMap(contractComponents.getLinks(), targetComponents::addLink);
        mergeComponentMap(contractComponents.getCallbacks(), targetComponents::addCallback);
    }

    private <T> void mergeComponentMap(Map<String, T> source, ComponentAdder<T> adder) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach(adder::add);
    }

    @FunctionalInterface
    private interface ComponentAdder<T> {
        void add(String name, T value);
    }
}
