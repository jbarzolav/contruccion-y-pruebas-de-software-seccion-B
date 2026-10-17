import json

from django.db import DatabaseError

from .repositories import UserApiRepository


class AdminService:
    """Administra lecturas compartidas y delega cambios de dominio a Spring."""

    @staticmethod
    def _decode(result):
        status, content = result
        try:
            data = json.loads(content) if content else None
        except (UnicodeDecodeError, json.JSONDecodeError):
            return 502, {"error": "Respuesta inválida del backend de usuarios."}
        return status, data

    @classmethod
    def listar_usuarios(cls, authorization):
        status, identity = cls._decode(UserApiRepository.autenticar_administrador(authorization))
        if status != 200:
            return status, identity
        if not isinstance(identity, dict) or identity.get("rol") != "ADMINISTRADOR":
            return 403, {"status": 403, "error": "Autorización", "messages": ["Solo ADMINISTRADOR puede consultar usuarios."]}
        try:
            return 200, UserApiRepository.leer_usuarios()
        except DatabaseError:
            return 503, {"status": 503, "error": "Base de datos no disponible", "messages": ["No fue posible leer los usuarios compartidos."]}

    @classmethod
    def asignar_rol(cls, id_usuario, authorization, body):
        status, identity = cls._decode(UserApiRepository.autenticar_administrador(authorization))
        if status != 200:
            return status, identity
        if not isinstance(identity, dict) or identity.get("rol") != "ADMINISTRADOR":
            return 403, {"status": 403, "error": "Autorización", "messages": ["Solo ADMINISTRADOR puede asignar roles."]}
        return cls._decode(
            UserApiRepository.asignar_rol(id_usuario, authorization, body)
        )

    @classmethod
    def listar_auditoria(cls, query_string, authorization):
        status, identity = cls._decode(UserApiRepository.autenticar_administrador(authorization))
        if status != 200:
            return status, identity
        if not isinstance(identity, dict) or identity.get("rol") != "ADMINISTRADOR":
            return 403, {"status": 403, "error": "Autorización", "messages": ["Solo ADMINISTRADOR puede consultar auditoría."]}
        try:
            from urllib.parse import parse_qs

            params = parse_qs(query_string.removeprefix("?"))
            page = int(params.get("page", ["0"])[0])
            size = int(params.get("size", ["20"])[0])
            if page < 0 or not 1 <= size <= 100:
                raise ValueError
            return 200, UserApiRepository.leer_auditoria(page, size)
        except (ValueError, TypeError):
            return 400, {"status": 400, "error": "Validación", "messages": ["La página debe ser >= 0 y el tamaño debe estar entre 1 y 100."]}
        except DatabaseError:
            return 503, {"status": 503, "error": "Base de datos no disponible", "messages": ["No fue posible leer la auditoría compartida."]}
