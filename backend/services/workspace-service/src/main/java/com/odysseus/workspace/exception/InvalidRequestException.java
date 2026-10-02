package com.odysseus.workspace.exception;

/** Запрос нарушает инвариант, который не выразить аннотациями валидации. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
