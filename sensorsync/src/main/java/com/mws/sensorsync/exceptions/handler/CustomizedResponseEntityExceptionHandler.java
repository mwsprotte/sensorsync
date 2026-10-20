package com.mws.sensorsync.exceptions.handler;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.exceptions.ExceptionResponse;
import com.mws.sensorsync.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Date;

@ControllerAdvice
@RestController
public class CustomizedResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomizedResponseEntityExceptionHandler.class);

    private ResponseEntity<ExceptionResponse> build(HttpStatus status, String message, WebRequest request) {
        return new ResponseEntity<>(new ExceptionResponse(new Date(), message, request.getDescription(false)), status);
    }

    @ExceptionHandler(ApiException.class)
    public final ResponseEntity<ExceptionResponse> handleApi(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public final ResponseEntity<ExceptionResponse> handleNotFound(Exception ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public final ResponseEntity<ExceptionResponse> handleDenied(Exception ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "Acesso negado", request);
    }

    @ExceptionHandler(Exception.class)
    public final ResponseEntity<ExceptionResponse> handleAll(Exception ex, WebRequest request) {
        log.error("Erro nao tratado em {}", request.getDescription(false), ex);
        // Nao devolve detalhes internos (mensagens de SQL etc.) para o cliente
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor", request);
    }
}
