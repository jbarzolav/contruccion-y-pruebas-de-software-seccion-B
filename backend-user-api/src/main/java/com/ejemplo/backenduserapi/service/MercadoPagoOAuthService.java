package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.MercadoPagoOAuthIntento;
import com.ejemplo.backenduserapi.entity.MercadoPagoVendedor;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Vinculación OAuth por vendedor para Checkout Pro, exclusivamente con credenciales de prueba. */
@Service
public class MercadoPagoOAuthService {
    private static final String TOKEN_URL = "https://api.mercadopago.com/oauth/token";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    @PersistenceContext private EntityManager em;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public MercadoPagoOAuthService(ObjectMapper mapper) { this.mapper = mapper; }

    public record Vinculo(String url) {}
    public record Estado(boolean vinculado, Instant vence) {}

    private String obligatorio(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta configuración de Mercado Pago");
        return valor;
    }

    private String redirectUri() {
        String raw = obligatorio("MP_REDIRECT_URI");
        URI uri;
        try { uri = URI.create(raw); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "URL de retorno inválida");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !"/api/mercado-pago/oauth/callback".equals(uri.getPath()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "URL de retorno inválida");
        return raw;
    }

    private SecretKeySpec clave() {
        try {
            byte[] bytes = Base64.getDecoder().decode(obligatorio("MP_TOKEN_ENCRYPTION_KEY"));
            if (bytes.length != 32) throw new IllegalArgumentException("longitud");
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clave de cifrado inválida");
        }
    }

    private String cifrar(String texto, Long vendedorId, String tipo) {
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(128, iv));
            cipher.updateAAD((vendedorId + ":" + tipo).getBytes(StandardCharsets.UTF_8));
            return URL.encodeToString(iv) + "." + URL.encodeToString(cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("No se pudo proteger la credencial", ex); }
    }

    private String descifrar(String cifrado, Long vendedorId, String tipo) {
        try {
            String[] partes = cifrado.split("\\.", -1);
            if (partes.length != 2) throw new IllegalArgumentException("Formato inválido");
            byte[] iv = Base64.getUrlDecoder().decode(partes[0]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(128, iv));
            cipher.updateAAD((vendedorId + ":" + tipo).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(partes[1])), StandardCharsets.UTF_8);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("No se pudo leer la credencial protegida", ex); }
    }

    private static String aleatorio() {
        byte[] value = new byte[32]; RANDOM.nextBytes(value);
        return URL.encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 no disponible", ex); }
    }

    private static String parametro(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /** Solo para un vendedor autenticado; el ID jamás viene de la petición. */
    @Transactional
    public Vinculo iniciar(Usuario vendedor) {
        if (vendedor == null || vendedor.getRol() != Rol.VENDEDOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el vendedor puede vincular su cuenta");
        String id = obligatorio("MP_CLIENT_ID");
        obligatorio("MP_CLIENT_SECRET");
        String redirect = redirectUri();
        clave(); // Sin clave persistente no se permite iniciar una vinculación.
        String state = aleatorio();
        boolean pkce = Boolean.parseBoolean(System.getenv().getOrDefault("MP_OAUTH_PKCE", "false"));
        String verifier = pkce ? aleatorio() : null;
        em.persist(new MercadoPagoOAuthIntento(sha256(state), vendedor.getId(), Instant.now().plusSeconds(600),
                verifier == null ? null : cifrar(verifier, vendedor.getId(), "pkce")));
        String url = "https://auth.mercadopago.com/authorization?response_type=code&platform_id=mp"
                + "&client_id=" + parametro(id) + "&redirect_uri=" + parametro(redirect)
                + "&state=" + parametro(state) + "&scope=" + parametro("offline_access");
        if (verifier != null) url += "&code_challenge=" + parametro(URL.encodeToString(java.util.HexFormat.of().parseHex(sha256(verifier))))
                + "&code_challenge_method=S256";
        return new Vinculo(url);
    }

    private JsonNode intercambiar(Map<String, Object> cuerpo) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            // El cuerpo remoto puede incluir credenciales: nunca adjuntarlo a errores ni logs.
            if (response.statusCode() / 100 != 2)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mercado Pago rechazó la autorización");
            return mapper.readTree(response.body());
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar Mercado Pago");
        }
    }

    private void guardar(MercadoPagoVendedor cuenta, JsonNode credencial) {
        String access = credencial.path("access_token").asText("");
        String refresh = credencial.path("refresh_token").asText("");
        Long userId = credencial.path("user_id").canConvertToLong() ? credencial.path("user_id").longValue() : null;
        long segundos = credencial.path("expires_in").asLong(0);
        if (credencial.path("live_mode").asBoolean(true) || userId == null || userId < 1
                || access.isBlank() || refresh.isBlank() || segundos < 60
                || !"bearer".equalsIgnoreCase(credencial.path("token_type").asText())
                || !credencial.path("scope").asText("").contains("offline_access"))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mercado Pago no devolvió credenciales de prueba renovables");
        if (cuenta.getMercadoPagoUserId() != null && !cuenta.getMercadoPagoUserId().equals(userId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta de Mercado Pago vinculada es diferente");
        cuenta.actualizar(userId, cifrar(access, cuenta.getVendedorId(), "access"),
                cifrar(refresh, cuenta.getVendedorId(), "refresh"), Instant.now().plusSeconds(segundos));
    }

    /** Callback público: state aleatorio, con vencimiento y bloqueo de escritura contra reenvíos concurrentes. */
    @Transactional
    public void completar(String state, String code) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}") || code == null || code.isBlank() || code.length() > 512)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Autorización inválida");
        MercadoPagoOAuthIntento intento = em.find(MercadoPagoOAuthIntento.class, sha256(state), LockModeType.PESSIMISTIC_WRITE);
        if (intento == null || !intento.getVence().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Autorización inválida o vencida");
        Long vendedorId = intento.getVendedorId();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("client_id", obligatorio("MP_CLIENT_ID"));
        cuerpo.put("client_secret", obligatorio("MP_CLIENT_SECRET"));
        cuerpo.put("grant_type", "authorization_code");
        cuerpo.put("code", code);
        cuerpo.put("redirect_uri", redirectUri());
        cuerpo.put("test_token", true);
        if (intento.getVerifierCifrado() != null)
            cuerpo.put("code_verifier", descifrar(intento.getVerifierCifrado(), vendedorId, "pkce"));
        JsonNode credencial = intercambiar(cuerpo);
        MercadoPagoVendedor cuenta = em.find(MercadoPagoVendedor.class, vendedorId, LockModeType.PESSIMISTIC_WRITE);
        if (cuenta == null) { cuenta = new MercadoPagoVendedor(vendedorId); em.persist(cuenta); }
        guardar(cuenta, credencial);
        em.remove(intento);
    }

    @Transactional(readOnly = true)
    public Estado estado(Usuario vendedor) {
        MercadoPagoVendedor cuenta = em.find(MercadoPagoVendedor.class, vendedor.getId());
        return cuenta == null ? new Estado(false, null) : new Estado(true, cuenta.getVence());
    }

    /** Renueva ambos tokens en una sola transacción; nunca devuelve el secreto al cliente. */
    @Transactional
    public String tokenDePrueba(Long vendedorId) {
        MercadoPagoVendedor cuenta = em.find(MercadoPagoVendedor.class, vendedorId, LockModeType.PESSIMISTIC_WRITE);
        if (cuenta == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "El vendedor debe vincular Mercado Pago");
        if (!cuenta.getVence().isAfter(Instant.now().plus(Duration.ofDays(1)))) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("client_id", obligatorio("MP_CLIENT_ID"));
            cuerpo.put("client_secret", obligatorio("MP_CLIENT_SECRET"));
            cuerpo.put("grant_type", "refresh_token");
            cuerpo.put("refresh_token", descifrar(cuenta.getRefreshCifrado(), vendedorId, "refresh"));
            cuerpo.put("test_token", true);
            guardar(cuenta, intercambiar(cuerpo));
        }
        return descifrar(cuenta.getAccessCifrado(), vendedorId, "access");
    }
}
