package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ConversacionResponse;
import com.ejemplo.backenduserapi.dto.ConversacionResponse.MensajeResponse;
import com.ejemplo.backenduserapi.entity.*;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class ConversacionService {
    private final ConversacionRepository conversaciones;
    private final MensajeRepository mensajes;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final int longitudMaxima;

    public ConversacionService(ConversacionRepository conversaciones, MensajeRepository mensajes,
            ProductoRepository productos, UsuarioRepository usuarios,
            @Value("${app.conversaciones.longitud-maxima:2000}") int longitudMaxima) {
        if (longitudMaxima < 1 || longitudMaxima > 10000) throw new IllegalArgumentException("Longitud máxima: 1 a 10000.");
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.productos = productos;
        this.usuarios = usuarios;
        this.longitudMaxima = longitudMaxima;
    }
    public int getLongitudMaxima() { return longitudMaxima; }

    public ConversacionResponse consultar(Usuario cliente, Long productoId, String texto) {
        exigirRol(cliente, Rol.CLIENTE);
        String contenido = validarMensaje(texto);
        if (productoId == null || productoId <= 0) throw error(HttpStatus.BAD_REQUEST, "Producto obligatorio.");
        Producto producto = productos.buscarParaConsulta(productoId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Producto no encontrado."));
        if (!"DISPONIBLE".equals(producto.getEstado()) && !"AGOTADO".equals(producto.getEstado()))
            throw error(HttpStatus.CONFLICT, "Este producto no admite nuevas consultas.");
        Usuario vendedor = producto.getVendedorId() == null ? null : usuarios.findById(producto.getVendedorId()).orElse(null);
        if (vendedor == null || vendedor.getRol() != Rol.VENDEDOR || vendedor.getId().equals(cliente.getId()))
            throw error(HttpStatus.CONFLICT, "El producto no tiene un vendedor válido.");
        Conversacion conversacion = conversaciones.findByProducto_IdAndCliente_IdAndVendedor_Id(
                productoId, cliente.getId(), vendedor.getId())
                .orElseGet(() -> conversaciones.save(new Conversacion(producto, cliente, vendedor)));
        mensajes.save(new Mensaje(conversacion, cliente, contenido));
        return respuesta(conversacion);
    }

    public MensajeResponse responder(Usuario vendedor, Long id, String texto) {
        exigirRol(vendedor, Rol.VENDEDOR);
        Conversacion conversacion = buscar(id);
        if (!conversacion.getVendedor().getId().equals(vendedor.getId()))
            throw error(HttpStatus.FORBIDDEN, "No participas como vendedor en esta conversación.");
        return MensajeResponse.desde(mensajes.save(new Mensaje(conversacion, vendedor, validarMensaje(texto))));
    }

    @Transactional(readOnly = true)
    public List<ConversacionResponse> listar(Usuario usuario) {
        exigirParticipanteRol(usuario);
        return conversaciones.findByCliente_IdOrVendedor_IdOrderByIdDesc(usuario.getId(), usuario.getId())
                .stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public ConversacionResponse obtener(Usuario usuario, Long id) {
        exigirParticipanteRol(usuario);
        Conversacion conversacion = buscar(id);
        if (!conversacion.getCliente().getId().equals(usuario.getId()) && !conversacion.getVendedor().getId().equals(usuario.getId()))
            throw error(HttpStatus.FORBIDDEN, "No participas en esta conversación.");
        return respuesta(conversacion);
    }

    private Conversacion buscar(Long id) {
        return conversaciones.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conversación no encontrada."));
    }
    private ConversacionResponse respuesta(Conversacion c) {
        return new ConversacionResponse(c.getId(), c.getProducto().getId(), c.getProducto().getNombre(),
                c.getFechaCreacion(), mensajes.findByConversacion_IdOrderByIdAsc(c.getId()).stream().map(MensajeResponse::desde).toList());
    }
    private String validarMensaje(String texto) {
        String contenido = texto == null ? "" : texto.strip();
        if (contenido.isBlank()) throw error(HttpStatus.BAD_REQUEST, "El mensaje es obligatorio.");
        if (contenido.length() > longitudMaxima) throw error(HttpStatus.BAD_REQUEST, "El mensaje admite hasta " + longitudMaxima + " caracteres.");
        return contenido;
    }
    private void exigirParticipanteRol(Usuario usuario) {
        if (usuario.getRol() != Rol.CLIENTE && usuario.getRol() != Rol.VENDEDOR)
            throw error(HttpStatus.FORBIDDEN, "Solo clientes y vendedores pueden consultar conversaciones.");
    }
    private void exigirRol(Usuario usuario, Rol rol) {
        if (usuario.getRol() != rol) throw error(HttpStatus.FORBIDDEN, "Esta operación requiere rol " + rol + ".");
    }
    private AutorizacionException error(HttpStatus estado, String mensaje) { return new AutorizacionException(estado, mensaje); }
}
