package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ConversacionResponse;
import com.ejemplo.backenduserapi.dto.ConversacionResponse.MensajeResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.ConversacionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/conversaciones")
public class ConversacionController {
    private final ConversacionService servicio;
    public ConversacionController(ConversacionService servicio) { this.servicio = servicio; }
    public record ConsultaRequest(Long productoId, String mensaje) { }
    public record RespuestaRequest(String mensaje) { }

    @GetMapping("/configuracion")
    public Map<String, Integer> configuracion() { return Map.of("longitudMaxima", servicio.getLongitudMaxima()); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversacionResponse consultar(@RequestAttribute("usuarioSesion") Usuario usuario, @RequestBody ConsultaRequest request) {
        return servicio.consultar(usuario, request.productoId(), request.mensaje());
    }
    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeResponse responder(@RequestAttribute("usuarioSesion") Usuario usuario, @PathVariable Long id, @RequestBody RespuestaRequest request) {
        return servicio.responder(usuario, id, request.mensaje());
    }
    @GetMapping
    public List<ConversacionResponse> listar(@RequestAttribute("usuarioSesion") Usuario usuario) { return servicio.listar(usuario); }
    @GetMapping("/{id}")
    public ConversacionResponse obtener(@RequestAttribute("usuarioSesion") Usuario usuario, @PathVariable Long id) { return servicio.obtener(usuario, id); }
}
