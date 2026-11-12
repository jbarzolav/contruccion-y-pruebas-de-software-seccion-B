package com.ejemplo.backenduserapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
public class CorreoRecuperacionService {
    private static final Logger log = LoggerFactory.getLogger(CorreoRecuperacionService.class);
    private static final String ENDPOINT_BREVO = "https://api.brevo.com/v3/smtp/email";

    private final Path directorio;
    private final String url;
    private final String apiKey;
    private final String remitente;
    private final String nombreRemitente;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CorreoRecuperacionService(
            @Value("${app.recuperacion.directorio:${java.io.tmpdir}/pulgatec-correos}") String directorio,
            @Value("${app.recuperacion.url:http://localhost:8080/#/restablecer}") String url,
            @Value("${app.recuperacion.api-key:}") String apiKey,
            @Value("${app.recuperacion.remitente:}") String remitente,
            @Value("${app.recuperacion.remitente-nombre:PulgaTec}") String nombreRemitente) {
        this.directorio = Path.of(directorio);
        this.url = url;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
    }

    // Envía el código de recuperación por Brevo; si no hay clave configurada o el
    // envío falla, deja el mensaje en la bandeja local de pruebas. Ni la clave ni
    // el código quedan registrados en el log.
    public void enviar(String correo, String token) {
        String texto = "Recupera tu contraseña de PulgaTec.\n\n"
                + "Tu código de verificación es: " + token + "\n\n"
                + "Escribe ese código en " + url + " para poner tu nueva contraseña.\n"
                + "Si prefieres el enlace, abre el botón y ya komtaste con el código listo.\n\n"
                + "El código vence en 15 minutos. Si no solicitaste este cambio, ignora este mensaje.\n";
        String html = "<p>Recupera tu contraseña de <strong>PulgaTec</strong>.</p>"
                + "<p>Tu código de verificación es: <strong>" + token + "</strong></p>"
                + "<p>Escribe ese código en <a href=\"" + url + "\">" + url + "</a> "
                + "para poner tu nueva contraseña.</p>"
                + "<p>El código vence en 15 minutos. Si no solicitaste este cambio, ignora este mensaje.</p>"
                + "<p><a href=\"" + url + "?token=" + token + "\">Poner mi nueva contraseña</a></p>";

        if (apiKey.isEmpty() || remitente.isEmpty()) {
            guardarLocal(correo, token, texto);
            return;
        }
        if (!enviarPorBrevo(correo, texto, html)) {
            guardarLocal(correo, token, texto);
        }
    }

    private boolean enviarPorBrevo(String correo, String texto, String html) {
        try {
            ObjectNode cuerpo = mapper.createObjectNode();
            ObjectNode emisor = cuerpo.putObject("sender");
            emisor.put("name", nombreRemitente);
            emisor.put("email", remitente);
            cuerpo.putArray("to").addObject().put("email", correo);
            cuerpo.put("subject", "Código de recuperación de contraseña - PulgaTec");
            cuerpo.put("textContent", texto);
            cuerpo.put("htmlContent", html);

            HttpRequest peticion = HttpRequest.newBuilder(URI.create(ENDPOINT_BREVO))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> respuesta =
                    http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 200 && respuesta.statusCode() < 300) {
                log.info("Brevo aceptó el correo de recuperación (HTTP {})", respuesta.statusCode());
                return true;
            }
            log.warn("Brevo rechazó el correo de recuperación (HTTP {}): {}",
                    respuesta.statusCode(), recortar(respuesta.body()));
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Envío de recuperación interrumpido; se usa la bandeja local");
            return false;
        } catch (Exception ex) {
            log.warn("No se pudo enviar la recuperación por Brevo ({}); se usa la bandeja local",
                    ex.getMessage());
            return false;
        }
    }

    private void guardarLocal(String correo, String token, String texto) {
        try {
            Files.createDirectories(directorio);
            Path mensaje = Files.createTempFile(directorio, "recuperacion-", ".txt");
            Files.writeString(mensaje, "Para: " + correo + "\nRecuperar acceso: "
                    + url + "?token=" + token + "\nAbrir en Android: pulgatec://restablecer?token="
                    + token + "\n\n" + texto, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el correo local de recuperación", ex);
        }
    }

    private String recortar(String cuerpo) {
        if (cuerpo == null) return "";
        return cuerpo.length() > 300 ? cuerpo.substring(0, 300) : cuerpo;
    }
}
