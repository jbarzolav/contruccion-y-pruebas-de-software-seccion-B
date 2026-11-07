package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.AvisoStockBajoResponse;
import com.ejemplo.backenduserapi.dto.NotificacionResponse;
import com.ejemplo.backenduserapi.dto.UmbralStockResponse;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.UmbralStock;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.PropietarioInvalidoException;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.ejemplo.backenduserapi.repository.UmbralStockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HU 58 - Umbrales de stock y avisos de stock bajo.
 *
 * Reglas de negocio:
 *  - El umbral es un entero mayor o igual a cero y solo lo modifica el
 *    vendedor propietario de la publicación.
 *  - La condicion de stock bajo es `stockActual <= umbral`.
 *  - El aviso se emite SOLO en la cruzada descendente (de stock por encima
 *    del umbral a stock en o por debajo). Mientras el stock siga en el mismo
 *    lado no se repite el aviso.
 *  - Una reposicion (de stock bajo a stock por encima del umbral) rearma la
 *    alerta: el siguiente cruce vuelve a avisar.
 *  - Al configurar el umbral con el stock ya por debajo NO se emite aviso
 *    (decision documentada de la HU 58.2.D): el panel muestra el estado
 *    "stock bajo" y la alerta queda armada como ya notificada hasta que
 *    haya una reposicion.
 *  - La deteccion vive aqui y se invoca desde los dos unicos puntos que
 *    reducen unidades: la venta (PedidoService) y la edicion manual de stock
 *    (ProductoService).
 */
@Service
public class AlertaStockService {

    private final UmbralStockRepository umbrales;
    private final ProductoRepository productos;
    private final NotificacionService notificaciones;

    public AlertaStockService(UmbralStockRepository umbrales, ProductoRepository productos,
                              NotificacionService notificaciones) {
        this.umbrales = umbrales;
        this.productos = productos;
        this.notificaciones = notificaciones;
    }

    // ------------------------------------------------------------------
    // Configuracion y consulta
    // ------------------------------------------------------------------

    /**
     * Crea o actualiza el umbral de una publicacion propia.
     * 404 si el producto no existe, 403 si es de otro vendedor, 400 si el
     * valor no es valido.
     */
    @Transactional
    public UmbralStockResponse configurar(Usuario vendedor, Long idProducto, Integer umbral) {
        exigirVendedor(vendedor);
        if (umbral == null || umbral < 0) {
            throw error(HttpStatus.BAD_REQUEST,
                    "El umbral debe ser un numero entero mayor o igual a cero.");
        }

        Producto producto = productoPropio(vendedor, idProducto);
        LocalDateTime ahora = LocalDateTime.now();

        UmbralStock registro = umbrales.buscarParaActualizar(idProducto)
                .orElseGet(() -> new UmbralStock(producto, umbral, ahora));
        registro.setProducto(producto);
        registro.setUmbral(umbral);
        registro.setFechaActualizacion(ahora);
        // Sin aviso inmediato: si el stock ya esta por debajo, la alerta queda
        // armada como ya notificada y solo se rearma con una reposicion.
        registro.setAlertaDisparada(stock(producto) <= umbral);

        UmbralStock guardado = umbrales.save(registro);
        return respuesta(guardado, producto);
    }

    /**
     * Consulta el umbral de una publicacion propia.
     * Si todavia no hay umbral configurado, la respuesta llega con umbral
     * null (estado "sin umbral configurado") y no es un error.
     */
    @Transactional(readOnly = true)
    public UmbralStockResponse consultar(Usuario vendedor, Long idProducto) {
        exigirVendedor(vendedor);
        Producto producto = productoPropio(vendedor, idProducto);
        UmbralStock registro = umbrales.findByProductoId(idProducto).orElse(null);
        return respuesta(registro, producto);
    }

    /**
     * Panel del vendedor: todas sus publicaciones con su umbral vigente.
     * Las publicaciones sin umbral llegan con umbral null.
     */
    @Transactional(readOnly = true)
    public List<UmbralStockResponse> listar(Usuario vendedor) {
        exigirVendedor(vendedor);
        List<Producto> publicaciones = productos.findByVendedorId(vendedor.getId());
        Map<Long, UmbralStock> porProducto = new LinkedHashMap<>();
        for (UmbralStock registro : umbrales.findByVendedorId(vendedor.getId())) {
            porProducto.put(registro.getProducto().getId(), registro);
        }

        List<UmbralStockResponse> respuestas = new ArrayList<>(publicaciones.size());
        for (Producto producto : publicaciones) {
            respuestas.add(respuesta(porProducto.get(producto.getId()), producto));
        }
        return respuestas;
    }

    /**
     * Avisos de stock bajo del vendedor. Se leen del sistema de notificaciones
     * existente (tipo STOCK_BAJO) y se completan con el stock y el umbral
     * actuales de cada publicacion.
     */
    @Transactional(readOnly = true)
    public List<AvisoStockBajoResponse> listarAvisos(Usuario vendedor) {
        exigirVendedor(vendedor);
        List<NotificacionResponse> avisos = notificaciones.listarPorTipo(vendedor, NotificacionService.STOCK_BAJO);
        if (avisos.isEmpty()) {
            return List.of();
        }

        Map<Long, Producto> publicaciones = new LinkedHashMap<>();
        for (Producto producto : productos.findByVendedorId(vendedor.getId())) {
            publicaciones.put(producto.getId(), producto);
        }
        Map<Long, UmbralStock> porProducto = new LinkedHashMap<>();
        for (UmbralStock registro : umbrales.findByVendedorId(vendedor.getId())) {
            porProducto.put(registro.getProducto().getId(), registro);
        }

        List<AvisoStockBajoResponse> respuestas = new ArrayList<>(avisos.size());
        for (NotificacionResponse aviso : avisos) {
            Producto producto = publicaciones.get(aviso.productoId());
            UmbralStock registro = porProducto.get(aviso.productoId());
            Integer umbral = (registro == null) ? null : registro.getUmbral();
            Integer stockActual = (producto == null) ? null : stock(producto);
            respuestas.add(new AvisoStockBajoResponse(
                    aviso.idNotificacion(),
                    aviso.productoId(),
                    (producto == null) ? null : producto.getNombre(),
                    (producto == null) ? null : producto.getImagenUrl(),
                    stockActual,
                    umbral,
                    (umbral != null && stockActual != null) ? stockActual <= umbral : Boolean.FALSE,
                    aviso.estado(),
                    aviso.fecha()));
        }
        return respuestas;
    }

    // ------------------------------------------------------------------
    // Deteccion del cruce (punto unico de la aplicacion)
    // ------------------------------------------------------------------

    /**
     * Detecta el cruce del umbral tras un cambio de stock y emite el aviso
     * cuando corresponde. Es el unico lugar donde se decide si hay aviso:
     * lo invocan la venta y la edicion manual de stock.
     */
    @Transactional
    public void registrarCambioStock(Producto producto, int stockAnterior, int stockNuevo) {
        if (producto == null || producto.getId() == null) {
            return;
        }
        if (stockAnterior == stockNuevo) {
            return;
        }
        Optional<UmbralStock> encontrado = umbrales.buscarParaActualizar(producto.getId());
        if (encontrado.isEmpty()) {
            // El producto no tiene umbral configurado: no hay nada que avisar.
            return;
        }

        UmbralStock registro = encontrado.get();
        int limite = registro.getUmbral();
        boolean bajoAntes = stockAnterior <= limite;
        boolean bajoAhora = stockNuevo <= limite;

        if (!bajoAntes && bajoAhora) {
            // Cruzada descendente: nuevo aviso (una clave por producto y cruce).
            int numero = (registro.getAlertasEmitidas() == null ? 0 : registro.getAlertasEmitidas()) + 1;
            registro.setAlertasEmitidas(numero);
            registro.setAlertaDisparada(true);
            registro.setFechaActualizacion(LocalDateTime.now());
            umbrales.save(registro);
            notificaciones.notificarStockBajo(producto, stockNuevo, limite, numero);
        } else if (bajoAntes && !bajoAhora) {
            // Reposicion: rearma la alerta para permitir un futuro cruce.
            registro.setAlertaDisparada(false);
            registro.setFechaActualizacion(LocalDateTime.now());
            umbrales.save(registro);
        }
    }

    // ------------------------------------------------------------------
    // Reglas compartidas
    // ------------------------------------------------------------------

    private Producto productoPropio(Usuario vendedor, Long idProducto) {
        if (idProducto == null || idProducto < 1) {
            throw new ProductoNoEncontradoException("Producto no encontrado");
        }
        Producto producto = productos.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(
                        "Producto no encontrado con id " + idProducto));
        if (producto.getVendedorId() == null || !producto.getVendedorId().equals(vendedor.getId())) {
            throw new PropietarioInvalidoException("El producto no pertenece al vendedor");
        }
        return producto;
    }

    private UmbralStockResponse respuesta(UmbralStock registro, Producto producto) {
        Integer umbral = (registro == null) ? null : registro.getUmbral();
        int stock = stock(producto);
        boolean stockBajo = (umbral != null) && stock <= umbral;
        return new UmbralStockResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getImagenUrl(),
                stock,
                umbral,
                stockBajo,
                (registro != null) && registro.isAlertaDisparada(),
                (registro == null) ? null : registro.getFechaActualizacion());
    }

    private int stock(Producto producto) {
        return (producto == null || producto.getStock() == null) ? 0 : producto.getStock();
    }

    private void exigirVendedor(Usuario vendedor) {
        if (vendedor == null) {
            throw error(HttpStatus.UNAUTHORIZED, "Sesion requerida.");
        }
        if (vendedor.getRol() != Rol.VENDEDOR) {
            throw error(HttpStatus.FORBIDDEN, "Solo un vendedor puede gestionar los umbrales de stock.");
        }
    }

    private ExperienciaCompraException error(HttpStatus estado, String mensaje) {
        return new ExperienciaCompraException(estado, mensaje);
    }
}
