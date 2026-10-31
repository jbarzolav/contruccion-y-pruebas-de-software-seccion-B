package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.CalificacionesVendedorResumenResponse;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.CalificacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CalificacionPublicaService {

    private final CalificacionRepository calificaciones;
    private final UsuarioRepository usuarios;

    public CalificacionPublicaService(CalificacionRepository calificaciones, UsuarioRepository usuarios) {
        this.calificaciones = calificaciones;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public CalificacionesVendedorResumenResponse obtenerResumen(Long vendedorId) {
        if (vendedorId == null || vendedorId < 1) {
            throw error(HttpStatus.BAD_REQUEST, "El identificador del vendedor no es válido.");
        }

        Usuario vendedor = usuarios.findById(vendedorId)
                .filter(usuario -> usuario.getRol() == Rol.VENDEDOR)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "No se encontró el vendedor."));

        long cantidad = calificaciones.countByVendedorIdAndValorBetween(vendedor.getId(), 1, 5);
        Double promedioCalculado = calificaciones.calcularPromedioPorVendedor(vendedor.getId());
        Map<Integer, Long> distribucion = new LinkedHashMap<>();
        for (int valor = 1; valor <= 5; valor++) {
            distribucion.put(valor, 0L);
        }
        calificaciones.contarPorValorYVendedor(vendedor.getId()).forEach(fila -> {
            int valor = ((Number) fila[0]).intValue();
            long total = ((Number) fila[1]).longValue();
            distribucion.put(valor, total);
        });

        BigDecimal promedio = BigDecimal.valueOf(promedioCalculado == null ? 0.0 : promedioCalculado)
                .setScale(2, RoundingMode.HALF_UP);
        return new CalificacionesVendedorResumenResponse(vendedor.getId(), promedio, cantidad, distribucion);
    }

    private ExperienciaCompraException error(HttpStatus estado, String mensaje) {
        return new ExperienciaCompraException(estado, mensaje);
    }
}
