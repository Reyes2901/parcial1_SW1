package com.workflow.bpm.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidProcessException extends RuntimeException {
    public InvalidProcessException(String message) {
        super(message);
    }
}