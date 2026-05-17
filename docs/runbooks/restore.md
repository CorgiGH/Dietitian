# Runbook X — Full VPS disaster restore from backup

**Symptom:** VPS disk dies / VPS provider goes down / accidental `rm -rf`. Need to restore Dietician from backups.

**Prerequisites:**
- Backblaze B2 account credentials (separate from VPS, stored in password manager)
- B2 bucket name + app key
- DNS / Tailscale auth keys
- New VPS (ByteHosting or alternative) with Ubuntu 22.04+

**Restore steps:**

1. **Provision new VPS:**
   ```bash
   # SSH in as root, basic hardening
   apt update && apt upgrade -y
   apt install -y curl git docker.io docker-compose-plugin rclone postgresql-16 postgresql-16-pgvector
   systemctl enable docker postgresql
   ```

2. **Install Tailscale + join mesh:**
   ```bash
   curl -fsSL https://tailscale.com/install.sh | sh
   tailscale up --auth-key=<TS_AUTH_KEY> --hostname=dietician-vps-new
   # Apply tag:dietician-backend in Tailscale admin
   ```

3. **Restore credentials:**
   ```bash
   # Set up rclone with B2 creds (interactive)
   rclone config
   # config name: b2
   # provider: Backblaze B2
   # account: <B2_KEY_ID>
   # key: <B2_APP_KEY>
   ```

4. **Pull backup:**
   ```bash
   mkdir -p /restore/{db,wiki,raw,etc}
   rclone copy b2:dietician-backups/db/latest.dump.zst /restore/db/
   rclone copy b2:dietician-backups/raw/ /restore/raw/
   rclone copy b2:dietician-backups/etc/ /restore/etc/
   ```

5. **Restore Postgres:**
   ```bash
   sudo -u postgres createuser dietician
   sudo -u postgres createdb -O dietician dietician
   sudo -u postgres psql -d dietician -c "CREATE EXTENSION vector;"
   zstd -d /restore/db/latest.dump.zst -o /restore/db/latest.dump
   sudo -u postgres pg_restore -d dietician /restore/db/latest.dump
   ```

6. **Restore wiki:**
   ```bash
   cd /opt/dietician
   git clone <wiki-remote-url> wiki
   # Or: rclone copy b2:dietician-backups/wiki/ /opt/dietician/wiki/
   ```

7. **Restore raw assets:**
   ```bash
   rsync -av /restore/raw/ /opt/dietician/storage/
   ```

8. **Restore systemd units + config:**
   ```bash
   cp /restore/etc/dietician/* /etc/dietician/
   systemctl daemon-reload
   ```

9. **Restore Docker services:**
   ```bash
   cd /etc/dietician/docker
   docker compose up -d  # ntfy + grobid
   ```

10. **Deploy dietician-backend:**
    ```bash
    # From dev laptop:
    ./gradlew :server:buildFatJar
    scp server/build/libs/dietician-server.jar root@<new-vps>:/opt/dietician/lib/
    ssh root@<new-vps> systemctl restart dietician-backend
    ```

11. **Verify:**
    - `curl http://<tailscale-ip>:8081/health` returns 200
    - Phone + desktop: open app, `/diag` shows all green
    - Trigger a test sync from each client; verify writes land in canonical Postgres

12. **DNS / Tailscale ACL:**
    - Update Tailscale ACL tag for new VPS hostname if changed
    - Old VPS Tailscale node should be removed from mesh in admin UI

**Estimated restore wall-time:** depends on backup size. Postgres dump ~few hundred MB compressed → minutes. Raw assets (flyers/receipts/llm-raw) could be GBs.

**Test the restore quarterly** so it doesn't break silently.
