"""Inicia un servicio local cargando el .env de la raíz, sin imprimir secretos."""
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
env = os.environ.copy()
for line in (ROOT / '.env').read_text().splitlines():
    if line.strip() and not line.lstrip().startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        env[key.strip()] = value.strip().strip('\"\'')
service = sys.argv[1] if len(sys.argv) > 1 else ''
if service == 'spring':
    uploads = ROOT / 'backend-user-api/uploads'
    uploads.mkdir(exist_ok=True)
    env['APP_UPLOAD_DIR'] = str(uploads)
    folder = ROOT / 'backend-user-api'
    command = ['mvn', 'spring-boot:run', '-Dspring-boot.run.arguments=--server.address=127.0.0.1']
elif service == 'django':
    folder = ROOT / 'backend-admin'
    command = [str(folder / '.venv/bin/python'), 'manage.py', 'runserver', '127.0.0.1:8001', '--noreload']
else:
    raise SystemExit('Uso: python3 scripts/iniciar_local.py spring|django')
os.chdir(folder)
os.execvpe(command[0], command, env)
