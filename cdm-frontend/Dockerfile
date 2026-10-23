# Frontend Dockerfile
ARG NODE_IMAGE=docker.m.daocloud.io/library/node:20-alpine
FROM ${NODE_IMAGE} AS builder
WORKDIR /app
ENV NODE_OPTIONS=--max-old-space-size=2048
COPY package*.json ./
RUN if [ -f package-lock.json ]; then npm ci --registry=https://registry.npmmirror.com; else npm install --registry=https://registry.npmmirror.com; fi
COPY . .
RUN npm run build

ARG NODE_IMAGE=docker.m.daocloud.io/library/node:20-alpine
FROM ${NODE_IMAGE}
WORKDIR /app
ENV PORT=80
COPY --from=builder /app/dist ./dist
COPY serve-static.cjs ./serve-static.cjs
EXPOSE 80
CMD ["node", "serve-static.cjs"]
