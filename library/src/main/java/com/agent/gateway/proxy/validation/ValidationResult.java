package com.agent.gateway.proxy.validation;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of schema validation
 */
@Data
@Builder
public class ValidationResult {
    
    private boolean valid;
    private List<String> errors;
    
    public static ValidationResult allowed() {
        return ValidationResult.builder()
            .valid(true)
            .errors(new ArrayList<>())
            .build();
    }
    
    public static ValidationResult rejected(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return ValidationResult.builder()
            .valid(false)
            .errors(errors)
            .build();
    }
    
    public static ValidationResult rejected(List<String> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errors(errors)
            .build();
    }
}
