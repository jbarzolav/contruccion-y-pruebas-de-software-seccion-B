package com.ejemplo.backenduserapi.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * HU 21 - Generación del token opaco de sesión.
 *
 * No es un JWT: es un identificador aleatorio e impredecible que el
 * cliente devolverá como "Authorization: Bearer {token}". Queda en
 * una clase aparte para que las siguientes historias (autorización)
 * puedan reutilizarlo sin tocar AuthService.
 */
@Service
public class TokenService {

    public String generarToken() {
        // UUID v4: 122 bits de aleatoriedad, suficiente para un token opaco
        return UUID.randomUUID().toString();
    }
}
