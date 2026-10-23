# Medplum MCP Sidecar

This sidecar exposes Medplum Cloud and local CDM sync operations as MCP tools.

It supports two transports:

- `stdio` for local desktop/CLI MCP clients
- `http` for Docker and remote MCP access

## Environment

The bridge reads these environment variables:

- `MEDPLUM_ENABLED`
- `MEDPLUM_BASE_URL`
- `MEDPLUM_CLIENT_ID`
- `MEDPLUM_CLIENT_SECRET`
- `MEDPLUM_ACCESS_TOKEN`
- `MEDPLUM_PROJECT_ID`
- `MEDPLUM_TIMEOUT_SECONDS`
- `CDM_BACKEND_BASE_URL` or `MEDPLUM_CDM_BACKEND_BASE_URL`
- `MCP_TRANSPORT` (`stdio` by default, `http` for remote sidecar mode)
- `HOST` (`0.0.0.0` recommended in Docker HTTP mode)
- `PORT` (`8811` by default in HTTP mode)
- `MCP_HTTP_PATH` (`/mcp` by default)

`CDM_BACKEND_BASE_URL` defaults to `http://127.0.0.1:8080`.

## Install

```bash
cd third_party/medplum-mcp
npm install
```

Node.js `18+` is required.

## Run

```bash
node cdm-bridge.mjs
```

Run in HTTP mode:

```bash
MCP_TRANSPORT=http PORT=8811 node cdm-bridge.mjs
```

## Tools

- `medplum_status`
- `sync_local_patient_to_medplum`
- `sync_all_local_patients_to_medplum`
- `search_patient_by_local_id`
- `search_patients_by_name`
- `get_latest_observations`
- `fhir_read_resource`
- `fhir_search_resource`

## Notes

- Read tools access Medplum Cloud directly.
- Sync tools call the local Spring Boot backend so local MySQL remains the source of truth.
- HTTP mode exposes:
  - `GET /health`
  - `POST /mcp`
- The backend already exposes:
  - `GET /api/medplum/status`
  - `POST /api/medplum/patient/{patientId}/sync`
  - `POST /api/medplum/sync-all`
