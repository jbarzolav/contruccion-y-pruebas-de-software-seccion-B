package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.EstadoPedidoResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.ExperienciaCompraService;
import com.ejemplo.backenduserapi.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PedidoController.class, excludeFilters =
        @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {com.ejemplo.backenduserapi.config.AutorizacionConfig.class,
                        com.ejemplo.backenduserapi.config.AutorizacionInterceptor.class}))
class PedidoEstadoControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PedidoService pedidoService;
    @MockitoBean private ExperienciaCompraService experiencia;
    @MockitoBean private com.ejemplo.backenduserapi.service.EstadoPedidoService estadoPedido;

    @Test
    void consultaEstadoDelPedido() throws Exception {
        Usuario cliente = new Usuario("cliente@test.com", "hash");
        cliente.setId(10L);
        when(experiencia.consultarEstado(eq(20L), eq(cliente))).thenReturn(
                new EstadoPedidoResponse(20L, "REGISTRADO", LocalDateTime.of(2026, 11, 2, 10, 0)));

        mockMvc.perform(get("/api/pedidos/20/estado").requestAttr("usuarioSesion", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPedido").value(20))
                .andExpect(jsonPath("$.estado").value("REGISTRADO"))
                .andExpect(jsonPath("$.fechaActualizacion").exists());
    }
}
