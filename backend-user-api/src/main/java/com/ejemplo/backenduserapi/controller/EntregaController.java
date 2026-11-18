package com.ejemplo.backenduserapi.controller;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversaciones/entrega/{id}")
public class EntregaController {
    private final EntregaService entrega;
    public record Texto(String texto) {}
    @GetMapping("/mensajes") public java.util.List<com.ejemplo.backenduserapi.entity.MensajeEntrega> mensajes(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u) {return entrega.leer(id,u);}
    @PostMapping("/mensajes") public com.ejemplo.backenduserapi.entity.MensajeEntrega enviar(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u,@RequestBody Texto cuerpo) {return entrega.enviar(id,u,cuerpo.texto());}
    public EntregaController(EntregaService entrega) {this.entrega=entrega;}
    @GetMapping public EntregaService.Hilo abrir(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u) {return entrega.abrir(id,u);}
    @GetMapping("/pedido") public CheckoutService.Vista resumen(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u) {return entrega.resumen(id,u);}
}
