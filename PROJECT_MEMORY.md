# Project Memory

## 2026-04-07

- MarketSocial is a Spring Boot app with a static frontend, PostgreSQL persistence, Docker Compose local hosting, and optional Caddy-based HTTPS for production.
- Core backend areas currently present in the repo: authentication, profiles, products/listings, seller posts, media uploads, direct messages, admin user management, and API exception handling.
- Frontend lives in `src/main/resources/static/` with `index.html`, `app.js`, and `style.css`.
- Docker helpers exist in `scripts/`: `restart-docker.sh` restarts the stack and can run the smoke test, and `docker-smoke.sh` creates a throwaway user, logs in, checks `/api/auth/me`, then deletes the user.
- Runtime configuration is environment-variable driven for server binding, database credentials, media storage, session cookie security, and SMTP/email notifications.
- Production deployment support exists via `docker-compose.prod.yml`, `.env.prod.example`, and `Caddyfile`.
- Automated tests currently cover account management, unread message notifications, and email notification sending behavior.
- The project has message notifications wired up with unread badges, in-site popups, a red-dot indicator on the Messages tab, and unread counts in the browser tab title.
- Optional email alerts for new messages are implemented through SMTP environment variables. Required settings are `EMAIL_NOTIFICATIONS_ENABLED`, `EMAIL_FROM`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD`.
- Local Docker restarts can be run with `marketsocial-restart`, which exists at `/home/harrison/.local/bin/marketsocial-restart`.
- The direct project restart script is `./scripts/restart-docker.sh`.
- New chats should start by reading `README.md` for the overview and `PROJECT_MEMORY.md` for recent context; `AGENTS.md` now instructs that explicitly.
- Added Oracle VM deployment helpers: `scripts/deploy-prod.sh` validates `.env` and starts the HTTPS production stack, and `scripts/oracle-vm-bootstrap.sh` installs Docker/UFW and opens ports `80` and `443` on an Ubuntu VM.
- Fixed database schema issue: Added missing `email_notifications_enabled` column to `app_user` table to resolve HTTP 500 registration errors.
- Refactored user email notification logic:
    - Added `canReceiveEmailNotifications()` helper to `User` model for centralized validation.
    - Updated `MessageEmailNotificationService` to use the new model helper.
    - Enhanced UI in `app.js` to disable the "Email alerts" checkbox when no email address is provided (on registration and profile forms).
    - Unified email and notification preference validation across `AuthController`, `ProfileController`, and `UserManagementController`.
    - Updated `README.md` to reflect the refined notification requirements and UI behavior.
- Documented testing and connection procedures for Oracle/Production hosting:
    - Use `BASE_URL=https://your-domain scripts/docker-smoke.sh` for remote health checks.
    - Verified Oracle VM bootstrap script opens ports 80/443 and enables UFW.
    - Web access: `https://your-domain`.
    - VM access: `ssh -i private_key.key ubuntu@ip`.
    - Note: This info was previously in README but moved here to keep README focused on project overview.
