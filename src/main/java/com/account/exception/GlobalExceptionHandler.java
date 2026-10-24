package com.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler for handling exceptions across the application.
 * This class intercepts exceptions thrown by controllers and provides a
 * consistent response format.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyExistsException(AccountAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse(
            java.time.LocalDateTime.now(),
            HttpStatus.CONFLICT.value(),
            "An unexpected error occurred during Account Registration.",
            ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidLookupValueException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLookupValueException(InvalidLookupValueException ex) {
        ErrorResponse error = new ErrorResponse(
            java.time.LocalDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Invalid account request.",
            ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ErrorResponse error = new ErrorResponse(
            java.time.LocalDateTime.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred.",
            ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
