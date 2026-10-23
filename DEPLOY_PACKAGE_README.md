# Docker Deployment Package

This package contains only the files required for the full Docker deployment.

## Included stack

- MySQL
- MongoDB
- Qdrant
- Redis
- Spring Boot backend
- Frontend
- Medplum MCP bridge
- medical.json auto-import scripts

## Quick start

1. Upload and unzip this package on the server.
2. Review the bundled .env file and keep it secure.
3. Run:

   chmod +x deploy/server-preflight.sh deploy/server-up.sh deploy/server-update.sh deploy/server-down.sh
   ./deploy/server-preflight.sh
   ./deploy/server-up.sh

4. Or use Docker Compose directly:

   docker compose --env-file .env -f docker-compose.server.yml up -d --build

## Notes

- The package already includes the current local .env file.
- Keep .env private. It may contain production-like secrets.
- Runtime data is written into ./data after startup.