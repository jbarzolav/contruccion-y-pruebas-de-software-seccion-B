import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from django.conf import settings
from .models import RegistroAuditoriaCompartido, UsuarioCompartido


class UserApiRepository:
    """Verifica sesiones Spring y delega solo la mutación de dominio interna."""

    @staticmethod
    def request(path, method="GET", authorization=None, body=None):
        headers = {"Accept": "application/json"}
        if authorization:
            headers["Authorization"] = authorization
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = Request(
            f"{settings.USER_API_BASE_URL}{path}", data=data, headers=headers, method=method
        )
        try:
            with urlopen(request, timeout=5) as response:
                return response.status, response.read()
        except HTTPError as error:
            return error.code, error.read()
        except (URLError, TimeoutError):
            return 503, b'{"status":503,"error":"Servicio no disponible","messages":["No fue posible contactar al backend de usuarios."]}'

    @staticmethod
    def autenticar_administrador(authorization):
        return UserApiRepository.request("/api/auth/me", authorization=authorization)

    @staticmethod
    def leer_usuarios():
        permisos = {
            "CLIENTE": ["COMPRAR"],
            "VENDEDOR": ["GESTIONAR_PRODUCTOS"],
            "ADMINISTRADOR": ["ASIGNAR_ROLES"],
        }
        return [
            {"id": user.id, "correo": user.correo, "rol": user.rol,
             "permisos": permisos.get(user.rol, [])}
            for user in UsuarioCompartido.objects.using("default").only("id", "correo", "rol").order_by("id")
        ]

    @staticmethod
    def leer_auditoria(page, size):
        query = RegistroAuditoriaCompartido.objects.using("default").only(
            "id", "accion", "usuario", "fecha"
        ).order_by("-fecha", "-id")
        total = query.count()
        items = query[page * size:(page + 1) * size]
        total_pages = (total + size - 1) // size
        return {
            "contenido": [
                {"id": entry.id, "accion": entry.accion, "usuario": entry.usuario,
                 "fecha": entry.fecha}
                for entry in items
            ],
            "pagina": page,
            "tamano": size,
            "totalElementos": total,
            "totalPaginas": total_pages,
        }

    @classmethod
    def asignar_rol(cls, id_usuario, authorization, body):
        key = settings.INTERNAL_ADMIN_API_KEY
        if not key:
            return 503, b'{"status":503,"error":"Servicio no configurado","messages":["La integraci\\u00f3n interna de administraci\\u00f3n no est\\u00e1 configurada."]}'
        headers = {"X-Internal-Admin-Key": key}
        if authorization:
            headers["Authorization"] = authorization
        data = json.dumps(body).encode("utf-8")
        request = Request(
            f"{settings.USER_API_BASE_URL}/_internal/admin/usuarios/{id_usuario}/rol",
            data=data, headers={**headers, "Accept": "application/json", "Content-Type": "application/json"},
            method="PATCH",
        )
        try:
            with urlopen(request, timeout=5) as response:
                return response.status, response.read()
        except HTTPError as error:
            return error.code, error.read()
        except (URLError, TimeoutError):
            return 503, b'{"status":503,"error":"Servicio no disponible","messages":["No fue posible completar el cambio de rol."]}'
