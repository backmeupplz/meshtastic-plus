FROM oven/bun:1.4.5-alpine@sha256:da816670775f4c2b4e990b6b1847cf5540dcdc721a42033dd331ce7a88ba6a3b
WORKDIR /app
COPY server.ts .
USER bun
EXPOSE 3000
CMD ["bun", "server.ts"]
