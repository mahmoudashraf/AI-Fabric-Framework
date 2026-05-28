# Provider-neutral Partner UI image.
#
# Build context: repo root
FROM node:20-alpine AS build

WORKDIR /workspace

COPY Platfrom/partner-ui/package.json Platfrom/partner-ui/package-lock.json ./Platfrom/partner-ui/

WORKDIR /workspace/Platfrom/partner-ui

RUN npm ci

COPY Platfrom/partner-ui ./

RUN npm run build

FROM node:20-alpine

WORKDIR /app

ENV NODE_ENV=production

COPY --from=build /workspace/Platfrom/partner-ui/dist /app/dist
COPY Platfrom/partner-ui/deploy/container/server.mjs /app/server.mjs

EXPOSE 3000

ENTRYPOINT ["node", "/app/server.mjs"]
