# Production image for the Loom AI Labs public site.
# Coolify build context: repository root
FROM node:22-alpine AS build

WORKDIR /workspace/Platfrom/loomai-site

COPY Platfrom/loomai-site/package.json Platfrom/loomai-site/package-lock.json ./
RUN npm ci

COPY Platfrom/loomai-site ./
RUN npm run check && npm run build && npm run smoke

FROM node:22-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

COPY --from=build /workspace/Platfrom/loomai-site/dist ./dist
COPY --from=build /workspace/Platfrom/loomai-site/deploy/container/server.mjs ./server.mjs

RUN addgroup -S loomsite \
    && adduser -S loomsite -G loomsite \
    && chown -R loomsite:loomsite /app

USER loomsite

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:3000/health >/dev/null || exit 1

ENTRYPOINT ["node", "/app/server.mjs"]
