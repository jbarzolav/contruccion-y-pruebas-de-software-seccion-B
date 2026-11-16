from django.contrib.admin import AdminSite


class PulgaTecAdminSite(AdminSite):
    site_header = "Administración de PulgaTec"
    site_title = "PulgaTec"
    index_title = "Gestión de PulgaTec"
    site_url = None

    def get_app_list(self, request, app_label=None):
        apps = super().get_app_list(request, app_label)
        for app in apps:
            for model in app["models"]:
                if model["model"]._meta.label_lower == "auth.user":
                    model["name"] = "Cuentas del panel"
        return apps
