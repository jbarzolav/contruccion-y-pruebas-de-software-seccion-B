from django.db import models


class UsuarioCompartido(models.Model):
    """Vista de solo lectura; Spring Boot es dueño de la tabla y de sus escrituras."""

    id = models.BigAutoField(primary_key=True)
    correo = models.CharField(max_length=255)
    rol = models.CharField(max_length=32)

    class Meta:
        managed = False
        db_table = "usuarios"
        verbose_name = "usuario de PulgaTec"
        verbose_name_plural = "usuarios de PulgaTec"


class RegistroAuditoriaCompartido(models.Model):
    """Proyección de solo lectura de la auditoría administrada por Spring."""

    id = models.BigAutoField(primary_key=True)
    accion = models.CharField(max_length=48)
    usuario = models.CharField(max_length=255)
    fecha = models.DateTimeField()

    class Meta:
        managed = False
        db_table = "registros_auditoria"
        verbose_name = "registro de auditoría"
        verbose_name_plural = "registros de auditoría"


class ListaRevision(models.Model):
    nombre = models.CharField(max_length=120)
    fecha = models.DateField(auto_now_add=True)

    class Meta:
        verbose_name = "lista de revisión"
        verbose_name_plural = "listas de revisión"

    def __str__(self):
        return self.nombre


class ItemRevision(models.Model):
    lista = models.ForeignKey(ListaRevision, on_delete=models.CASCADE, related_name="items")
    tarea = models.CharField(max_length=180)
    completado = models.BooleanField(default=False)

    class Meta:
        verbose_name = "ítem de revisión"
        verbose_name_plural = "ítems de revisión"
