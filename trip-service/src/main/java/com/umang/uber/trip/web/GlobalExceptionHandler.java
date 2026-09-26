package com.umang.uber.trip.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.common.api.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(IllegalArgumentException ex) {
        return ApiResponse.failure(ErrorResponse.builder()
                .code("TRIP_NOT_FOUND").message(ex.getMessage()).build());
    }

    // An illegal state-machine transition is a client error, not a 404.
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleIllegalTransition(IllegalStateException ex) {
        return ApiResponse.failure(ErrorResponse.builder()
                .code("ILLEGAL_TRIP_TRANSITION").message(ex.getMessage()).build());
    }
}
