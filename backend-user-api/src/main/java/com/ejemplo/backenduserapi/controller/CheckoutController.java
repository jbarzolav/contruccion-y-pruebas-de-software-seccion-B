package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.CheckoutService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos/checkout")
public class CheckoutController {
    private final CheckoutService checkout;
    public CheckoutController(CheckoutService checkout) { this.checkout=checkout; }
    public record Seleccion(Long vendedorId,List<Long> itemIds) {}
    @PostMapping public CheckoutService.Vista iniciar(@RequestAttribute("usuarioSesion") Usuario u,@RequestBody Seleccion s) {
        return checkout.iniciar(u,s.vendedorId(),s.itemIds());
    }
    @GetMapping("/{id}") public CheckoutService.Vista ver(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u) {return checkout.consultar(id,u);}
    @PostMapping("/{id}/reintentar") public CheckoutService.Vista reintentar(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u) {return checkout.reintentar(id,u);}
}
