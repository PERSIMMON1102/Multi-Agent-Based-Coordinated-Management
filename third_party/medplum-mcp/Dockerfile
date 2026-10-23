ARG NODE_IMAGE=docker.m.daocloud.io/library/node:20-alpine
FROM ${NODE_IMAGE}

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

COPY cdm-bridge.mjs ./
COPY README.md ./

ENV MCP_TRANSPORT=http
ENV HOST=0.0.0.0
ENV PORT=8811
ENV MCP_HTTP_PATH=/mcp

EXPOSE 8811

CMD ["node", "cdm-bridge.mjs"]
