from django.http import QueryDict
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView

from .serializers import AsignarRolSerializer
from .services import AdminService


def _authorization(request):
    return request.headers.get("Authorization")


class UsuariosAdminView(APIView):
    def get(self, request):
        code, data = AdminService.listar_usuarios(_authorization(request))
        return Response(data, status=code)


class UsuarioRolAdminView(APIView):
    def patch(self, request, id_usuario):
        serializer = AsignarRolSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(
                {"status": 400, "error": "Validación", "messages": ["El rol debe ser CLIENTE, VENDEDOR o ADMINISTRADOR."]},
                status=status.HTTP_400_BAD_REQUEST,
            )
        code, data = AdminService.asignar_rol(
            id_usuario, _authorization(request), serializer.validated_data
        )
        return Response(data, status=code)


class AuditoriaAdminView(APIView):
    def get(self, request):
        # Pasar solo page/size, evitando reenviar parámetros ajenos al contrato.
        query = QueryDict(mutable=True)
        for key in ("page", "size"):
            if key in request.query_params:
                query.setlist(key, request.query_params.getlist(key))
        code, data = AdminService.listar_auditoria(
            f"?{query.urlencode()}" if query else "", _authorization(request)
        )
        return Response(data, status=code)
