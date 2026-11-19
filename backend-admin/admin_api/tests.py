from datetime import datetime, timezone
from unittest.mock import call, patch

from django.contrib.auth import get_user_model

from django.db import connection
from django.test import SimpleTestCase, TestCase, TransactionTestCase
from django.urls import reverse
from rest_framework.test import APIClient

from .models import ListaRevision, RegistroAuditoriaCompartido, UsuarioCompartido
from .services import AdminService


class AdminEndpointsTests(SimpleTestCase):
    def setUp(self):
        self.client = APIClient()
        self.client.credentials(HTTP_AUTHORIZATION="Bearer opaque-test-session")

    @patch("admin_api.services.UserApiRepository.leer_usuarios")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_admin_lista_usuarios_directo_de_bd_sin_campos_sensibles(self, authenticate, reader):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        reader.return_value = [{"id": 1, "correo": "admin@example.test", "rol": "ADMINISTRADOR", "permisos": []}]
        response = self.client.get("/api/admin/usuarios")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()[0]["correo"], "admin@example.test")
        self.assertNotIn("password", response.content.decode().lower())
        self.assertNotIn("token", response.content.decode().lower())
        authenticate.assert_called_once_with("Bearer opaque-test-session")
        reader.assert_called_once_with()

    @patch("admin_api.services.UserApiRepository.asignar_rol")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_admin_delega_cambio_de_rol_solo_a_api_interna(self, authenticate, internal):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        internal.return_value = (200, b'{"id":2,"correo":"u@example.test","rol":"CLIENTE"}')
        for role in ("CLIENTE", "VENDEDOR", "ADMINISTRADOR"):
            response = self.client.patch(
                "/api/admin/usuarios/2/rol", {"rol": role}, format="json"
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(internal.call_args.args, (2, "Bearer opaque-test-session", {"rol": role}))

    def test_rol_desconocido_es_400_sin_llamar_spring(self):
        with patch("admin_api.views.AdminService.asignar_rol") as upstream:
            response = self.client.patch(
                "/api/admin/usuarios/2/rol", {"rol": "SUPERADMIN"}, format="json"
            )
        self.assertEqual(response.status_code, 400)
        upstream.assert_not_called()

    @patch("admin_api.services.UserApiRepository.asignar_rol")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_usuario_inexistente_se_propaga_como_404(self, authenticate, internal):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        internal.return_value = (404, b'{"status":404,"error":"No encontrado"}')
        response = self.client.patch(
            "/api/admin/usuarios/999/rol", {"rol": "VENDEDOR"}, format="json"
        )
        self.assertEqual(response.status_code, 404)

    @patch("admin_api.services.UserApiRepository.asignar_rol")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_cliente_y_vendedor_no_llegan_a_escritura(self, authenticate, internal):
        for role in ("CLIENTE", "VENDEDOR"):
            authenticate.return_value = (200, f'{{"id":2,"correo":"u@example.test","rol":"{role}"}}'.encode())
            response = self.client.patch(
                "/api/admin/usuarios/2/rol", {"rol": "ADMINISTRADOR"}, format="json"
            )
            self.assertEqual(response.status_code, 403)
        internal.assert_not_called()

    @patch("admin_api.services.UserApiRepository.leer_auditoria")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_auditoria_directa_paginada_y_campos_publicos(self, authenticate, reader):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        reader.return_value = {"contenido": [{"id": 1, "accion": "LOGIN", "usuario": "u@example.test", "fecha": "2026-10-16T00:00:00Z"}], "pagina": 0, "tamano": 20, "totalElementos": 1, "totalPaginas": 1}
        response = self.client.get("/api/admin/auditoria?page=0&size=20")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["contenido"][0]["accion"], "LOGIN")
        body = response.content.decode().lower()
        for secret in ("password", "passwordhash", "token", "recovery"):
            self.assertNotIn(secret, body)
        reader.assert_called_once_with(0, 20)

    @patch("admin_api.services.UserApiRepository.leer_auditoria")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_auditoria_vacia_se_conserva(self, authenticate, reader):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        reader.return_value = {"contenido": [], "pagina": 0, "tamano": 20, "totalElementos": 0, "totalPaginas": 0}
        response = self.client.get("/api/admin/auditoria")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["contenido"], [])

    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_no_auth_y_roles_sin_permiso_dan_401_403(self, authenticate):
        casos = [
            ("get", "/api/admin/usuarios", None),
            ("patch", "/api/admin/usuarios/2/rol", {"rol": "VENDEDOR"}),
            ("get", "/api/admin/auditoria", None),
        ]
        self.client.credentials()
        authenticate.return_value = (401, b'{"status":401,"error":"No autenticado"}')
        for metodo, ruta, body in casos:
            if body:
                respuesta = getattr(self.client, metodo)(ruta, body, format="json")
            else:
                respuesta = getattr(self.client, metodo)(ruta)
            self.assertEqual(respuesta.status_code, 401, f"{metodo.upper()} {ruta} sin token")

        self.client.credentials(HTTP_AUTHORIZATION="Bearer user-session")
        authenticate.return_value = (200, b'{"id":2,"correo":"user@example.test","rol":"CLIENTE"}')
        for metodo, ruta, body in casos:
            if body:
                respuesta = getattr(self.client, metodo)(ruta, body, format="json")
            else:
                respuesta = getattr(self.client, metodo)(ruta)
            self.assertEqual(respuesta.status_code, 403, f"{metodo.upper()} {ruta} sin rol ADMINISTRADOR")

    @patch("admin_api.services.UserApiRepository.leer_auditoria")
    @patch("admin_api.services.UserApiRepository.autenticar_administrador")
    def test_parametros_auditoria_invalidos_son_validados_en_django(self, authenticate, reader):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        response = self.client.get("/api/admin/auditoria?page=-1&size=101")
        self.assertEqual(response.status_code, 400)
        reader.assert_not_called()


class AdminServiceFailureTests(SimpleTestCase):
    def test_respuesta_no_json_del_upstream_es_502(self):
        from admin_api.services import AdminService

        with patch("admin_api.services.UserApiRepository.autenticar_administrador", return_value=(200, b"not-json")):
            code, data = AdminService.listar_usuarios("Bearer token")
        self.assertEqual(code, 502)
        self.assertEqual(data["error"], "Respuesta inválida del backend de usuarios.")


class MySqlSharedReadTests(TransactionTestCase):
    """Ejercita modelos managed=False sobre tablas de prueba aisladas en SQLite."""

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        with connection.schema_editor() as editor:
            editor.create_model(UsuarioCompartido)
            editor.create_model(RegistroAuditoriaCompartido)

    @classmethod
    def tearDownClass(cls):
        with connection.schema_editor() as editor:
            editor.delete_model(RegistroAuditoriaCompartido)
            editor.delete_model(UsuarioCompartido)
        super().tearDownClass()

    def setUp(self):
        UsuarioCompartido.objects.create(id=50, correo="safe@example.test", rol="ADMINISTRADOR")
        RegistroAuditoriaCompartido.objects.create(
            id=101, accion="LOGIN", usuario="safe@example.test",
            fecha=datetime(2026, 10, 16, 9, 0, tzinfo=timezone.utc),
        )
        RegistroAuditoriaCompartido.objects.create(
            id=102, accion="CAMBIO_ROL", usuario="safe@example.test",
            fecha=datetime(2026, 10, 16, 10, 0, tzinfo=timezone.utc),
        )

    def tearDown(self):
        RegistroAuditoriaCompartido.objects.all().delete()
        UsuarioCompartido.objects.all().delete()
        super().tearDown()

    @patch("admin_api.repositories.UserApiRepository.autenticar_administrador")
    def test_bd_compartida_lee_usuario_sin_hash(self, authenticate):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        code, users = AdminService.listar_usuarios("Bearer test-token")
        self.assertEqual(code, 200)
        self.assertEqual(users[0]["correo"], "safe@example.test")
        self.assertEqual(users[0]["rol"], "ADMINISTRADOR")
        self.assertNotIn("passwordHash", users[0])

    @patch("admin_api.repositories.UserApiRepository.autenticar_administrador")
    def test_bd_compartida_lee_auditoria_mas_reciente_primero(self, authenticate):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        code, result = AdminService.listar_auditoria("?page=0&size=1", "Bearer test-token")
        self.assertEqual(code, 200)
        self.assertEqual(result["contenido"][0]["accion"], "CAMBIO_ROL")
        self.assertEqual(result["totalElementos"], 2)
        self.assertNotIn("passwordHash", str(result))

    @patch("admin_api.repositories.UserApiRepository.autenticar_administrador")
    def test_lectura_compartida_requiere_rol_admin(self, authenticate):
        authenticate.return_value = (200, b'{"id":1,"correo":"user@example.test","rol":"CLIENTE"}')
        code, _ = AdminService.listar_usuarios("Bearer client-token")
        self.assertEqual(code, 403)

    @patch("admin_api.repositories.UserApiRepository.autenticar_administrador")
    def test_paginacion_rechaza_parametros_invalidos(self, authenticate):
        authenticate.return_value = (200, b'{"id":1,"correo":"admin@example.test","rol":"ADMINISTRADOR"}')
        code, _ = AdminService.listar_auditoria("?page=-1&size=101", "Bearer test-token")
        self.assertEqual(code, 400)

    @patch("admin_api.repositories.UserApiRepository.asignar_rol")
    @patch("admin_api.repositories.UserApiRepository.request")
    def test_admin_django_lista_filtra_y_delega_cambio_de_rol(self, request_spring, assign):
        self.client.force_login(get_user_model().objects.create_superuser("operador"))
        users_url = reverse("admin:admin_api_usuariocompartido_changelist")
        users = self.client.get(users_url, {"q": "safe@", "rol": "ADMINISTRADOR"})
        self.assertContains(users, "safe@example.test")
        audit_url = reverse("admin:admin_api_registroauditoriacompartido_changelist")
        audit = self.client.get(audit_url, {"accion": "CAMBIO_ROL"})
        self.assertEqual(audit.context["cl"].result_count, 1)
        detalle_auditoria = self.client.get(reverse(
            "admin:admin_api_registroauditoriacompartido_change", args=[102]
        ))
        self.assertContains(detalle_auditoria, "Realizado por:")
        self.assertContains(detalle_auditoria, "safe@example.test")

        request_spring.side_effect = [
            (200, b'{"token":"sesion-temporal"}'),
            (200, b'{"rol":"ADMINISTRADOR"}'),
            (200, b'{}'),
        ]
        assign.return_value = (200, b'{"rol":"VENDEDOR"}')
        role_url = reverse("admin:pulgatec_cambiar_rol", args=[50])
        formulario = self.client.get(role_url)
        self.assertContains(formulario, "Guardar cambio")
        self.assertContains(formulario, 'id="guardar-cambio-rol" type="submit" value="Guardar cambio" disabled')
        response = self.client.post(role_url, {
            "rol": "VENDEDOR", "correo": "admin@example.test", "password": "clave-de-prueba",
        })
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Rol actualizado para")
        self.assertContains(response, "Rol anterior:")
        self.assertContains(response, "Rol nuevo:")
        self.assertContains(response, f'{audit_url}?accion=CAMBIO_ROL')
        self.assertContains(response, users_url)
        assign.assert_called_once_with(50, "Bearer sesion-temporal", {"rol": "VENDEDOR"})
        self.assertEqual(request_spring.call_args_list, [
            call("/api/auth/login", method="POST",
                 body={"correo": "admin@example.test", "password": "clave-de-prueba"}),
            call("/api/auth/me", authorization="Bearer sesion-temporal"),
            call("/api/auth/logout", method="POST", authorization="Bearer sesion-temporal"),
        ])

    def test_cambio_de_rol_mismo_valor_no_llama_a_spring(self):
        self.client.force_login(get_user_model().objects.create_superuser("operador"))
        role_url = reverse("admin:pulgatec_cambiar_rol", args=[50])
        with patch("admin_api.admin.AdminService.cambiar_rol_desde_django") as change_role:
            response = self.client.post(role_url, {
                "rol": "ADMINISTRADOR", "correo": "admin@example.test", "password": "clave-de-prueba",
            })
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Selecciona un rol distinto al actual.")
        change_role.assert_not_called()

    def test_error_de_spring_se_muestra_y_no_indica_rol_actualizado(self):
        self.client.force_login(get_user_model().objects.create_superuser("operador"))
        role_url = reverse("admin:pulgatec_cambiar_rol", args=[50])
        with patch("admin_api.admin.AdminService.cambiar_rol_desde_django", return_value=(
            503, {"messages": ["No fue posible completar el cambio de rol."]}
        )):
            response = self.client.post(role_url, {
                "rol": "VENDEDOR", "correo": "admin@example.test", "password": "clave-de-prueba",
            })
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "No fue posible completar el cambio de rol.")
        self.assertContains(response, "Rol actual ADMINISTRADOR")
        self.assertNotContains(response, "Cambio de rol registrado")

    def test_cambio_de_rol_rechaza_personal_sin_superusuario(self):
        staff = get_user_model().objects.create_user("staff", is_staff=True)
        self.client.force_login(staff)
        response = self.client.get(reverse("admin:pulgatec_cambiar_rol", args=[50]))
        self.assertEqual(response.status_code, 403)


class ListaRevisionAdminTests(TestCase):
    def test_alta_guarda_el_item_en_el_formulario_de_lista(self):
        self.client.force_login(get_user_model().objects.create_superuser("operador"))
        response = self.client.post(reverse("admin:admin_api_listarevision_add"), {
            "nombre": "Revisión de pedidos",
            "items-TOTAL_FORMS": "1", "items-INITIAL_FORMS": "0",
            "items-MIN_NUM_FORMS": "0", "items-MAX_NUM_FORMS": "1000",
            "items-0-tarea": "Validar el estado del pedido", "items-0-completado": "on",
        })
        self.assertEqual(response.status_code, 302)
        item = ListaRevision.objects.get(nombre="Revisión de pedidos").items.get()
        self.assertEqual(item.tarea, "Validar el estado del pedido")
        self.assertTrue(item.completado)
