package com.ejemplo.backenduserapi.entity;

/** Acciones de negocio autorizadas para persistir en el historial de auditoría. */
public enum AccionAuditoria {
    LOGIN,
    LOGOUT,
    CAMBIO_ROL,
    RESTABLECIMIENTO_CONTRASENA
}
