#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$project_root/backups/$timestamp"
compose=(docker compose -f "$project_root/compose.prod.yml" --env-file "$project_root/.env")

mkdir -p "$backup_dir"

restart_server() {
  "${compose[@]}" start server >/dev/null
}
trap restart_server EXIT

# H2 file backups must be taken while its only writer is stopped.
server_container="$("${compose[@]}" ps -q server)"
db_volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/medassist/db"}}{{.Name}}{{end}}{{end}}' "$server_container")"
recordings_volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/medassist/recordings"}}{{.Name}}{{end}}{{end}}' "$server_container")"
if [[ -z "$db_volume" || -z "$recordings_volume" ]]; then
  echo "Could not resolve the server volumes." >&2
  exit 1
fi
"${compose[@]}" stop -t 45 server
docker run --rm \
  -v "$db_volume:/source:ro" \
  -v "$backup_dir:/backup" \
  alpine:3.21 tar -C /source -czf /backup/server-db.tar.gz .
docker run --rm \
  -v "$recordings_volume:/source:ro" \
  -v "$backup_dir:/backup" \
  alpine:3.21 tar -C /source -czf /backup/recordings.tar.gz .

restart_server
trap - EXIT

(
  cd "$backup_dir"
  sha256sum server-db.tar.gz recordings.tar.gz > SHA256SUMS
)

echo "Backup completed: $backup_dir"
