from rest_framework import serializers


class AsignarRolSerializer(serializers.Serializer):
    rol = serializers.ChoiceField(choices=("CLIENTE", "VENDEDOR", "ADMINISTRADOR"))
