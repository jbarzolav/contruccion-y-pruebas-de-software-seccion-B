from django.contrib.admin.apps import AdminConfig


class PulgaTecAdminConfig(AdminConfig):
    default_site = "admin_api.sites.PulgaTecAdminSite"
