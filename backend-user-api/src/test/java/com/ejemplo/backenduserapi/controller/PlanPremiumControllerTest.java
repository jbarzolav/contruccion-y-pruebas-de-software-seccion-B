package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.service.PlanPremiumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ejemplo.backenduserapi.dto.PlanPremiumResponse;
import java.util.List;

@WebMvcTest(PlanPremiumController.class)
class PlanPremiumControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanPremiumService planPremiumService;

    @Test
    void debeMostrarInformacionYBeneficiosPremium() throws Exception {
        when(planPremiumService.consultarBeneficios()).thenReturn(
                new PlanPremiumResponse(
                        "Plan Premium",
                        "Plan para vendedores",
                        List.of(
                                "Publicaciones destacadas",
                                "Mayor visibilidad de los productos",
                                "Herramientas adicionales para vendedores"
                        )
                )
        );

        mockMvc.perform(get("/api/planes/premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Plan Premium"))
                .andExpect(jsonPath("$.descripcion").value("Plan para vendedores"))
                .andExpect(jsonPath("$.beneficios.length()").value(3))
                .andExpect(jsonPath("$.beneficios[0]").value("Publicaciones destacadas"));
    }
}
