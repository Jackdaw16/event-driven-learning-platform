package io.github.jackdaw16.learningplatform.catalog.application.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, UUID id) {
        super(resourceName + " with id " + id + " was not found");
    }
}
