package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.EstadoPedido;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** HU 55/56 - Validaciones compartidas para filtrar y paginar pedidos. */
final class FiltroPedidos {
    static final int TAMANO_MAXIMO = 100;

    private FiltroPedidos() { }

    static String estado(String estado) {
        if (estado == null || estado.isBlank()) return null;
        String normalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (EstadoPedido.desde(normalizado) == null) {
            throw error("El estado indicado no es válido.");
        }
        return normalizado;
    }

    static void validar(LocalDate desde, LocalDate hasta, BigDecimal montoMin, BigDecimal montoMax,
                        Integer pagina, Integer tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw error("La fecha inicial no puede ser posterior a la fecha final.");
        }
        if ((montoMin != null && montoMin.signum() < 0) || (montoMax != null && montoMax.signum() < 0)) {
            throw error("Los montos deben ser mayores o iguales a cero.");
        }
        if (montoMin != null && montoMax != null && montoMin.compareTo(montoMax) > 0) {
            throw error("El monto mínimo no puede superar al monto máximo.");
        }
        if (pagina == null || pagina < 0 || tamano == null || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw error("La página debe ser mayor o igual a cero y el tamaño debe estar entre 1 y 100.");
        }
    }

    static boolean fechaEnRango(LocalDateTime fecha, LocalDate desde, LocalDate hasta) {
        return fecha != null
                && (desde == null || !fecha.toLocalDate().isBefore(desde))
                && (hasta == null || !fecha.toLocalDate().isAfter(hasta));
    }

    static <T> List<T> pagina(List<T> resultados, int pagina, int tamano) {
        long inicioLong = (long) pagina * tamano;
        if (inicioLong >= resultados.size()) return List.of();
        int inicio = (int) inicioLong;
        return List.copyOf(resultados.subList(inicio, Math.min(inicio + tamano, resultados.size())));
    }

    private static ExperienciaCompraException error(String mensaje) {
        return new ExperienciaCompraException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
