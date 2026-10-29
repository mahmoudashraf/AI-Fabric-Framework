# Railway-friendly Platform UI image.
#
# Build context: repo root
FROM node:20-alpine AS engage-preview
WORKDIR /preview
COPY max-mode-widget/package.json max-mode-widget/package-lock.json ./
RUN npm ci
COPY max-mode-widget ./
COPY experience-packs/dealership-experience/src /experience-packs/dealership-experience/src
COPY experience-packs/generic-workspace-experience/src /experience-packs/generic-workspace-experience/src
RUN npm run build:preview

FROM node:20-alpine AS build

WORKDIR /workspace

COPY Platfrom/ui/package.json Platfrom/ui/package-lock.json ./Platfrom/ui/

WORKDIR /workspace/Platfrom/ui

RUN npm ci

COPY Platfrom/ui ./
COPY experience-packs/dealership-experience/src /workspace/experience-packs/dealership-experience/src
COPY experience-packs/generic-workspace-experience/src /workspace/experience-packs/generic-workspace-experience/src
COPY max-mode-widget/src/preview/configuration.ts /workspace/max-mode-widget/src/preview/configuration.ts
COPY --from=engage-preview /preview/dist/engage-preview.iife.js ./public/engage-preview.iife.js

RUN npm run build

FROM node:20-alpine

WORKDIR /app

RUN apk add --no-cache curl

ENV NODE_ENV=production

COPY --from=build /workspace/Platfrom/ui/dist /app/dist
COPY Platfrom/ui/deploy/railway/server.mjs /app/server.mjs

EXPOSE 3000

ENTRYPOINT ["node", "/app/server.mjs"]
