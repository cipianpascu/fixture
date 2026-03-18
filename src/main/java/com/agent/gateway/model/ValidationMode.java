package com.agent.gateway.model;

public enum ValidationMode {
    STRICT,   // Block operations that fail validation
    WARN,     // Allow operations but log warnings
    OFF       // No validation
}
