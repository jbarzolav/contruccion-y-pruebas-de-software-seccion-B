package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PerfilUsuarioResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ImagenInvalidaException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** HU 30 - Guarda la foto bajo la identidad de sesión y reemplaza el archivo previo. */
@Service
public class FotoPerfilService {
    private final UsuarioRepository usuarios;
    private final Path carpeta;
    private final long maxBytes;

    public FotoPerfilService(UsuarioRepository usuarios,
            @Value("${app.upload.dir:${java.io.tmpdir}/producto-imagenes}") Path carpetaImagenes,
            @Value("${app.profile-photo.max-size-bytes:2097152}") long maxBytes) {
        this.usuarios = usuarios;
        this.carpeta = carpetaImagenes.resolve("perfiles").toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Transactional
    public PerfilUsuarioResponse guardar(Usuario sesion, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) throw new ImagenInvalidaException("Selecciona una imagen.");
        if (archivo.getSize() > maxBytes) throw new ImagenInvalidaException("La imagen supera el tamaño máximo permitido.");
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        String extension = nombre.endsWith(".jpeg") || nombre.endsWith(".jpg") ? "jpg"
                : nombre.endsWith(".png") ? "png" : null;
        String mimeEsperado = "png".equals(extension) ? "image/png" : "image/jpeg";
        if (extension == null || (!tipo.isBlank() && !tipo.equals(mimeEsperado))) {
            throw new ImagenInvalidaException("Formato no permitido. Selecciona una imagen JPG o PNG.");
        }

        try {
            BufferedImage imagen;
            try (var entrada = archivo.getInputStream()) {
                imagen = ImageIO.read(entrada);
            }
            if (imagen == null) throw new ImagenInvalidaException("El archivo no contiene una imagen válida.");
            Files.createDirectories(carpeta);
            String archivoNuevo = sesion.getId() + "-" + UUID.randomUUID() + "." + extension;
            archivo.transferTo(carpeta.resolve(archivoNuevo));

            Usuario usuario = usuarios.findById(sesion.getId()).orElseThrow(
                    () -> new ImagenInvalidaException("La cuenta ya no existe."));
            String anterior = usuario.getFotoUrl();
            usuario.setFotoUrl("/imagenes/perfiles/" + archivoNuevo);
            PerfilUsuarioResponse resultado = PerfilUsuarioResponse.de(usuarios.save(usuario));
            eliminarFotoAnterior(anterior, usuario.getId());
            return resultado;
        } catch (IOException e) {
            throw new ImagenInvalidaException("No se pudo guardar la imagen.");
        }
    }

    private void eliminarFotoAnterior(String url, Long usuarioId) {
        if (url == null || !url.startsWith("/imagenes/perfiles/")) return;
        String nombre = url.substring("/imagenes/perfiles/".length());
        if (!nombre.matches(usuarioId + "-[0-9a-fA-F-]+\\.(jpg|png)")) return;
        Path anterior = carpeta.resolve(nombre).normalize();
        if (!anterior.startsWith(carpeta)) return;
        try { Files.deleteIfExists(anterior); } catch (IOException ignored) { /* Se conserva la referencia nueva. */ }
    }
}
