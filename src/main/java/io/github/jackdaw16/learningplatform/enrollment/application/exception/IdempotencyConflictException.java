package io.github.jackdaw16.learningplatform.enrollment.application.exception;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;

public final class IdempotencyConflictException extends ConflictException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key " + idempotencyKey + " is already associated with another enrollment request");
    }
}
