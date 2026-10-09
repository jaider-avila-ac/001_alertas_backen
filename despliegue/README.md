# Despliegue

Dos servidores en la misma red privada del proveedor:

| Servidor | Corre | Abierto a internet |
|---|---|---|
| **BD** | PostgreSQL 17 (`db/`) | Nada. Solo el puerto 5432 para la IP privada del servidor de la app |
| **App** | Backend + Redis (`docker-compose.yml`), los dos fronts y nginx | 80 y 443 (y 22 para entrar) |

Los contenedores solo escuchan en `127.0.0.1` del servidor de la app; desde afuera todo entra por nginx con https.

```
navegador ──https──> nginx (app) ─┬─> 127.0.0.1:8081  front de las instituciones
                                  ├─> 127.0.0.1:8082  superadmin
                                  └─> 127.0.0.1:8085  backend ──> redis (red de docker)
                                                          └──ssl, ip privada──> PostgreSQL (servidor BD)
```

En los ejemplos: IP privada del servidor BD `10.0.0.3`, IP privada del servidor de la app `10.0.0.2`.
Dominios: `app.midominio.com`, `admin.midominio.com`, `api.midominio.com`.

---

## 1. Servidor de la BD

### 1.1 Archivos
```bash
git clone <repo del backend> alertas && cd alertas/db
cp .env.example .env
```
En `.env`: contraseñas largas y distintas (`openssl rand -base64 24`) y `POSTGRES_BIND=10.0.0.3` (la IP privada, nunca `0.0.0.0`).

### 1.2 Certificado para SSL
```bash
mkdir -p certs
openssl req -new -x509 -days 3650 -nodes -subj "/CN=bd-alertas" \
  -keyout certs/server.key -out certs/server.crt
sudo chown 70:70 certs/server.key certs/server.crt   # 70 = usuario postgres de la imagen alpine
sudo chmod 600 certs/server.key
```

### 1.3 Quién puede entrar
En `pg_hba.conf` cambiar `10.0.0.2/32` por la IP privada del servidor de la app. Solo entra `alertas_app` (el usuario con RLS), solo con SSL.

### 1.4 Firewall
```bash
sudo ufw default deny incoming
sudo ufw allow 22/tcp
sudo ufw allow from 10.0.0.2 to any port 5432 proto tcp
sudo ufw enable
```
Docker publica puertos por fuera de ufw: por eso además el puerto se publica solo en la IP privada (`POSTGRES_BIND`). Si el proveedor tiene firewall propio (security groups), dejar ahí la misma regla.

### 1.5 Arrancar y migrar
```bash
docker compose -f docker-compose.yml -f docker-compose.produccion.yml up -d
./migrar.sh --estado     # muestra las pendientes
./migrar.sh              # las aplica en orden
./probar.sh              # pruebas de RLS (terminan en rollback, no dejan datos)
```

### 1.6 Revisar que el SSL quedó obligatorio
Desde el servidor de la app:
```bash
docker run --rm -e PGPASSWORD='<DB_APP_PASSWORD>' postgres:17-alpine \
  psql "host=10.0.0.3 dbname=alertas user=alertas_app sslmode=require" -c "select ssl, version from pg_stat_ssl where pid = pg_backend_pid()"
# debe decir t | TLSv1.3.  con sslmode=disable debe fallar con "no pg_hba.conf entry"
```
Si falla con `no pg_hba.conf entry for host "X"`, la IP `X` es la que ve PostgreSQL: esa es la que va en `pg_hba.conf`.

### 1.7 Copias de seguridad
Diaria, con cron (`crontab -e`):
```
0 2 * * * cd /home/usuario/alertas/db && docker compose exec -T postgres pg_dump -U postgres -Fc alertas > /backups/alertas-$(date +\%F).dump && find /backups -name 'alertas-*.dump' -mtime +14 -delete
```
Restaurar: `pg_restore -U postgres -d alertas --clean <archivo>.dump`. Probar una restauración de vez en cuando; una copia que nunca se restauró no es una copia.

---

## 2. Servidor de la app

### 2.1 Backend y Redis
```bash
git clone <repo del backend> alertas-backen && cd alertas-backen
cp .env.example .env
```
En `.env`:
```
DB_URL=jdbc:postgresql://10.0.0.3:5432/alertas?sslmode=require
DB_USERNAME=alertas_app
DB_PASSWORD=<el DB_APP_PASSWORD del servidor BD>
REDIS_PASSWORD=<openssl rand -base64 24>
JWT_SECRET=<openssl rand -hex 32>
CORS_ORIGENES=https://app.midominio.com,https://admin.midominio.com
URL_FRONT=https://app.midominio.com
SUPERADMIN_USUARIO=...           # solo se usa si no hay ningun superadmin
SUPERADMIN_CONTRASENA=...
TWILIO_SID= / TWILIO_TOKEN= / TWILIO_NUMERO=   # vacias = no sale ningun SMS
```
`sslmode=require` cifra la conexión. Para además verificar el certificado del servidor BD: copiar su `server.crt` y usar `sslmode=verify-full&sslrootcert=/ruta/server.crt` (el CN debe coincidir con el host).

```bash
docker compose up -d --build
docker compose ps                       # backend "healthy"
curl -s http://127.0.0.1:8085/actuator/health   # {"status":"UP"}
```
El contenedor corre sin root, con `TZ=America/Bogota` y la memoria de la JVM según la del contenedor.

### 2.2 Fronts
```bash
git clone <repo del front> alertas-front && cd alertas-front
echo "VITE_API_URL=https://api.midominio.com" > .env
docker compose up -d --build            # queda en 127.0.0.1:8081

git clone <repo del superadmin> alertas-superadmin && cd alertas-superadmin
printf "VITE_API_URL=https://api.midominio.com\nVITE_URL_FRONT=https://app.midominio.com\n" > .env
docker compose up -d --build            # queda en 127.0.0.1:8082
```
Las direcciones quedan dentro de la imagen al construirla: si cambian, hay que volver a construir.

### 2.3 nginx y https
```bash
sudo apt install nginx certbot python3-certbot-nginx
sudo cp alertas-backen/despliegue/nginx-vps.conf /etc/nginx/sites-available/alertas
# cambiar midominio.com por el dominio real
sudo ln -s /etc/nginx/sites-available/alertas /etc/nginx/sites-enabled/
sudo certbot certonly --nginx -d app.midominio.com -d admin.midominio.com -d api.midominio.com
sudo nginx -t && sudo systemctl reload nginx
```
Lo que hace `nginx-vps.conf`: http a https, el WebSocket de `/ws/` (cabeceras `Upgrade` y `Connection`), la IP real del cliente al backend, límite de intentos al login, `/actuator` cerrado a internet y el superadmin con opción de dejarlo solo para ciertas IP.

### 2.4 Firewall
```bash
sudo ufw default deny incoming
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
sudo ufw enable
```

---

## 3. Actualizar
```bash
# servidor BD, si hay migraciones nuevas (antes que el backend)
cd alertas && git pull && cd db && ./migrar.sh
# servidor de la app
cd alertas-backen && git pull && docker compose up -d --build
cd alertas-front && git pull && docker compose up -d --build
cd alertas-superadmin && git pull && docker compose up -d --build
```

## 4. Antes de abrirlo a los colegios
- [ ] Contraseñas largas y distintas en los dos `.env`; el `.env` nunca va a git.
- [ ] `sslmode=require` en `DB_URL` y probado el rechazo sin SSL (1.6).
- [ ] Puerto 5432 cerrado a internet (probar desde otra red: debe fallar).
- [ ] Solo 22, 80 y 443 abiertos en el servidor de la app.
- [ ] https funcionando en los tres dominios y `/actuator/health` no responde desde afuera.
- [ ] Copia de seguridad corriendo y una restauración probada.
- [ ] SMS: llaves de Twilio solo si se van a usar; cada colegio se enciende desde el superadmin.
