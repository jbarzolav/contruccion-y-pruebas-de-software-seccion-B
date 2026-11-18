from django import forms
from django.conf import settings
from django.contrib import admin
from django.core.exceptions import PermissionDenied
from django.http import Http404
from django.template.response import TemplateResponse
from django.urls import path, reverse
from django.utils.html import format_html
from django.utils.formats import date_format
from django.utils.timezone import localtime

from .models import ItemRevision, ListaRevision, RegistroAuditoriaCompartido, UsuarioCompartido
from .repositories import UserApiRepository
from .serializers import AsignarRolSerializer
from .services import AdminService

class ConsultaCompartidaAdmin(admin.ModelAdmin):
    actions = None

    def get_queryset(self, request):
        return super().get_queryset(request).using(settings.SHARED_DB_ALIAS)

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


class CambioRolForm(forms.Form):
    rol = forms.ChoiceField(
        label="Nuevo rol", choices=AsignarRolSerializer().fields["rol"].choices.items()
    )
    correo = forms.EmailField(label="Correo de administrador de PulgaTec")
    password = forms.CharField(label="Contraseña de PulgaTec", widget=forms.PasswordInput)


@admin.register(UsuarioCompartido)
class UsuarioCompartidoAdmin(ConsultaCompartidaAdmin):
    list_display = ("correo", "rol", "cambio_rol")
    search_fields = ("correo",)
    list_filter = ("rol",)
    ordering = ("id",)

    @admin.display(description="Acción")
    def cambio_rol(self, usuario):
        url = reverse("admin:pulgatec_cambiar_rol", args=[usuario.pk])
        return format_html('<a href="{}">Cambiar rol</a>', url)

    def get_urls(self):
        return [
            path("<int:usuario_id>/rol/", self.admin_site.admin_view(self.asignar_rol),
                 name="pulgatec_cambiar_rol"),
        ] + super().get_urls()

    def asignar_rol(self, request, usuario_id):
        if not request.user.is_superuser:
            raise PermissionDenied
        usuario = self.get_object(request, str(usuario_id))
        if usuario is None:
            raise Http404
        form = CambioRolForm(request.POST if request.method == "POST" else None,
                             initial={"rol": usuario.rol})
        if request.method == "POST" and form.is_valid():
            datos = form.cleaned_data
            if datos["rol"] == usuario.rol:
                form.add_error("rol", "Selecciona un rol distinto al actual.")
            else:
                status, result = AdminService.cambiar_rol_desde_django(usuario.pk, datos)
                if status == 200:
                    rol_nuevo = result.get("rol", datos["rol"]) if isinstance(result, dict) else datos["rol"]
                    auditoria_url = reverse("admin:admin_api_registroauditoriacompartido_changelist")
                    return TemplateResponse(request, "admin/cambiar_rol.html", {
                        **self.admin_site.each_context(request),
                        "title": "Cambio de rol registrado",
                        "usuario": usuario,
                        "guardado": True,
                        "rol_anterior": usuario.rol,
                        "rol_nuevo": rol_nuevo,
                        "usuarios_url": reverse("admin:admin_api_usuariocompartido_changelist"),
                        "auditoria_url": f"{auditoria_url}?accion=CAMBIO_ROL",
                    })
                error = result.get("messages") if isinstance(result, dict) else None
                form.add_error(None, error[0] if error else "No se pudo cambiar el rol. Verifica la cuenta y la conexión con Spring.")
        return TemplateResponse(request, "admin/cambiar_rol.html", {
            **self.admin_site.each_context(request),
            "title": "Cambiar rol",
            "usuario": usuario,
            "form": form,
        })


ACCIONES_AUDITORIA = {
    "CAMBIO_ROL": "Cambio de rol",
    "LOGIN": "Inicio de sesión",
    "LOGOUT": "Cierre de sesión",
}


class AccionAuditoriaFilter(admin.SimpleListFilter):
    title = "acción"
    parameter_name = "accion"

    def lookups(self, request, model_admin):
        acciones = model_admin.get_queryset(request).order_by("accion").values_list("accion", flat=True).distinct()
        return [(accion, ACCIONES_AUDITORIA.get(accion, accion)) for accion in acciones]

    def queryset(self, request, queryset):
        if self.value():
            return queryset.filter(accion=self.value())
        return queryset


@admin.register(RegistroAuditoriaCompartido)
class RegistroAuditoriaAdmin(ConsultaCompartidaAdmin):
    list_display = ("accion_visible", "realizado_por", "fecha_local")
    fields = ("accion_visible", "realizado_por", "fecha_local")
    readonly_fields = fields
    search_fields = ("usuario",)
    list_filter = (AccionAuditoriaFilter, "fecha")
    ordering = ("-fecha", "-id")

    @admin.display(description="Acción", ordering="accion")
    def accion_visible(self, registro):
        return ACCIONES_AUDITORIA.get(registro.accion, registro.accion)

    @admin.display(description="Realizado por", ordering="usuario")
    def realizado_por(self, registro):
        return registro.usuario

    @admin.display(description="Fecha", ordering="fecha")
    def fecha_local(self, registro):
        return date_format(localtime(registro.fecha), "j M Y, g:i a")


class ItemRevisionInline(admin.TabularInline):
    model = ItemRevision
    extra = 1


@admin.register(ListaRevision)
class ListaRevisionAdmin(admin.ModelAdmin):
    list_display = ("nombre", "fecha")
    search_fields = ("nombre",)
    list_filter = ("fecha",)
    inlines = (ItemRevisionInline,)


@admin.register(ItemRevision)
class ItemRevisionAdmin(admin.ModelAdmin):
    list_display = ("tarea", "lista", "completado")
    search_fields = ("tarea", "lista__nombre")
    list_filter = ("completado",)
    list_select_related = ("lista",)
