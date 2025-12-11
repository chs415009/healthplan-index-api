package com.healthplan.indexapi.exception;

/**
 * HTTP 409 Conflict
 */
public class ResourceAlreadyExistsException extends RuntimeException {

    public ResourceAlreadyExistsException(String objectId) {
        super("Resource already exists with objectId: " + objectId);
    }
}
