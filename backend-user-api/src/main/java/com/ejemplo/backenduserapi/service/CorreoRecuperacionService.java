package com.ejemplo.backenduserapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CorreoRecuperacionService {
    private final Path directorio;
    private final String url;

    public CorreoRecuperacionService(
            @Value("${app.recuperacion.directorio:${java.io.tmpdir}/pulgatec-correos}") String directorio,
            @Value("${app.recuperacion.url:http://localhost:3000/#/restablecer}") String url) {
        this.directorio = Path.of(directorio);
        this.url = url;
    }

    // Bandeja local de pruebas; no envía correos reales ni expone el token por HTTP.
    public void enviar(String correo, String token) {
        try {
            Files.createDirectories(directorio);
            Path mensaje = Files.createTempFile(directorio, "recuperacion-", ".txt");
            Files.writeString(mensaje, "Para: " + correo + "\nRecuperar acceso: "
                    + url + "?token=" + token + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el correo local de recuperación", ex);
        }
    }
}
