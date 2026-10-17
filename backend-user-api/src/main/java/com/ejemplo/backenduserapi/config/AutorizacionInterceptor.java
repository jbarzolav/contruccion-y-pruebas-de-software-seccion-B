package com.ejemplo.backenduserapi.config;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.service.AutorizacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
/** HU 23 - Permisos de administración, publicaciones, carrito y pedidos. */
@Component
public class AutorizacionInterceptor implements HandlerInterceptor {
    private final AutorizacionService autorizacion;
    private final ItemCarritoRepository items;
    public AutorizacionInterceptor(AutorizacionService autorizacion, ItemCarritoRepository items) { this.autorizacion = autorizacion; this.items = items; }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        String permiso = null;
        if (ruta.startsWith("/api/vendedores/") || (ruta.startsWith("/api/productos") && !"GET".equals(request.getMethod()))) permiso = "GESTIONAR_PRODUCTOS";
        else if (ruta.startsWith("/api/carrito/") || ruta.equals("/api/pedidos")) permiso = "COMPRAR";
        if (permiso == null && !ruta.equals("/api/auth/me")) return true;
        Usuario usuario = autorizacion.autenticar(request.getHeader("Authorization"));
        if (permiso != null) autorizacion.exigirPermiso(usuario, permiso);
        request.setAttribute("usuarioSesion", usuario);
        for (String campo : new String[]{"vendedorId", "clienteId"}) {
            String valor = request.getParameter(campo);
            if (valor != null) {
                try { autorizacion.exigirPropietario(usuario, Long.valueOf(valor)); }
                catch (NumberFormatException ex) { throw new AutorizacionException(HttpStatus.BAD_REQUEST, "Identificador inválido."); }
            }
        }
        if (ruta.matches("/api/carrito/items/[0-9]+") && ("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))) {
            Long id = Long.valueOf(ruta.substring(ruta.lastIndexOf('/') + 1));
            if (items.existsById(id) && !items.existsByIdAndCarritoClienteId(id, usuario.getId()))
                throw new AutorizacionException(HttpStatus.FORBIDDEN, "El ítem pertenece a otro cliente.");
        }
        return true;
    }
}
