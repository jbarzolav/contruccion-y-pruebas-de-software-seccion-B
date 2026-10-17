from django.urls import path

from .views import AuditoriaAdminView, UsuarioRolAdminView, UsuariosAdminView

urlpatterns = [
    path("usuarios", UsuariosAdminView.as_view(), name="admin-usuarios"),
    path("usuarios/<int:id_usuario>/rol", UsuarioRolAdminView.as_view(), name="admin-usuario-rol"),
    path("auditoria", AuditoriaAdminView.as_view(), name="admin-auditoria"),
]
