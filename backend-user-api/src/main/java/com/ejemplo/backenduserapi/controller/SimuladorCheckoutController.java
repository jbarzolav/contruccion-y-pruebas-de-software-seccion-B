package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Habilitar exclusivamente en un entorno de demostración. */
@ConditionalOnProperty(name="app.checkout.simulador",havingValue="true")
@RestController
@RequestMapping("/api/pedidos/checkout/{id}/simulador")
public class SimuladorCheckoutController {
    private final SimuladorPagoService simulador;
    @PostMapping("/reembolso/solicitar") public CheckoutService.Vista solicitar(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u,@RequestBody SimuladorPagoService.Aviso aviso) {
        return simulador.devolver(id,u,aviso,true);
    }
    @PostMapping("/reembolso/resultado") public CheckoutService.Vista resultado(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u,@RequestBody SimuladorPagoService.Aviso aviso) {
        return simulador.devolver(id,u,aviso,false);
    }
    public SimuladorCheckoutController(SimuladorPagoService simulador) { this.simulador=simulador; }
    @PostMapping public CheckoutService.Vista resolver(@PathVariable Long id,@RequestAttribute("usuarioSesion") Usuario u,@RequestBody SimuladorPagoService.Aviso aviso) {
        return simulador.resolver(id,u,aviso);
    }
}
