FROM node:22-bookworm-slim AS build

WORKDIR /workspace
COPY workspace-apps/standalone-ai-workspace/package.json workspace-apps/standalone-ai-workspace/package-lock.json ./
RUN npm ci
COPY workspace-apps/standalone-ai-workspace ./
RUN npm run typecheck && npm run build && npm test

FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
COPY workspace-apps/standalone-ai-workspace/package.json workspace-apps/standalone-ai-workspace/package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY workspace-apps/standalone-ai-workspace/server.mjs ./server.mjs
COPY --from=build /workspace/dist ./dist
USER node
EXPOSE 8080
CMD ["node", "server.mjs"]
