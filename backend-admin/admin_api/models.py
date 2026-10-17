from django.db import models


class UsuarioCompartido(models.Model):
    """Vista de solo lectura; Spring Boot es dueño de la tabla y de sus escrituras."""

    id = models.BigAutoField(primary_key=True)
    correo = models.CharField(max_length=255)
    rol = models.CharField(max_length=32)

    class Meta:
        managed = False
        db_table = "usuarios"


class RegistroAuditoriaCompartido(models.Model):
    """Proyección de solo lectura de la auditoría administrada por Spring."""

    id = models.BigAutoField(primary_key=True)
    accion = models.CharField(max_length=48)
    usuario = models.CharField(max_length=255)
    fecha = models.DateTimeField()

    class Meta:
        managed = False
        db_table = "registros_auditoria"
