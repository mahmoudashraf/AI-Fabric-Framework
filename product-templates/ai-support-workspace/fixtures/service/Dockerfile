FROM node:22-alpine
WORKDIR /app
COPY --chown=node:node fixtures/acme-read-observations.json ./fixtures/acme-read-observations.json
COPY --chown=node:node fixtures/service/server.mjs ./fixtures/service/server.mjs
COPY --chown=node:node documents/acme-cloud/ ./documents/acme-cloud/
USER node
ENV PORT=8118
EXPOSE 8118
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD node -e "fetch('http://127.0.0.1:'+process.env.PORT+'/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "fixtures/service/server.mjs"]
