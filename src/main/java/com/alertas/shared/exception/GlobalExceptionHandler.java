package com.alertas.shared.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String DATOS_INVALIDOS = "Los datos enviados no son validos";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> api(ApiException e) {
        return responder(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {

        // se muestra el primer error, el front valida lo demas
        List<FieldError> errores = e.getBindingResult().getFieldErrors();

        if (errores.isEmpty()) {
            return responder(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS);
        }

        return responder(HttpStatus.BAD_REQUEST, errores.get(0).getDefaultMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> validacion(ConstraintViolationException e) {

        Set<ConstraintViolation<?>> errores = e.getConstraintViolations();

        if (errores.isEmpty()) {
            return responder(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS);
        }

        ConstraintViolation<?> primero = errores.iterator().next();
        return responder(HttpStatus.BAD_REQUEST, primero.getMessage());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> malFormado(Exception e) {
        return responder(HttpStatus.BAD_REQUEST, "La solicitud no tiene el formato esperado");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> faltaCabecera(MissingRequestHeaderException e) {
        return responder(HttpStatus.BAD_REQUEST, "Falta la cabecera " + e.getHeaderName());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException e) {

        // normalmente un unique o un check de la bd que el servicio no alcanzo a validar
        log.warn("Violacion de integridad: {}", e.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "El registro choca con otro que ya existe o no cumple las reglas");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> concurrencia(OptimisticLockingFailureException e) {
        return responder(HttpStatus.CONFLICT, "Otra persona modifico este registro. Recarga e intenta de nuevo");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> prohibido(AccessDeniedException e) {
        return responder(HttpStatus.FORBIDDEN, "No tienes permiso para esta accion");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noExiste(NoResourceFoundException e) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodo(HttpRequestMethodNotSupportedException e) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Metodo no permitido");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception e) {

        log.error("Error no controlado", e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado, intenta mas tarde");
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
