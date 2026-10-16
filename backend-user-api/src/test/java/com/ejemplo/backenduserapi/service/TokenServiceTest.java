package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.SesionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** HU 23 - Identidad, vencimiento y rechazo de tokens no registrados. */
class TokenServiceTest {
    private final SesionRepository sesiones = new SesionRepository();
    private final TokenService servicio = new TokenService(sesiones);

    @Test
    void tokenValidoIdentificaAlUsuario() {
        String token = servicio.generarToken(23L);
        assertEquals(23L, servicio.validar(token));
        assertTrue(sesiones.buscar(token).orElseThrow().vencimiento().isAfter(Instant.now()));
    }

    @Test
    void tokensDeDistintasSesionesSonDiferentes() {
        assertNotEquals(servicio.generarToken(23L), servicio.generarToken(23L));
    }

    @Test
    void tokenDesconocidoDevuelve401() {
        var error = assertThrows(AutorizacionException.class, () -> servicio.validar("inventado"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getEstado());
    }

    @Test
    void tokenVencidoEsRechazadoYRetiradoDelAlmacenamiento() {
        sesiones.guardar("vencido", 23L, Instant.now().minusSeconds(1));
        var error = assertThrows(AutorizacionException.class, () -> servicio.validar("vencido"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getEstado());
        assertTrue(sesiones.buscar("vencido").isEmpty());
    }

    @Test
    void tokenGeneradoSinUsuarioNoAutorizaUnaSesion() {
        String token = servicio.generarToken();
        assertThrows(AutorizacionException.class, () -> servicio.validar(token));
    }
}
