# Server Deployment

This project is ready to run on a single Linux server with Docker Compose.

## 4C4G quick path

If your server is `4 vCPU / 4 GB RAM`, use the lightweight deployment entry instead of the full stack:

- Compose file: `docker-compose.server-4c4g.yml`
- Env template: `deploy/env.production.4c4g.example`
- Public-IP env template: `deploy/env.production.4c4g.ip.example`
- Startup script: `deploy/server-up-4c4g.sh`
- Stop script: `deploy/server-down-4c4g.sh`
- Status script: `deploy/server-status-4c4g.sh`
- Ubuntu bootstrap script: `deploy/bootstrap-ubuntu-4c4g.sh`

This mode keeps the core application online with `MySQL + MongoDB + Redis + Backend + Frontend` and disables these heavier optional parts by default:

- Qdrant vector search
- `medical.json` automatic import
- Medplum MCP

You can turn them on later after moving to a larger server.

### Fastest path from a fresh Ubuntu server

After cloning the repo, the shortest path is:

```bash
cp deploy/env.production.4c4g.example .env
vim .env
DOMAIN=your-domain.example EMAIL=you@example.com ./deploy/bootstrap-ubuntu-4c4g.sh
```

If you do not have a domain and want to access the app directly by public IP:

```bash
cp deploy/env.production.4c4g.ip.example .env
vim .env
SERVER_HOST=your-public-ip ./deploy/bootstrap-ubuntu-4c4g.sh
```

For the public-IP template, the minimum values you should replace are:

- `INITIAL_ADMIN_PASSWORD`
- `AUTH_JWT_SECRET`
- `MYSQL_ROOT_PASSWORD`
- `LLM_API_KEY`

After startup, verify service status with:

```bash
./deploy/server-status-4c4g.sh
```

If DNS is not ready yet, install everything first without requesting the certificate:

```bash
DOMAIN=your-domain.example SKIP_CERTBOT=true ./deploy/bootstrap-ubuntu-4c4g.sh
```

## Recommended server

- Ubuntu 22.04 or 24.04
- 4 vCPU
- 8 GB RAM
- 40 GB SSD or more
- A public domain with DNS already pointed at the server

## 1. Install base software

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg nginx certbot python3-certbot-nginx

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

Log out and log back in after adding your user to the `docker` group.

## 2. Upload project code

```bash
git clone https://gitee.com/yongzhen-li/Multi-Agent-Based-Coordinated-Management.git
cd Multi-Agent-Based-Coordinated-Management
```

## 3. Prepare environment file

```bash
cp deploy/env.production.example .env
```

For a `4C4G` server, use:

```bash
cp deploy/env.production.4c4g.example .env
```

For `4C4G + public IP`, use:

```bash
cp deploy/env.production.4c4g.ip.example .env
```

Edit `.env` and replace at least these values:

- `INITIAL_ADMIN_PASSWORD`
- `AUTH_JWT_SECRET`
- `MYSQL_ROOT_PASSWORD`
- `LLM_API_KEY`
- `ALIYUN_OSS_*` if you want OSS uploads

By default, `MEDICAL_JSON_AUTO_IMPORT_ENABLED=false` in the production template to avoid a heavy first boot import on the server.

If you do not need vector retrieval immediately, set:

```bash
VECTOR_ENABLED=false
EMBEDDING_API_ENABLED=false
```

## 4. Start the application

Use the bundled preflight + startup script. It checks Docker, Compose, `.env`, and placeholder values before starting.

```bash
chmod +x deploy/server-preflight.sh deploy/server-up.sh deploy/server-update.sh deploy/server-down.sh
./deploy/server-up.sh
```

For a `4C4G` server, use:

```bash
chmod +x deploy/server-preflight.sh deploy/server-up-4c4g.sh deploy/server-update-4c4g.sh deploy/server-down-4c4g.sh
./deploy/server-up-4c4g.sh
```

If you prefer raw Compose commands:

```bash
docker compose --env-file .env -f docker-compose.server.yml up -d --build
docker compose --env-file .env -f docker-compose.server.yml ps
```

For a `4C4G` server, use:

```bash
docker compose --env-file .env -f docker-compose.server-4c4g.yml up -d --build
docker compose --env-file .env -f docker-compose.server-4c4g.yml ps
```

## 5. Configure host Nginx

Copy the example config and replace the domain:

```bash
sudo cp deploy/nginx/cdm.example.conf /etc/nginx/sites-available/cdm.conf
sudo nano /etc/nginx/sites-available/cdm.conf
sudo ln -s /etc/nginx/sites-available/cdm.conf /etc/nginx/sites-enabled/cdm.conf
sudo nginx -t
sudo systemctl reload nginx
```

If you access by public IP only, use [cdm.ip.conf](./nginx/cdm.ip.conf) instead. It keeps plain HTTP on port `80` and proxies to `127.0.0.1:8081`.

## 6. Issue HTTPS certificate

```bash
sudo certbot --nginx -d your-domain.example
```

Skip this step when you only use a public IP.

## 7. Firewall

Only allow these ports publicly:

- `22` for SSH
- `80` for HTTP
- `443` for HTTPS when you use a domain

Do not expose `3306`, `27017`, `6333`, or `8080` to the public internet.

## 8. Operations

```bash
./deploy/server-preflight.sh
./deploy/server-update.sh
./deploy/server-down.sh

docker compose --env-file .env -f docker-compose.server.yml logs -f cdm-backend
docker compose --env-file .env -f docker-compose.server.yml logs -f cdm-frontend
docker compose --env-file .env -f docker-compose.server.yml pull
docker compose --env-file .env -f docker-compose.server.yml up -d --build
docker compose --env-file .env -f docker-compose.server.yml down
```

For a `4C4G` server, use:

```bash
./deploy/server-update-4c4g.sh
./deploy/server-down-4c4g.sh
./deploy/server-status-4c4g.sh

docker compose --env-file .env -f docker-compose.server-4c4g.yml logs -f cdm-backend
docker compose --env-file .env -f docker-compose.server-4c4g.yml logs -f cdm-frontend
docker compose --env-file .env -f docker-compose.server-4c4g.yml up -d --build
docker compose --env-file .env -f docker-compose.server-4c4g.yml down
```

## 9. Backups

Back up at least:

- MySQL data volume
- `./data/mongo`
- `./data/qdrant`
- `./data/uploads`
- the `.env` file

## 10. Go-live checklist

Before exposing the service publicly, walk through:

- `deploy/CHECKLIST.md`
- For public-IP mode on `4C4G`, also walk through `deploy/CHECKLIST_IP_4C4G.md`

## Notes

- The first administrator account is created from `.env` on first initialization only.
- `AUTH_JWT_SECRET` must be replaced before any public deployment. Do not keep the template value.
- Frontend requests, file uploads, and WebSocket traffic all go through the frontend container, then proxy to the backend.
- Current backend CORS is permissive. For a stricter production posture, restrict it to your real domain before launch.
- `cdm-backend` and `cdm-frontend` now include container health checks in the server Compose file, so startup ordering is more predictable.
