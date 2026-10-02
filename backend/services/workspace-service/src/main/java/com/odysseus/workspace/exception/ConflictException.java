package com.odysseus.workspace.exception;

/** Конфликт состояния: дубликат или недопустимый переход. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
