package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.CalificacionRequest;
import com.ejemplo.backenduserapi.dto.CalificacionResponse;
import com.ejemplo.backenduserapi.dto.DetallePedidoResponse;
import com.ejemplo.backenduserapi.dto.PedidoExperienciaResponse;
import com.ejemplo.backenduserapi.dto.ResenaRequest;
import com.ejemplo.backenduserapi.dto.ResenaResponse;
import com.ejemplo.backenduserapi.entity.Calificacion;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Resena;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.CalificacionRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ResenaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** HU 41/42 - Reglas compartidas de propiedad, elegibilidad y destinos reales. */
@Service
public class ExperienciaCompraService {

    public static final int VALOR_MINIMO = 1;
    public static final int VALOR_MAXIMO = 5;
    public static final int MAXIMO_RESENA = 1000;
    public static final String PRODUCTO = "PRODUCTO";
    public static final String VENDEDOR = "VENDEDOR";

    private final PedidoRepository pedidoRepository;
    private final CalificacionRepository calificacionRepository;
    private final ResenaRepository resenaRepository;

    public ExperienciaCompraService(PedidoRepository pedidoRepository,
                                    CalificacionRepository calificacionRepository,
                                    ResenaRepository resenaRepository) {
        this.pedidoRepository = pedidoRepository;
        this.calificacionRepository = calificacionRepository;
        this.resenaRepository = resenaRepository;
    }

    @Transactional(readOnly = true)
    public List<PedidoExperienciaResponse> listarPedidos(Usuario usuario) {
        exigirCliente(usuario);
        return pedidoRepository.findByClienteIdOrderByFechaDesc(usuario.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoExperienciaResponse obtenerPedido(Long idPedido, Usuario usuario) {
        return toResponse(pedidoPropio(idPedido, usuario));
    }

    @Transactional
    public PedidoExperienciaResponse calificar(Long idPedido, CalificacionRequest request, Usuario usuario) {
        Pedido pedido = pedidoPropio(idPedido, usuario);
        Integer valor = request.getValor();
        if (valor == null || valor < VALOR_MINIMO || valor > VALOR_MAXIMO) {
            throw error(HttpStatus.BAD_REQUEST, "La calificación debe estar entre 1 y 5.");
        }
        if (calificacionRepository.existsByPedidoId(idPedido)) {
            throw error(HttpStatus.CONFLICT, "Esta compra ya tiene una calificación.");
        }

        Long vendedorId = vendedorUnico(pedido);
        Calificacion calificacion = new Calificacion(
                pedido, usuario.getId(), vendedorId, valor, LocalDateTime.now());
        calificacionRepository.save(calificacion);
        return toResponse(pedido);
    }

    @Transactional
    public PedidoExperienciaResponse resenar(Long idPedido, ResenaRequest request, Usuario usuario) {
        Pedido pedido = pedidoPropio(idPedido, usuario);
        String destino = request.getDestino() == null
                ? "" : request.getDestino().trim().toUpperCase(Locale.ROOT);
        if (!PRODUCTO.equals(destino) && !VENDEDOR.equals(destino)) {
            throw error(HttpStatus.BAD_REQUEST, "El destino debe ser PRODUCTO o VENDEDOR.");
        }

        String texto = request.getTexto() == null ? "" : request.getTexto().trim();
        if (texto.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "La reseña no puede estar vacía.");
        }
        if (texto.length() > MAXIMO_RESENA) {
            throw error(HttpStatus.BAD_REQUEST, "La reseña no puede superar 1000 caracteres.");
        }
        if (resenaRepository.existsByPedidoIdAndDestinoTipo(idPedido, destino)) {
            throw error(HttpStatus.CONFLICT, "Esta compra ya tiene una reseña para ese destino.");
        }

        Long productoId = PRODUCTO.equals(destino) ? productoUnico(pedido) : null;
        Long vendedorId = VENDEDOR.equals(destino) ? vendedorUnico(pedido) : null;
        Resena resena = new Resena(
                pedido, usuario.getId(), destino, productoId, vendedorId, texto, LocalDateTime.now());
        resenaRepository.save(resena);
        return toResponse(pedido);
    }

    private Pedido pedidoPropio(Long idPedido, Usuario usuario) {
        exigirCliente(usuario);
        if (idPedido == null || idPedido < 1) {
            throw error(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
        }
        Pedido pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
        if (!usuario.getId().equals(pedido.getClienteId())) {
            throw error(HttpStatus.FORBIDDEN, "No puedes operar con una compra de otro cliente.");
        }
        // El dominio actual no tiene estados de pedido: un pedido registrado es una compra elegible.
        return pedido;
    }

    private void exigirCliente(Usuario usuario) {
        if (usuario == null || usuario.getRol() != Rol.CLIENTE) {
            throw error(HttpStatus.FORBIDDEN, "Solo un cliente puede gestionar la experiencia de compra.");
        }
    }

    private Long vendedorUnico(Pedido pedido) {
        Set<Long> vendedores = new LinkedHashSet<>();
        for (DetallePedido detalle : pedido.getDetalles()) {
            if (detalle.getProducto().getVendedorId() != null) {
                vendedores.add(detalle.getProducto().getVendedorId());
            }
        }
        if (vendedores.isEmpty()) {
            throw error(HttpStatus.NOT_FOUND, "La compra no tiene un vendedor válido.");
        }
        if (vendedores.size() > 1) {
            throw error(HttpStatus.BAD_REQUEST,
                    "La compra contiene varios vendedores y no admite una reseña global.");
        }
        return vendedores.iterator().next();
    }

    private Long productoUnico(Pedido pedido) {
        Set<Long> productos = new LinkedHashSet<>();
        for (DetallePedido detalle : pedido.getDetalles()) {
            if (detalle.getProducto().getId() != null) productos.add(detalle.getProducto().getId());
        }
        if (productos.size() != 1) {
            throw error(HttpStatus.BAD_REQUEST,
                    "La compra debe contener un único producto para reseñarlo.");
        }
        return productos.iterator().next();
    }

    private PedidoExperienciaResponse toResponse(Pedido pedido) {
        List<DetallePedidoResponse> detalles = new ArrayList<>();
        for (DetallePedido detalle : pedido.getDetalles()) {
            detalles.add(new DetallePedidoResponse(
                    detalle.getProducto().getId(), detalle.getProducto().getNombre(),
                    detalle.getCantidad(), detalle.getPrecioUnitario(), detalle.getSubtotal()));
        }
        CalificacionResponse calificacion = calificacionRepository.findByPedidoId(pedido.getId())
                .map(item -> new CalificacionResponse(item.getId(), pedido.getId(), item.getValor(), item.getFechaCreacion()))
                .orElse(null);
        List<ResenaResponse> resenas = resenaRepository.findByPedidoIdOrderByFechaCreacionAsc(pedido.getId())
                .stream().map(item -> new ResenaResponse(item.getId(), pedido.getId(), item.getDestinoTipo(),
                        item.getProductoId(), item.getVendedorId(), item.getTexto(), item.getFechaCreacion())).toList();
        return new PedidoExperienciaResponse(pedido.getId(), pedido.getClienteId(), pedido.getFecha(),
                pedido.getTotal(), detalles, calificacion, resenas);
    }

    private ExperienciaCompraException error(HttpStatus estado, String mensaje) {
        return new ExperienciaCompraException(estado, mensaje);
    }
}
