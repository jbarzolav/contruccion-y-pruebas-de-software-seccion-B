package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.exception.BeneficioPremiumNoDisponibleException;
import com.ejemplo.backenduserapi.exception.ChatbotInvalidoException;
import com.ejemplo.backenduserapi.exception.CriterioOrdenInvalidoException;
import com.ejemplo.backenduserapi.exception.EstadoInvalidoException;
import com.ejemplo.backenduserapi.exception.ImagenInvalidaException;
import com.ejemplo.backenduserapi.exception.ItemCarritoInvalidoException;
import com.ejemplo.backenduserapi.exception.ItemCarritoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.PedidoInvalidoException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.PropietarioInvalidoException;
import com.ejemplo.backenduserapi.exception.StockInsuficienteException;
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
     * Vendedor sin plan Premium activo que intenta destacar (HU 19) -> 403.
     */
    @ExceptionHandler(BeneficioPremiumNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleBeneficioPremiumNoDisponible(
            BeneficioPremiumNoDisponibleException ex) {
        return responder(HttpStatus.FORBIDDEN, "Beneficio premium no disponible", List.of(ex.getMessage()));
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

    /**
     * Criterio de ordenamiento no permitido (HU 09) -> 400 Bad Request.
     */
    @ExceptionHandler(CriterioOrdenInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleCriterioOrdenInvalido(CriterioOrdenInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Criterio de ordenamiento inválido", List.of(ex.getMessage()));
    }

    /**
     * Regla del carrito violada: cantidad < 1 o superior al stock (HU 11 / HU 12) -> 400.
     */
    @ExceptionHandler(ItemCarritoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleItemCarritoInvalido(ItemCarritoInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Item de carrito inválido", List.of(ex.getMessage()));
    }

    /**
     * Ítem del carrito inexistente (HU 12) -> 404 Not Found.
     */
    @ExceptionHandler(ItemCarritoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleItemCarritoNoEncontrado(ItemCarritoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, "Item de carrito no encontrado", List.of(ex.getMessage()));
    }

    /**
     * Regla del pedido violada (HU 15): carrito vacío o clienteId inválido -> 400.
     */
    @ExceptionHandler(PedidoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handlePedidoInvalido(PedidoInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Pedido inválido", List.of(ex.getMessage()));
    }

    /**
     * El carrito pide más stock del disponible (HU 15) -> 400 (no se registra el pedido).
     */
    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<Map<String, Object>> handleStockInsuficiente(StockInsuficienteException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Stock insuficiente", List.of(ex.getMessage()));
    }

    /**
     * Consulta del chatbot vacía (HU 16) -> 400.
     */
    @ExceptionHandler(ChatbotInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleChatbotInvalido(ChatbotInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Consulta inválida", List.of(ex.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> responder(HttpStatus estado, String error, List<String> mensajes) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", estado.value());
        cuerpo.put("error", error);
        cuerpo.put("messages", mensajes);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
