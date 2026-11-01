package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ResenaResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.ExperienciaCompraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ResenaController.class, excludeFilters =
        @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {com.ejemplo.backenduserapi.config.AutorizacionConfig.class,
                        com.ejemplo.backenduserapi.config.AutorizacionInterceptor.class}))
class ResenaControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExperienciaCompraService experiencia;

    @Test
    void edicionValidaDevuelve200() throws Exception {
        Usuario usuario = new Usuario("cliente@test.com", "hash");
        usuario.setId(10L);
        when(experiencia.editarResena(eq(7L), any(), eq(usuario))).thenReturn(
                new ResenaResponse(7L, 20L, "PRODUCTO", 30L, null,
                        "texto actualizado", LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(put("/api/resenas/7")
                        .requestAttr("usuarioSesion", usuario)
                        .contentType("application/json")
                        .content("{\"texto\":\"texto actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.texto").value("texto actualizado"));
    }

    @Test
    void textoVacioDevuelve400SinInvocarServicio() throws Exception {
        mockMvc.perform(put("/api/resenas/7")
                        .contentType("application/json")
                        .content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verify(experiencia, never()).editarResena(any(), any(), any());
    }
}
