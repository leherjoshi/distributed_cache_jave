package com.distributedcache.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of configuration validation.
 */
public class ValidationResult {
    private final boolean valid;
    private final List<String> errors;
    
    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors;
    }
    
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }
    
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, new ArrayList<>(errors));
    }
    
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, Collections.singletonList(error));
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    
    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{valid=true}";
        } else {
            return "ValidationResult{valid=false, errors=" + errors + '}';
        }
    }
}
