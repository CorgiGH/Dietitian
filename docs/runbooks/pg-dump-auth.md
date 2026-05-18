# Runbook — Postgres dump auth (BackupCron + manual pg_dump)

**Source of truth:** Plan-3 Task 35 + post-impl council day-1 prod-readiness item.

**Symptom:** `pg_dump` against the production `dietician` database needs to authenticate without exposing the app-user password in shell history, environment dumps, or rotated logs. Cron + ad-hoc operator dumps share the same auth surface.

**Key invariant:** the app-user password lives ONLY in `/run/dietician-keys/db.passphrase` (tmpfs, mode 0600, dietician sys-user owned). No `.pgpass` file on disk, no `PGPASSWORD` env-var written to shell rc files, no plaintext in `/etc/dietician/env`.

---

## How the BackupCron authenticates

`BackupCron` (Plan-3 Task 33, in-JVM per RC4) runs inside the backend process and uses the same HikariCP-pooled `DataSource` as request handlers. The connection inherits the app-user password loaded from tmpfs at startup — no fresh auth needed. To take a dump, BackupCron shells out to `pg_dump` over a Unix socket (peer-auth) under the `postgres` superuser via `sudo`, NOT the app user, so the dump captures full schema + grants:

```
sudo -n -u postgres pg_dump --format=custom --no-owner --no-acl --dbname=dietician
  | zstd -19 -T0
  | rclone rcat onedrive-crypt:dietician-backups/$(date -u +%Y-%m-%dT%H%M%SZ).dump.zst
```

The `sudo -n` (non-interactive) is allowed for the `dietician` sys-user via `/etc/sudoers.d/dietician-backup`:

```
dietician ALL=(postgres) NOPASSWD: /usr/bin/pg_dump
```

This sudo entry is the ONLY thing on the box that lets the dietician user act as postgres — no shell access, no other commands. Verify after install:

```bash
sudo -u dietician sudo -ln
# Expected: postgres pg_dump entry only.
```

---

## How an operator runs an ad-hoc dump

For incident dumps (pre-migration safety net, debugging) the same path works from an SSH session:

```bash
sudo -u postgres pg_dump --format=custom --dbname=dietician --file=/tmp/manual-$(date -u +%Y-%m-%dT%H%M%SZ).dump
zstd -19 -T0 /tmp/manual-*.dump
# Optional upload:
rclone copy /tmp/manual-*.dump.zst onedrive-crypt:dietician-backups/manual/
```

Peer-auth means no password needed when running as `postgres`. The dump file lives in `/tmp` (cleared on reboot — DO NOT move to `/root` or anywhere persistent without zstd + crypt). Delete the local copy after upload:

```bash
shred -u /tmp/manual-*.dump /tmp/manual-*.dump.zst
```

---

## Why NOT `.pgpass`

The standard `~/.pgpass` flow stores the app-user password at mode 0600 in the home dir. That defeats the "password never touches disk" invariant — disk snapshots, image clones, and `tar` backups all capture it. The peer-auth + sudo path keeps the password tmpfs-only and the dump-permission scope-limited to a single command.

If the operator absolutely must run `pg_dump` as the `dietician_app` user (e.g. testing app-user grants), source the tmpfs password into the shell for one command:

```bash
PGPASSWORD="$(sudo cat /run/dietician-keys/db.passphrase)" pg_dump --host=127.0.0.1 --username=dietician_app --dbname=dietician --file=/tmp/app-user.dump
# PGPASSWORD lives only in this shell's env — gone when the shell exits.
unset PGPASSWORD
```

Bash will keep `PGPASSWORD` in history if `set -o history` is on. Run with leading space (`HISTCONTROL=ignorespace`) or `set +o history` first.

---

## Failure modes

**`sudo: a password is required`:**
- The `/etc/sudoers.d/dietician-backup` file is missing or has wrong syntax. Re-install:
  ```bash
  sudo visudo -f /etc/sudoers.d/dietician-backup
  # File contents (single line):
  dietician ALL=(postgres) NOPASSWD: /usr/bin/pg_dump
  ```
  Visudo rejects bad syntax on save — won't leave a broken file.

**`pg_dump: server version mismatch`:**
- Client `pg_dump` is from an older Postgres version than the server. Use `/usr/lib/postgresql/16/bin/pg_dump` explicitly.

**`rclone: directory not found`:**
- The `onedrive-crypt:` remote is missing or unconfigured. See `docs/runbooks/rclone-onedrive-crypt-setup.md`.

**Backup file present but unreadable later:**
- The crypt password may have rotated between write + read. The crypt password lives in `~root/.config/rclone/rclone.conf` (encrypted via rclone's config-pass) — recovery requires the rclone config password from Victor's password manager. See `restore.md` failure-modes section.

---

## Drill

Every 90 days run:
```bash
sudo -u dietician sudo -n -u postgres pg_dump --schema-only --dbname=dietician > /tmp/schema-only.sql
test -s /tmp/schema-only.sql && echo OK || echo FAIL
shred -u /tmp/schema-only.sql
```

A schema-only dump is fast (~1s) and proves the full auth chain works end-to-end without touching real row data.

---

## Council references
- Plan-3 post-impl council 1779073963: day-1 prod-readiness item — auth path was implicit in the code, runbook makes it explicit for operators.
- Plan-3 RC4: in-JVM cron decision — backup runs inside the backend, not via systemd timer, so the dump-auth path is process-internal.
- §A18 (binding amendment): backup destination is UAIC OneDrive 1TB via rclone crypt, NOT Backblaze B2.
