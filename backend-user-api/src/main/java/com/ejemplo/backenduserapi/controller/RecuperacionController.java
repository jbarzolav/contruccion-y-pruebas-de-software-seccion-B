package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.CodigoRecuperacionRequest;
import com.ejemplo.backenduserapi.dto.RecuperacionRequest;
import com.ejemplo.backenduserapi.dto.RestablecerRequest;
import com.ejemplo.backenduserapi.exception.RecuperacionInvalidaException;
import com.ejemplo.backenduserapi.service.LimiteRecuperacion;
import com.ejemplo.backenduserapi.service.RecuperacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class RecuperacionController {
    private final RecuperacionService servicio;
    private final LimiteRecuperacion limite;

    public RecuperacionController(RecuperacionService servicio, LimiteRecuperacion limite) {
        this.servicio = servicio;
        this.limite = limite;
    }

    @PostMapping("/recuperacion")
    public Map<String, String> solicitar(@Valid @RequestBody RecuperacionRequest solicitud) {
        servicio.solicitar(solicitud.getCorreo());
        return Map.of("mensaje", RecuperacionService.MENSAJE_SOLICITUD);
    }

    /**
     * HU 22 - Paso 1: se comprueba el código recibido por correo. Solo si el
     * código es válido se habilita el formulario de nueva contraseña.
     */
    @PostMapping("/recuperacion/verificar")
    public ResponseEntity<Map<String, Object>> verificar(@Valid @RequestBody CodigoRecuperacionRequest solicitud,
                                                         HttpServletRequest request) {
        String codigo = solicitud.getCodigo().trim();
        if (!limite.permitirPorEquipo(equipo(request)) || !limite.permitirPorCodigo(codigo)) {
            return demasiadosIntentos();
        }
        if (!servicio.codigoValido(codigo)) {
            throw new RecuperacionInvalidaException("El código no es válido o ya venció.");
        }
        limite.limpiarCodigo(codigo);
        return ResponseEntity.ok(Map.of("valido", true, "mensaje", "Código válido."));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<Map<String, Object>> restablecer(@Valid @RequestBody RestablecerRequest solicitud,
                                                            HttpServletRequest request) {
        String codigo = solicitud.getToken() == null ? "" : solicitud.getToken().trim();
        if (!limite.permitirPorEquipo(equipo(request)) || !limite.permitirPorCodigo(codigo)) {
            return demasiadosIntentos();
        }
        servicio.restablecer(solicitud);
        limite.limpiarCodigo(codigo);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente."));
    }

    private ResponseEntity<Map<String, Object>> demasiadosIntentos() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Demasiados intentos",
                        "messages", List.of("Demasiados intentos. Espera un minuto y vuelve a intentarlo.")));
    }

    private String equipo(HttpServletRequest request) {
        String direccion = request.getRemoteAddr();
        return direccion == null || direccion.isBlank() ? "desconocido" : direccion;
    }
}
