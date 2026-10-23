import express from "express";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";

const nodeMajorVersion = Number.parseInt(process.versions.node.split(".")[0] ?? "0", 10);
if (nodeMajorVersion < 18 || typeof globalThis.fetch !== "function") {
  throw new Error("cdm-medplum-mcp requires Node.js 18+ with native fetch support.");
}

const PATIENT_IDENTIFIER_SYSTEM = "https://cdm.local/fhir/identifier/patient-id";
const DEFAULT_MEDPLUM_BASE_URL = "https://api.medplum.com";
const DEFAULT_BACKEND_BASE_URL = "http://127.0.0.1:8080";
const DEFAULT_HTTP_PATH = "/mcp";
const transportMode = (process.env.MCP_TRANSPORT ?? "stdio").trim().toLowerCase();
const httpHost = (process.env.HOST ?? "0.0.0.0").trim() || "0.0.0.0";
const httpPort = Math.max(Number.parseInt(process.env.PORT ?? "8811", 10) || 8811, 1);
const httpPath = normalizeHttpPath(process.env.MCP_HTTP_PATH ?? DEFAULT_HTTP_PATH);
const DEFAULT_TIMEOUT_MS = Math.max(
  Number.parseInt(process.env.MEDPLUM_TIMEOUT_SECONDS ?? "45", 10) * 1000,
  5000,
);

const config = {
  enabled: (process.env.MEDPLUM_ENABLED ?? "false").toLowerCase() === "true",
  medplumBaseUrl: normalizeBaseUrl(process.env.MEDPLUM_BASE_URL, DEFAULT_MEDPLUM_BASE_URL),
  backendBaseUrl: normalizeBaseUrl(
    process.env.CDM_BACKEND_BASE_URL ?? process.env.MEDPLUM_CDM_BACKEND_BASE_URL,
    DEFAULT_BACKEND_BASE_URL,
  ),
  clientId: process.env.MEDPLUM_CLIENT_ID?.trim() ?? "",
  clientSecret: process.env.MEDPLUM_CLIENT_SECRET?.trim() ?? "",
  accessToken: process.env.MEDPLUM_ACCESS_TOKEN?.trim() ?? "",
  projectId: process.env.MEDPLUM_PROJECT_ID?.trim() ?? "",
  timeoutMs: DEFAULT_TIMEOUT_MS,
};

let cachedToken = null;
let cachedTokenExpiresAt = 0;

function normalizeBaseUrl(value, fallback) {
  const target = (value ?? fallback ?? "").trim();
  return target.replace(/\/+$/, "");
}

function normalizeHttpPath(value) {
  const normalized = (value ?? DEFAULT_HTTP_PATH).trim();
  if (!normalized) {
    return DEFAULT_HTTP_PATH;
  }
  return normalized.startsWith("/") ? normalized : `/${normalized}`;
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function buildResult(title, data) {
  return {
    content: [
      {
        type: "text",
        text: `${title}\n${JSON.stringify(data, null, 2)}`,
      },
    ],
  };
}

async function parseResponse(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function requestJson(url, init = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);
  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal,
      headers: {
        Accept: "application/json, application/fhir+json",
        ...(init.headers ?? {}),
      },
    });
    const body = await parseResponse(response);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${typeof body === "string" ? body : JSON.stringify(body)}`);
    }
    return body;
  } finally {
    clearTimeout(timeout);
  }
}

async function getAccessToken() {
  if (hasText(config.accessToken)) {
    return config.accessToken;
  }
  if (!hasText(config.clientId) || !hasText(config.clientSecret)) {
    throw new Error("Medplum credentials are not configured.");
  }
  if (cachedToken && Date.now() < cachedTokenExpiresAt - 30000) {
    return cachedToken;
  }

  const body = new URLSearchParams({ grant_type: "client_credentials" });
  const basic = Buffer.from(`${config.clientId}:${config.clientSecret}`).toString("base64");
  const tokenResponse = await requestJson(`${config.medplumBaseUrl}/oauth2/token`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  if (!tokenResponse?.access_token) {
    throw new Error("Medplum token response did not include access_token.");
  }

  cachedToken = tokenResponse.access_token;
  cachedTokenExpiresAt = Date.now() + (Number(tokenResponse.expires_in ?? 3600) * 1000);
  return cachedToken;
}

async function medplumRequest(path, init = {}) {
  const token = await getAccessToken();
  return requestJson(`${config.medplumBaseUrl}/fhir/R4/${path.replace(/^\/+/, "")}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init.headers ?? {}),
    },
  });
}

async function backendRequest(path, init = {}) {
  return requestJson(`${config.backendBaseUrl}${path.startsWith("/") ? path : `/${path}`}`, init);
}

async function findPatientByLocalId(localPatientId) {
  const identifier = encodeURIComponent(`${PATIENT_IDENTIFIER_SYSTEM}|${localPatientId}`);
  const bundle = await medplumRequest(`Patient?identifier=${identifier}&_count=1`);
  const entry = bundle?.entry?.[0]?.resource;
  if (!entry) {
    return null;
  }
  return entry;
}

async function resolvePatientId({ medplumPatientId, localPatientId }) {
  if (hasText(medplumPatientId)) {
    return medplumPatientId.trim();
  }
  if (!hasText(String(localPatientId ?? ""))) {
    throw new Error("You must provide medplumPatientId or localPatientId.");
  }
  const patient = await findPatientByLocalId(localPatientId);
  if (!patient?.id) {
    throw new Error(`Patient with local id ${localPatientId} was not found in Medplum.`);
  }
  return patient.id;
}

function registerTools(server) {
  server.tool(
    "medplum_status",
    "Inspect Medplum connectivity and the local backend sync status.",
    {},
    async () => {
      const backendStatus = await backendRequest("/api/medplum/status?probe=true");
      return buildResult("Medplum status", {
        ...backendStatus,
        mcpBridge: {
          enabled: config.enabled,
          transportMode,
          medplumBaseUrl: config.medplumBaseUrl,
          backendBaseUrl: config.backendBaseUrl,
          hasClientId: hasText(config.clientId),
          hasClientSecret: hasText(config.clientSecret),
          hasAccessToken: hasText(config.accessToken),
          httpPath,
        },
      });
    },
  );

  server.tool(
    "sync_local_patient_to_medplum",
    "Trigger the local CDM backend to sync one patient and optional observations to Medplum.",
    {
      patientId: z.number().int().positive(),
      includeObservations: z.boolean().optional().default(true),
    },
    async ({ patientId, includeObservations }) => {
      const result = await backendRequest(
        `/api/medplum/patient/${patientId}/sync?includeObservations=${includeObservations ? "true" : "false"}`,
        { method: "POST" },
      );
      return buildResult("Patient sync result", result);
    },
  );

  server.tool(
    "sync_all_local_patients_to_medplum",
    "Trigger the local CDM backend to sync all local patients to Medplum.",
    {
      includeObservations: z.boolean().optional().default(true),
      continueOnError: z.boolean().optional().default(true),
    },
    async ({ includeObservations, continueOnError }) => {
      const result = await backendRequest(
        `/api/medplum/sync-all?includeObservations=${includeObservations ? "true" : "false"}&continueOnError=${continueOnError ? "true" : "false"}`,
        { method: "POST" },
      );
      return buildResult("Bulk sync result", result);
    },
  );

  server.tool(
    "search_patient_by_local_id",
    "Find a Medplum Patient resource using the local CDM patient id identifier mapping.",
    {
      localPatientId: z.number().int().positive(),
    },
    async ({ localPatientId }) => {
      const patient = await findPatientByLocalId(localPatientId);
      return buildResult("Patient search result", {
        found: Boolean(patient),
        patient,
      });
    },
  );

  server.tool(
    "search_patients_by_name",
    "Search Medplum Patient resources by name.",
    {
      name: z.string().min(1),
      count: z.number().int().positive().max(20).optional().default(5),
    },
    async ({ name, count }) => {
      const bundle = await medplumRequest(`Patient?name=${encodeURIComponent(name)}&_count=${count}`);
      return buildResult("Patient name search", bundle);
    },
  );

  server.tool(
    "get_latest_observations",
    "Get the latest Medplum Observation resources for a patient.",
    {
      medplumPatientId: z.string().min(1).optional(),
      localPatientId: z.number().int().positive().optional(),
      count: z.number().int().positive().max(20).optional().default(10),
    },
    async ({ medplumPatientId, localPatientId, count }) => {
      const patientId = await resolvePatientId({ medplumPatientId, localPatientId });
      const bundle = await medplumRequest(
        `Observation?subject=${encodeURIComponent(`Patient/${patientId}`)}&_sort=-date&_count=${count}`,
      );
      return buildResult("Latest observations", bundle);
    },
  );

  server.tool(
    "fhir_read_resource",
    "Read a specific Medplum FHIR resource by resource type and id.",
    {
      resourceType: z.string().min(1),
      id: z.string().min(1),
    },
    async ({ resourceType, id }) => {
      const resource = await medplumRequest(`${encodeURIComponent(resourceType)}/${encodeURIComponent(id)}`);
      return buildResult("FHIR resource", resource);
    },
  );

  server.tool(
    "fhir_search_resource",
    "Run a read-only Medplum FHIR search with a raw query string.",
    {
      resourceType: z.string().min(1),
      query: z.string().optional().default(""),
    },
    async ({ resourceType, query }) => {
      const suffix = query.trim() ? `?${query.replace(/^\?+/, "")}` : "";
      const bundle = await medplumRequest(`${encodeURIComponent(resourceType)}${suffix}`);
      return buildResult("FHIR search result", bundle);
    },
  );
}

function createBridgeServer() {
  const server = new McpServer({
    name: "cdm-medplum-mcp",
    version: "0.2.0",
  });
  registerTools(server);
  return server;
}

async function startStdioMode() {
  const server = createBridgeServer();
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

async function startHttpMode() {
  const app = express();
  app.use(express.json({ limit: "2mb" }));

  app.get("/health", (_req, res) => {
    res.json({
      ok: true,
      name: "cdm-medplum-mcp",
      transport: "http",
      httpPath,
      backendBaseUrl: config.backendBaseUrl,
      medplumBaseUrl: config.medplumBaseUrl,
      medplumEnabled: config.enabled,
    });
  });

  app.post(httpPath, async (req, res) => {
    let server;
    let transport;
    try {
      server = createBridgeServer();
      transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: undefined,
        enableJsonResponse: true,
      });
      await server.connect(transport);
      await transport.handleRequest(req, res, req.body);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error("[cdm-medplum-mcp] HTTP transport error:", message);
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: "2.0",
          error: {
            code: -32603,
            message,
          },
          id: req.body?.id ?? null,
        });
      }
    } finally {
      if (transport && typeof transport.close === "function") {
        await transport.close().catch(() => undefined);
      }
      if (server && typeof server.close === "function") {
        await server.close().catch(() => undefined);
      }
    }
  });

  app.all(httpPath, (_req, res) => {
    res.status(405).json({
      error: "Use POST for MCP requests.",
      path: httpPath,
    });
  });

  app.listen(httpPort, httpHost, () => {
    console.error(`[cdm-medplum-mcp] HTTP transport listening on http://${httpHost}:${httpPort}${httpPath}`);
  });
}

if (transportMode === "http") {
  await startHttpMode();
} else {
  await startStdioMode();
}
