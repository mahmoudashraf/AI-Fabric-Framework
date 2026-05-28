# Provider-neutral shared Ollama image.
#
# Build context: repo root
FROM ollama/ollama:latest

COPY ai-fabric-product/ai-fabric-shared-ollama-service/deploy/container/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 11434

ENTRYPOINT ["/entrypoint.sh"]
