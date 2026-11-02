package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PedidoVendedorResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.PedidosVendedorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PedidosVendedorController.class, excludeFilters =
        @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {com.ejemplo.backenduserapi.config.AutorizacionConfig.class,
                        com.ejemplo.backenduserapi.config.AutorizacionInterceptor.class}))
class PedidosVendedorControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PedidosVendedorService pedidos;

    @Test
    void listaPedidosDelVendedorDeLaSesion() throws Exception {
        Usuario vendedor = new Usuario("vendedor@test.com", "hash");
        vendedor.setId(40L);
        when(pedidos.listarPedidos(eq(vendedor))).thenReturn(List.of(
                new PedidoVendedorResponse(20L, LocalDateTime.of(2026, 11, 2, 10, 0), "REGISTRADO",
                        LocalDateTime.of(2026, 11, 2, 10, 0), new BigDecimal("75.00"), List.of())));

        mockMvc.perform(get("/api/vendedores/me/pedidos").requestAttr("usuarioSesion", vendedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idPedido").value(20))
                .andExpect(jsonPath("$[0].estado").value("REGISTRADO"))
                .andExpect(jsonPath("$[0].total").value(75.0));
    }
}
