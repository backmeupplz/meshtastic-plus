FROM oven/bun:1.4.4-alpine
WORKDIR /app
COPY server.ts .
USER bun
EXPOSE 3000
CMD ["bun", "server.ts"]
