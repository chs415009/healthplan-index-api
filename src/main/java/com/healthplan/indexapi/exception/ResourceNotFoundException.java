package com.healthplan.indexapi.exception;

/**
 * objectId not exist
 * HTTP 404 Not Found
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String objectId) {
        super("Resource not found with objectId: " + objectId);
    }
}
