package com.ejemplo.backenduserapi.service;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HU 22 - Límite de intentos para la recuperación por código.
 * El código es corto (6 dígitos), así que se bloquea el uso masivo:
 * máximo 10 comprobaciones por minuto y por equipo, y máximo 6 intentos
 * por código. Los contadores viven en memoria y se reinician cada minuto.
 */
@Component
public class LimiteRecuperacion {
    public static final int MAX_INTENTOS_POR_MINUTO = 10;
    public static final int MAX_INTENTOS_POR_CODIGO = 6;

    private final Map<String, AtomicInteger> porEquipo = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> porCodigo = new ConcurrentHashMap<>();
    private final Map<String, Integer> minutoPorEquipo = new ConcurrentHashMap<>();
    private final Map<String, Integer> minutoPorCodigo = new ConcurrentHashMap<>();

    /** Registra un intento y devuelve false si ya se pasó el límite del equipo. */
    public boolean permitirPorEquipo(String equipo) {
        int minuto = minutoActual();
        Integer previo = minutoPorEquipo.put(equipo, minuto);
        if (previo == null || previo != minuto) {
            porEquipo.put(equipo, new AtomicInteger(1));
            return true;
        }
        return porEquipo.computeIfAbsent(equipo, k -> new AtomicInteger(0)).incrementAndGet() <= MAX_INTENTOS_POR_MINUTO;
    }

    /** Registra un intento sobre un código y devuelve false si se agotaron los intentos. */
    public boolean permitirPorCodigo(String codigo) {
        int minuto = minutoActual();
        Integer previo = minutoPorCodigo.put(codigo, minuto);
        if (previo == null || previo != minuto) {
            porCodigo.put(codigo, new AtomicInteger(1));
            return true;
        }
        return porCodigo.computeIfAbsent(codigo, k -> new AtomicInteger(0)).incrementAndGet() <= MAX_INTENTOS_POR_CODIGO;
    }

    /** Tras un acierto el código vuelve a tener intentos disponibles. */
    public void limpiarCodigo(String codigo) {
        porCodigo.remove(codigo);
        minutoPorCodigo.remove(codigo);
    }

    private int minutoActual() {
        return (int) (System.currentTimeMillis() / 60_000);
    }
}
