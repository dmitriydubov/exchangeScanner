package com.exchange.scanner.security.handler;

import com.exchange.scanner.security.error.NoSuchUserException;
import com.exchange.scanner.security.error.RefreshTokenException;
import com.exchange.scanner.security.error.UserAlreadyExistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class WebAppExceptionHandler {

    @ExceptionHandler(value = RefreshTokenException.class)
    public ResponseEntity<ErrorResponseBody> refreshTokenExceptionHandler(RefreshTokenException ex, WebRequest webRequest) {
        return buildResponse(HttpStatus.FORBIDDEN, ex, webRequest);
    }

    @ExceptionHandler(value = UserAlreadyExistException.class)
    public ResponseEntity<ErrorResponseBody> alreadyExistHandler(UserAlreadyExistException ex, WebRequest webRequest) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex, webRequest);
    }

    @ExceptionHandler(value = NoSuchUserException.class)
    public ResponseEntity<ErrorResponseBody> noSuchUserHandler(NoSuchUserException ex, WebRequest webRequest) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex, webRequest);
    }


    private ResponseEntity<ErrorResponseBody> buildResponse(HttpStatus httpStatus, Exception ex, WebRequest webRequest) {
        return ResponseEntity
                .status(httpStatus)
                .body(ErrorResponseBody.builder()
                    .message(ex.getMessage())
                    .description(webRequest.getDescription(false))
                    .build());
    }
}
