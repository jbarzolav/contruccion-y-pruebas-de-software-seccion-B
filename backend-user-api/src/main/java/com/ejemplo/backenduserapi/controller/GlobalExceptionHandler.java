package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.exception.EstadoInvalidoException;
import com.ejemplo.backenduserapi.exception.ImagenInvalidaException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.PropietarioInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Validaciones Jakarta del cuerpo de la petición -> 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacion(MethodArgumentNotValidException ex) {

        List<String> mensajes = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .toList();

        return responder(HttpStatus.BAD_REQUEST, "Validación fallida", mensajes);
    }

    /**
     * Producto inexistente -> 404 Not Found.
     */
    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleProductoNoEncontrado(ProductoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, "Producto no encontrado", List.of(ex.getMessage()));
    }

    /**
     * Producto de otro vendedor -> 403 Forbidden.
     */
    @ExceptionHandler(PropietarioInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handlePropietarioInvalido(PropietarioInvalidoException ex) {
        return responder(HttpStatus.FORBIDDEN, "Acceso denegado", List.of(ex.getMessage()));
    }

    /**
     * Imagen vacía o formato no permitido -> 400 Bad Request.
     */
    @ExceptionHandler(ImagenInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleImagenInvalida(ImagenInvalidaException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Imagen inválida", List.of(ex.getMessage()));
    }

    /**
     * Estado no permitido (HU 04) -> 400 Bad Request.
     */
    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleEstadoInvalido(EstadoInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Estado inválido", List.of(ex.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> responder(HttpStatus estado, String error, List<String> mensajes) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", estado.value());
        cuerpo.put("error", error);
        cuerpo.put("messages", mensajes);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
