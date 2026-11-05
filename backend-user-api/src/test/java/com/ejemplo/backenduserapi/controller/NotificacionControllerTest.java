package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.ejemplo.backenduserapi.service.NotificacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacionControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private NotificacionRepository notificaciones;
    @Autowired private PasswordEncoder encoder;

    private Usuario cliente;
    private Usuario vendedor;

    @BeforeEach
    void preparar() {
        cliente = new Usuario("avisos-cliente@prueba.com", encoder.encode("Prueba123*"));
        cliente.setRol(Rol.CLIENTE);
        cliente = usuarios.saveAndFlush(cliente);
        Usuario otro = new Usuario("avisos-otro@prueba.com", encoder.encode("Prueba123*"));
        otro.setRol(Rol.CLIENTE);
        otro = usuarios.saveAndFlush(otro);
        vendedor = new Usuario("avisos-vendedor@prueba.com", encoder.encode("Prueba123*"));
        vendedor.setRol(Rol.VENDEDOR);
        vendedor = usuarios.saveAndFlush(vendedor);
        notificaciones.saveAndFlush(new Notificacion(NotificacionService.CAMBIO_ESTADO_PEDIDO,
                "El pedido #70 cambió a EN_PROCESO.", LocalDateTime.now(), cliente.getId(), 70L, "estado:70"));
        notificaciones.saveAndFlush(new Notificacion(NotificacionService.CAMBIO_ESTADO_PEDIDO,
                "El pedido #71 cambió a EN_PROCESO.", LocalDateTime.now(), otro.getId(), 71L, "estado:71"));
        notificaciones.saveAndFlush(new Notificacion(NotificacionService.NUEVA_COMPRA,
                "Recibiste una nueva compra: pedido #72.", LocalDateTime.now(), vendedor.getId(), 72L, "compra:72:" + vendedor.getId()));
    }

    @Test
    void devuelveSoloLosAvisosDelUsuarioAutenticado() throws Exception {
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].idPedido").value(70))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].enlace").value("/pedidos/70"));
        assertEquals(3, notificaciones.count());
    }

    @Test
    void devuelveAvisoDeCompraSoloAlVendedorParticipante() throws Exception {
        mvc.perform(get("/api/notificaciones").header("Authorization", "Bearer " + token(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value(NotificacionService.NUEVA_COMPRA))
                .andExpect(jsonPath("$[0].idPedido").value(72))
                .andExpect(jsonPath("$[0].enlace").value("/pedidos-recibidos#pedido-72"));
    }

    @Test
    void requiereSesionParaListarAvisos() throws Exception {
        mvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
    }

    private String token() throws Exception {
        return token(cliente);
    }

    private String token(Usuario usuario) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "correo", usuario.getCorreo(), "password", "Prueba123*"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(respuesta).get("token").asText();
    }
}
