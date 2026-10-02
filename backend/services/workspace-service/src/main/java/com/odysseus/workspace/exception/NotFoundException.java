package com.odysseus.workspace.exception;

/** Ресурс не найден или принадлежит другому workspace (для клиента это одно и то же). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
