# MarketSocial

Spring Boot app with a static frontend, file uploads, and a database-backed backend.

Start here for future chats:
- Read `README.md` first for the current app and deployment overview.
- Then read `PROJECT_MEMORY.md` for the running diary of recent changes, decisions, shortcuts, and user requests that should carry forward.

## Run as a local hosted service

This project now runs against PostgreSQL only. Start it with Docker Compose:

```bash
docker compose up --build
```

That starts:

- the Spring Boot app on port `8080`
- PostgreSQL on port `5432`
- persistent storage in `./postgres-data`
- uploaded media in `./uploads`

Then open `http://localhost:8080`.

## Smoke test

Once the Docker stack is up, run the local smoke test:

```bash
./scripts/docker-smoke.sh
```

It creates a throwaway user, signs in, and confirms `/api/auth/me` returns that account.

## Runtime configuration

The app supports environment variables so the same build can move from your PC to a VPS later:

- `SERVER_ADDRESS` default: `0.0.0.0`
- `SERVER_PORT` default: `8080`
- `DATABASE_URL` default: `jdbc:postgresql://db:5432/marketsocial`
- `DATABASE_USERNAME` default: `marketsocial`
- `DATABASE_PASSWORD` default: `marketsocial`
- `MEDIA_UPLOAD_DIR` default: `./uploads`
- `EMAIL_NOTIFICATIONS_ENABLED` default: `false`
- `EMAIL_FROM` default: empty

## Message notifications

The app now supports:
- unread message badges in the UI
- in-site popups for new messages while the user is online
- optional email alerts when a new message arrives

Email alerts are off until you configure SMTP and a user adds an email address in their profile.

For SMTP, set these environment variables on the app container:

- `EMAIL_NOTIFICATIONS_ENABLED=true`
- `EMAIL_FROM=alerts@your-domain.com`
- `SPRING_MAIL_HOST=...`
- `SPRING_MAIL_PORT=587`
- `SPRING_MAIL_USERNAME=...`
- `SPRING_MAIL_PASSWORD=...`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`

Common providers:
- SendGrid, Mailgun, Amazon SES, Postmark, or your own SMTP host

Quick setup flow:
1. Pick an SMTP provider and get its host, port, username, and password or API key.
2. Put those values into your local shell environment or `.env` file before starting Docker Compose.
3. Set `EMAIL_NOTIFICATIONS_ENABLED=true`.
4. Set `EMAIL_FROM` to a real sender address that your provider allows.
5. Restart the app container.
6. Add an email address in a user's profile and leave "Email me when I get a new message" enabled.

The app will only send a notification email if:
- `EMAIL_NOTIFICATIONS_ENABLED=true` is set in the environment.
- SMTP settings are correctly configured.
- The receiving user has provided a valid email address.
- That user has enabled the "Email me when I get a new message" checkbox.

Note: The UI now enforces that the notification checkbox can only be enabled if an email address is entered.

## HTTPS for live hosting

For public hosting, do not expose plain HTTP from the app container directly. Use the TLS stack in `docker-compose.prod.yml`, which puts Caddy in front of the app and handles HTTPS certificates automatically.

1. Point your domain's DNS at the server.
2. Copy `.env.prod.example` to `.env`.
3. Set `DOMAIN`, `DATABASE_USERNAME`, and a strong `DATABASE_PASSWORD`.
4. Start the production stack:

```bash
./scripts/deploy-prod.sh
```

That gives you:
- HTTPS on ports `80` and `443`
- automatic certificate management via Caddy
- secure session cookies for authenticated users

Without a real domain, you cannot get a proper public TLS certificate. For local-only testing, your current Docker setup is still plain HTTP.

## Oracle Cloud VM deployment

The production stack fits a standard Oracle Cloud Ubuntu VM. Keep the app on one VM first; you do not need Oracle-specific managed services to get live quickly.

Server preparation:
1. Create an Ubuntu VM with a public IP and point your domain's `A` record at that IP.
2. Open inbound `22`, `80`, and `443` in the Oracle Cloud security list or NSG for that subnet.
3. SSH into the VM and run the bootstrap helper:

```bash
sudo bash scripts/oracle-vm-bootstrap.sh
```

4. Copy the project onto the VM.
5. Copy `.env.prod.example` to `.env` and set the real values.
6. Start the stack with `./scripts/deploy-prod.sh`.

Persistent data on the VM:
- `./postgres-data` stores PostgreSQL data
- `./uploads` stores user-uploaded media
- `./caddy_data` stores TLS certificates

Operational notes:
- Caddy must be reachable on public ports `80` and `443` so it can obtain and renew certificates.
- The app itself stays internal to Docker and is only exposed through Caddy.
- If you enable the VM firewall with UFW, allow `OpenSSH`, `80/tcp`, and `443/tcp`.
- After deployment, confirm `https://your-domain` loads before testing login, uploads, or email.

## GitHub Auto-Deploy (Webhook-based)

This project supports automatic deployment when you push to GitHub.

### How it works

1. You push to `main` on GitHub.
2. GitHub sends a webhook to your VM's `webhook` listener.
3. The VM runs `/home/ubuntu/deploy.sh`, which:
   - Fetches and resets to `origin/main`
   - Rebuilds the Docker image
   - Restarts the stack

### Setup on your VM

1. Install `webhook`:

   ```bash
   sudo apt-get update
   sudo apt-get install -y webhook
   ```

2. Create the deploy script:

   ```bash
   cd /home/ubuntu/marketsocial

   cat > /home/ubuntu/deploy.sh <<'EOF'
   #!/usr/bin/env bash
   set -euo pipefail

   cd /home/ubuntu/marketsocial

   echo "=== $(date) deploy start ==="

   git fetch origin main
   git reset --hard origin/main

   docker compose build --no-cache app
   docker compose up -d

   echo "=== $(date) deploy done ==="
   EOF

   chmod +x /home/ubuntu/deploy.sh
   ```

3. Create webhook config (`/home/ubuntu/hooks.json`) with a secret (e.g., `ERIN`):

   ```json
   [
     {
       "id": "deploy",
       "execute-command": "/home/ubuntu/deploy.sh",
       "command-working-directory": "/home/ubuntu/marketsocial",
       "pass-arguments-to-command": [
         { "source": "string", "name": "github" }
       ],
       "trigger-rule": {
         "match": {
           "type": "value",
           "value": "YOUR_SECRET_HERE",
           "parameter": {
             "source": "header",
             "name": "X-Hub-Signature-256"
           }
         }
       }
     }
   ]
   ```

4. Create the systemd service (`/etc/systemd/system/webhook.service`):

   ```bash
   sudo tee /etc/systemd/system/webhook.service > /dev/null <<'EOF'
   [Unit]
   Description=GitHub Webhook Listener for Auto-Deploy
   After=network.target

   [Service]
   Type=simple
   User=ubuntu
   Group=ubuntu
   ExecStart=/usr/bin/webhook -hooks /home/ubuntu/hooks.json -verbose -port 9000
   Restart=always
   RestartSec=5
   WorkingDirectory=/home/ubuntu/marketsocial

   [Install]
   WantedBy=multi-user.target
   EOF
   ```

5. Start and enable the service:

   ```bash
   sudo systemctl daemon-reexec
   sudo systemctl daemon-reload
   sudo systemctl enable webhook.service
   sudo systemctl start webhook.service
   ```

6. Confirm it's running:

   ```bash
   sudo ss -tulnp | grep webhook
   # Should show port 9000
   ```

### GitHub webhook setup

1. In your repo, go to **Settings → Webhooks → Add webhook**.
2. **Payload URL**: `http://<YOUR_VM_IP>:9000/hooks/deploy`
   - Example: `http://145.241.193.116:9000/hooks/deploy`
3. **Content type**: `application/json`
4. **Secret**: `ERIN`
5. **Which events?**: `Just the push event`
6. ✅ **Active**: checked
7. Click **Add webhook**.

### Test the setup

Make a small change, commit, and push:

```bash
echo "# test" >> README.md
git add .
git commit -m "test auto-deploy"
git push origin main
```

Check the webhook delivery in GitHub (repo → Settings → Webhooks → click the webhook → check the delivery log).

On the VM, watch logs:

```bash
sudo journalctl -u webhook.service -f
```

### Troubleshooting

- If the webhook fails, verify the secret in `hooks.json` matches the GitHub webhook secret.
- Ensure the VM firewall allows inbound port `9000`.
- Confirm `webhook` service is running: `sudo systemctl status webhook.service`.

## Next step for real hosting

When you want to move off your PC, this setup transfers cleanly to:

- a cheap VPS running Docker Compose
- a cloud VM with Docker
- a platform that can run a Java container plus Postgres
# test auto-deploy
