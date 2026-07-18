#!/usr/bin/env bash
# Rentle backup: Postgres dump + private/public upload sync. Point cron at this.
# Usage: DATABASE_URL=postgres://user:pass@host:5432/db BACKUP_DIR=/backups ./ops/backup.sh
# Restore: gunzip -c rentle-db-YYYYmmdd-HHMMSS.sql.gz | psql "$DATABASE_URL"
#
# ponytail: plain pg_dump + rsync — no WAL archiving/PITR. Add that only when RPO demands it.
set -euo pipefail

DB_URL="${DATABASE_URL:-postgres://rentle:dev_password@localhost:5433/rentle}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RETAIN_DAYS="${RETAIN_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

echo "[backup] dumping database -> $BACKUP_DIR/rentle-db-$STAMP.sql.gz"
pg_dump "$DB_URL" | gzip > "$BACKUP_DIR/rentle-db-$STAMP.sql.gz"

# Identity documents and deposit proofs live on local disk unless object storage is configured.
# Back them up too — losing them means losing KYC evidence and dispute records.
for dir in private-uploads uploads; do
  if [ -d "$dir" ]; then
    echo "[backup] syncing $dir -> $BACKUP_DIR/$dir"
    rsync -a --delete "$dir/" "$BACKUP_DIR/$dir/"
  fi
done

echo "[backup] pruning dumps older than $RETAIN_DAYS days"
find "$BACKUP_DIR" -name 'rentle-db-*.sql.gz' -mtime +"$RETAIN_DAYS" -delete

echo "[backup] done: $STAMP"
