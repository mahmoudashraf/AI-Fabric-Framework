# Provider-neutral Platform UI image.
#
# Build context: repo root
FROM node:20-alpine AS build

WORKDIR /workspace

COPY Platfrom/ui/package.json Platfrom/ui/package-lock.json ./Platfrom/ui/

WORKDIR /workspace/Platfrom/ui

RUN npm ci

COPY Platfrom/ui ./

RUN npm run build

FROM node:20-alpine

WORKDIR /app

ENV NODE_ENV=production

COPY --from=build /workspace/Platfrom/ui/dist /app/dist
COPY Platfrom/ui/deploy/container/server.mjs /app/server.mjs

EXPOSE 3000

ENTRYPOINT ["node", "/app/server.mjs"]
