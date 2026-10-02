package com.odysseus.workspace.exception;

/** Бизнес-запрет операции для текущего пользователя. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
