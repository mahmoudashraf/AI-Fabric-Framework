# Neutral container deployment for Coolify or any Docker-compatible host.
FROM node:20-alpine

WORKDIR /app
ENV NODE_ENV=production
ENV PORT=4177

COPY Real_Apps/loomai-landing-site/package.json ./
COPY Real_Apps/loomai-landing-site/server.mjs ./
COPY Real_Apps/loomai-landing-site/public ./public
COPY Real_Apps/loomai-landing-site/scripts ./scripts

RUN apk add --no-cache curl \
  && addgroup -S nodeapp && adduser -S nodeapp -G nodeapp \
  && mkdir -p /tmp/loomai \
  && chown -R nodeapp:nodeapp /app /tmp/loomai

USER nodeapp
EXPOSE 4177
CMD ["node", "server.mjs"]
