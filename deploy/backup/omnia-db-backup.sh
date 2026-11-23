#!/usr/bin/env bash
# Daily verified MySQL backup for the Omnia production server, with an optional restore test.
#
#   omnia-db-backup.sh                 # dump, verify, rotate
#   omnia-db-backup.sh --restore-test  # also restore the newest dump into a throwaway MySQL container
#
# Runs as the deploy user (needs docker). Nothing here writes to the live database.
# Install: see deploy/backup/README.md (cron + systemd examples). Not installed automatically.
set -euo pipefail

MYSQL_CONTAINER=${MYSQL_CONTAINER:-omnia-mysql}
BK=${BACKUP_DIR:-/home/deploy/backups/daily}
KEEP_DAYS=${KEEP_DAYS:-14}
MIN_FREE_MB=${MIN_FREE_MB:-2048}
RESTORE_IMAGE=${RESTORE_IMAGE:-mysql:8.4}

die()  { echo "STOP: $*" >&2; exit 1; }
info() { echo "== $(date -u +%FT%TZ) $*"; }

RESTORE_TEST=false
[ "${1:-}" = "--restore-test" ] && RESTORE_TEST=true

umask 077
mkdir -p "$BK"
exec 9>"$BK/.lock"
flock -n 9 || die "another backup is running"

FREE_MB=$(df -Pm "$BK" | awk 'NR==2 {print $4}')
[ "$FREE_MB" -ge "$MIN_FREE_MB" ] || die "only ${FREE_MB} MB free in $BK (need $MIN_FREE_MB)"
[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null)" = "true" ] || die "$MYSQL_CONTAINER is not running"

db() { docker exec -i "$MYSQL_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot "$MYSQL_DATABASE"'; }

TS=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$BK/omnia-$TS.sql.gz"
TMP="$OUT.part"
trap 'rm -f "$TMP"' EXIT

info "mysqldump -> $OUT"
docker exec "$MYSQL_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
  | gzip -6 > "$TMP"

info "verify"
gzip -t "$TMP" || die "not a valid gzip file"
LAST=$(gzip -dc "$TMP" | tail -n 1)
case "$LAST" in "-- Dump completed"*) ;; *) die "dump does not end with '-- Dump completed'";; esac
TABLES=$(gzip -dc "$TMP" | grep -c '^CREATE TABLE ' || true)
EXPECTED=$(echo "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';" | db)
[ "$TABLES" = "$EXPECTED" ] || die "dump has $TABLES tables, database has $EXPECTED"
LATEST=$(echo "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;" | db)

mv "$TMP" "$OUT"
( cd "$BK" && sha256sum "$(basename "$OUT")" > "$(basename "$OUT").sha256" )
echo "ok: $TABLES tables, Flyway V$LATEST, $(du -h "$OUT" | cut -f1)"

info "rotate (keep $KEEP_DAYS days)"
find "$BK" -maxdepth 1 -type f \( -name 'omnia-*.sql.gz' -o -name 'omnia-*.sql.gz.sha256' \) -mtime +"$KEEP_DAYS" -print -delete

if $RESTORE_TEST; then
  info "restore test in a throwaway container ($RESTORE_IMAGE, no network, no ports)"
  NAME="omnia-restore-test-$TS"
  PW=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')
  cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -f "$TMP"; }
  trap cleanup EXIT
  docker run -d --name "$NAME" --network none -e MYSQL_ROOT_PASSWORD="$PW" -e MYSQL_DATABASE=restore "$RESTORE_IMAGE" >/dev/null
  for _ in $(seq 1 60); do
    docker exec -e MYSQL_PWD="$PW" "$NAME" mysql -uroot -e 'SELECT 1' restore >/dev/null 2>&1 && break
    sleep 2
  done
  gzip -dc "$OUT" | docker exec -i -e MYSQL_PWD="$PW" "$NAME" mysql -uroot restore || die "restore failed"
  rt() { docker exec -e MYSQL_PWD="$PW" "$NAME" mysql -N -B -uroot restore -e "$1"; }
  R_TABLES=$(rt "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'restore' AND table_type = 'BASE TABLE';")
  R_LATEST=$(rt "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;")
  [ "$R_TABLES" = "$EXPECTED" ] || die "restored $R_TABLES tables, expected $EXPECTED"
  [ "$R_LATEST" = "$LATEST" ] || die "restored Flyway V$R_LATEST, expected V$LATEST"
  for t in users products orders; do
    LIVE=$(echo "SELECT COUNT(*) FROM $t;" | db)
    REST=$(rt "SELECT COUNT(*) FROM $t;")
    # Rows can be added between the dump and this check, never removed in bulk.
    [ "$REST" -le "$LIVE" ] && [ "$REST" -ge $(( LIVE * 9 / 10 )) ] || die "$t: restored $REST rows, live has $LIVE"
  done
  echo "restore test ok: $R_TABLES tables, Flyway V$R_LATEST"
  echo "$TS ok" > "$BK/LAST_RESTORE_TEST"
fi

echo "$TS $(basename "$OUT")" > "$BK/LAST_BACKUP"
info "done"
