# Runbook — rclone OneDrive-crypt setup (UAIC 1TB backup destination)

**Source of truth:** spec §A18 (binding amendment) + Plan-3 Task 35 + Plan-3 post-impl council day-1 prod-readiness item.
**Replaces:** Backblaze B2 backup target (deprecated 2026-05-18 — UAIC OneDrive 1TB confirmed available via student Microsoft 365).

**Symptom:** Fresh VPS install or VPS migration; `BackupCron` (Plan-3 Task 33) cannot upload the nightly `pg_dump` because the `onedrive-crypt:` rclone remote does not exist yet.

**Key invariant:** the backup files written to OneDrive are encrypted client-side. Microsoft (and any UAIC IT staff with OneDrive admin access) MUST NOT be able to read the dumps. `rclone crypt` filename + content encryption is non-negotiable.

---

## Pre-requisites

- SSH into VPS as root (or sudo-capable user).
- Microsoft 365 student account on `*@student.uaic.ro` (Victor's UAIC IdP credentials).
- A web browser on the operator's local machine (for the rclone OAuth flow).
- 5 minutes uninterrupted at the keyboard — the OAuth flow is interactive.

---

## Install rclone

```bash
# Debian/Ubuntu (VPS):
sudo apt-get update && sudo apt-get install -y rclone

# Or pin a specific version (recommended — rclone breaks rarely but config syntax has shifted historically):
curl -fsSL https://rclone.org/install.sh | sudo bash

rclone version
# Expected: >= 1.65 (older versions lacked OneDrive Business chunk-upload tweaks).
```

---

## Step 1 — base OneDrive remote (`onedrive-raw`)

`rclone config` is interactive. The transcript below is what you type:

```bash
sudo rclone config
```

```
n) New remote
name> onedrive-raw
Storage> onedrive                          # autocompletes from "one"
client_id>                                 # leave blank (uses rclone's default)
client_secret>                             # leave blank
region> global                             # 1
Edit advanced config? n
Use web browser to automatically authenticate? y
```

rclone prints a `http://127.0.0.1:53682/auth?...` URL. **On the VPS this fails** because the browser cannot reach localhost — instead use the SSH-port-forward variant:

```bash
# From the operator's local machine BEFORE `rclone config`:
ssh -L 53682:localhost:53682 root@46.247.109.91
# Then run rclone config inside that SSH session.
```

The browser on your local machine now hits the forwarded URL → Microsoft login → consent → "Success" page. Back in the SSH session:

```
Choose drive type> onedrive               # personal/business — pick "onedrive" for UAIC student tenancy
Found 1 drives... use this one? y
y) Yes this is OK
q) Quit config
```

Verify:
```bash
sudo rclone lsd onedrive-raw:
# Expected: a list of OneDrive root folders (Documents, Pictures, etc.).
```

---

## Step 2 — crypt overlay (`onedrive-crypt`)

The crypt remote wraps `onedrive-raw:` and encrypts both filenames and content with operator-supplied passphrases (NOT the Microsoft password).

```bash
sudo rclone config
```

```
n) New remote
name> onedrive-crypt
Storage> crypt                            # autocompletes
remote> onedrive-raw:dietician-backups    # the path on OneDrive where encrypted dumps land
filename_encryption> standard             # encrypts filenames so MS sees random strings
directory_name_encryption> true
password>                                 # paste a strong passphrase (32+ chars). Store in Victor's password manager.
password2>                                # paste a SECOND passphrase for the filename salt. Different from password.
y) Yes this is OK
q) Quit
```

**CRITICAL:** the two passphrases are unrecoverable. Lose them = backups become permanently unreadable. Store both in the password manager AND on the offline-printed recovery card (Plan-3.5 — Victor's master-key-recovery-card.pdf).

---

## Step 3 — encrypt the rclone config itself

Without this step, anyone with root on the VPS can `cat ~/.config/rclone/rclone.conf` and see the OneDrive refresh token + the crypt passphrases. Encrypt the config:

```bash
sudo rclone config
```

```
s) Set configuration password
a) Add Password
password>                                 # third passphrase — protects the config file
y) Confirm
```

Now every rclone command on the VPS prompts for this password OR reads it from `RCLONE_CONFIG_PASS` env-var. The BackupCron loads it the same way it loads the database passphrase — from tmpfs at `/run/dietician-keys/rclone.passphrase`, populated by `dietician-key-unlock.service` at restart (see `restart.md`).

Add the rclone config-password line to the key-unlock script:

```bash
# /etc/dietician/key-unlock.sh — add this stanza:
echo "<rclone_config_passphrase>" > /run/dietician-keys/rclone.passphrase
chmod 0600 /run/dietician-keys/rclone.passphrase
chown dietician:dietician /run/dietician-keys/rclone.passphrase
```

And add to `/etc/systemd/system/dietician-backend.service`:

```ini
[Service]
Environment="RCLONE_CONFIG_PASS_FILE=/run/dietician-keys/rclone.passphrase"
```

(rclone 1.65+ reads `RCLONE_CONFIG_PASS_FILE` natively. On older versions: `Environment="RCLONE_CONFIG_PASS=$(cat /run/dietician-keys/rclone.passphrase)"` won't work — use a wrapper script.)

---

## Step 4 — smoke test

```bash
# As root, with the config password loaded:
export RCLONE_CONFIG_PASS="<paste config passphrase>"

echo "smoke $(date -u +%FT%TZ)" > /tmp/smoke.txt
sudo -E rclone copy /tmp/smoke.txt onedrive-crypt:smoke/
sudo -E rclone ls onedrive-crypt:smoke/
# Expected: "smoke.txt" listed.

# Open the OneDrive web UI in a browser and navigate to dietician-backups/smoke/.
# Expected: a random-string filename (e.g. "k7n3q...="), NOT "smoke.txt" — that proves filename encryption is working.

# Cleanup:
sudo -E rclone delete onedrive-crypt:smoke/
rm /tmp/smoke.txt
unset RCLONE_CONFIG_PASS
```

If the OneDrive web UI shows a plaintext `smoke.txt` filename, **filename encryption is off** — re-run Step 2 with `filename_encryption = standard`.

---

## Step 5 — verify BackupCron picks it up

After the backend starts (`systemctl start dietician-backend`), watch the journal for the next backup emission:

```bash
sudo journalctl -u dietician-backend.service -f | grep -i backup
# Within 24h (or sooner if you set BACKUP_TEST_INTERVAL_MIN=5 in /etc/dietician/env):
# Expected log line: backup_emit ok dest=onedrive-crypt:dietician-backups/2026-05-18T040000Z.dump.zst size=...
```

Confirm in `/health`:

```bash
curl https://dietician-vps.<tailnet>.ts.net/health | jq .last_backup_at
# Expected: an ISO-8601 timestamp within the last 24h after the first cron tick.
```

---

## Failure modes

**Microsoft Conditional Access blocks the OAuth flow:**
- UAIC tenant may enforce device-compliance / MFA-on-every-grant policies that reject rclone's OAuth client. Workaround: from a UAIC-managed device, run `rclone authorize "onedrive"` interactively — paste the resulting token blob into the VPS `rclone config` flow when prompted for `config_token`. See https://rclone.org/onedrive/#getting-your-own-client-id-and-client-secret.

**`HTTP 429 Too Many Requests` during large initial upload:**
- OneDrive Business throttles aggressively on bulk writes. Backup cron emits one ~50MB compressed dump per night — well below throttle. If a full historical re-upload is needed, set `--tpslimit=4 --tpslimit-burst=2`.

**Crypt remote returns "decryption failed" on read:**
- Password drift between the config used to write the file and the config used to read it. Restore the config from the encrypted backup (`rclone config show` against a saved copy). If irrecoverable: the dump file is permanently lost. The DATABASE itself is unaffected — only the backup is gone, so take a fresh one immediately and fix the config.

**Bandwidth saturation during backup:**
- ByteHosting VPS has ~100 Mbps upload. A 50MB dump uploads in ~5s — negligible. If multi-GB dumps appear (corpus ingestion writes too much data), add `--bwlimit=20M` to the cron command to cap at 20 Mbps.

---

## Drill

Every 90 days run the full restore drill from `restore.md` against a throwaway database. That proves the full chain — OneDrive read + crypt decrypt + zstd decompress + pg_restore — works end-to-end. Log the drill outcome in `audit_log` (`action='backup_drill_completed'`).

---

## Council references
- §A18 (binding amendment): OneDrive 1TB supersedes Backblaze B2 as the canonical backup destination.
- Plan-3 RC4: in-JVM cron means the rclone invocation is a process-spawn from the JVM, NOT a systemd timer. Plan-3 Task 33 (`BackupCron.kt`) shells out via `ProcessBuilder`.
- Plan-3 RC11: this runbook + `restore.md` together demonstrate GDPR Art 32 restorability — required evidence.
