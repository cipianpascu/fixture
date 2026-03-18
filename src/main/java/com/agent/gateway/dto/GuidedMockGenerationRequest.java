package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidedMockGenerationRequest {
    private boolean generateEndpoints; // Generate mock endpoints from schema
    private boolean generateResponses; // Generate mock responses from schema
    
    /**
     * Guided values for response generation
     * Format: "path.to.property" -> value
     * Examples:
     *   "id" -> 123
     *   "user.name" -> "John Doe"
     *   "items[].__size" -> 5  (array size)
     *   "items[0].id" -> 1     (specific array item)
     */
    private Map<String, Object> guidedValues;
}
