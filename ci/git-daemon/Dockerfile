# Serveur git en lecture seule pour le Jenkins local (phase 11) : git daemon, protocole git://
FROM alpine:3.22
RUN apk add --no-cache git git-daemon
EXPOSE 9418
ENTRYPOINT ["git", "daemon", "--reuseaddr", "--export-all", "--base-path=/git", "--informative-errors", "/git"]
