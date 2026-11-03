package com.ejemplo.backenduserapi.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * HU 49 - Estados del pedido y transiciones permitidas.
 *
 * Un pedido registrado ("REGISTRADO") solo puede pasar a "EN_PROCESO" o ser
 * cancelado; un pedido en proceso puede entregarse o cancelarse. Los estados
 * "ENTREGADO" y "CANCELADO" son finales: no admiten cambios posteriores.
 *
 * Las transiciones se validan SIEMPRE en el servidor; la interfaz solo
 * propone el estado siguiente y el backend decide si es válido.
 */
public enum EstadoPedido {

    REGISTRADO(List.of("EN_PROCESO", "CANCELADO")),
    EN_PROCESO(List.of("ENTREGADO", "CANCELADO")),
    ENTREGADO(List.of()),
    CANCELADO(List.of());

    private final List<String> siguientes;

    EstadoPedido(List<String> siguientes) {
        this.siguientes = List.copyOf(siguientes);
    }

    /** Estados a los que puede pasar el pedido desde el estado actual. */
    public List<String> getSiguientes() {
        return siguientes;
    }

    /**
     * Convierte el texto recibido en un estado válido.
     * Un valor vacío/null significa "REGISTRADO" (estado inicial por defecto).
     * Devuelve null cuando el texto no corresponde a ningún estado conocido.
     */
    public static EstadoPedido desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return REGISTRADO;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(estado -> estado.name().equals(normalizado))
                .findFirst()
                .orElse(null);
    }

    /** Estados permitidos desde el estado indicado (lista vacía si es desconocido). */
    public static List<String> permitidosDesde(String valor) {
        EstadoPedido actual = desde(valor);
        return (actual == null) ? List.of() : actual.getSiguientes();
    }

    /** Todos los estados conocidos, para mensajes de ayuda de la API. */
    public static List<String> todos() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
