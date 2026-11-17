# Build Angular dans node:22 (Node n'est pas installé dans WSL), puis nginx non root.
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
# Phase 11 : Trivy signalait 57 vulnérabilités dans les paquets alpine de l'image de base
# (openssl, musl, zlib…) : mise à jour des paquets, puis retour à l'utilisateur non root.
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/events-management-frontend/browser /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
