package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** HU 23 - Sesiones reales para conservar los casos de negocio de HU01-HU19. */
abstract class SesionControllerTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenService tokens;
    private static final String HASH_PRUEBA = new BCryptPasswordEncoder().encode("Prueba123*");

    protected RequestPostProcessor comoVendedor(long id) { return conSesion(id, Rol.VENDEDOR); }
    protected RequestPostProcessor comoCliente(long id) { return conSesion(id, Rol.CLIENTE); }

    private RequestPostProcessor conSesion(long id, Rol rol) {
        return request -> {
            // Las pruebas antiguas usan propietarios con IDs explícitos (1, 2, 10, 999).
            // Solo se preparan cuentas en H2; no se sustituye la autorización del backend.
            if (jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, id) == 0) {
                jdbc.update("insert into usuarios (id, correo, password_hash, rol) values (?, ?, ?, ?)",
                        id, "prueba-hu23-" + id + "@pulgatec.com", HASH_PRUEBA, rol.name());
                Long siguiente = jdbc.queryForObject("select max(id) + 1 from usuarios", Long.class);
                jdbc.execute("alter table usuarios alter column id restart with " + siguiente);
            }
            jdbc.update("update usuarios set rol = ? where id = ?", rol.name(), id);
            request.addHeader("Authorization", "Bearer " + tokens.generarToken(id));
            return request;
        };
    }
}
