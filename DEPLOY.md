# Marketsocial - Auto-Deploy Setup

## Security Model

### What's in the repo (safe):
- All source code (`src/main/java/`)
- `.env.prod.example` (template with placeholder values)
- Docker configuration (`Dockerfile`, `docker-compose.yml`)
- Build files (`pom.xml`, `mvnw`)

### What's on the VM only (not in repo):
- `.env` — actual secrets (DB password, email credentials)
- `hooks.json` — webhook configuration
- `deploy.sh` — deployment script

## Auto-Deploy Flow

1. Push to `main` branch on GitHub
2. GitHub sends webhook to `http://145.241.193.116:9000/hooks/deploy`
3. Webhook service executes `/home/ubuntu/deploy.sh`
4. Script fetches latest code, rebuilds Docker containers, restarts app

## Features Implemented

### ✅ Authentication
- User registration (USER, SELLER, ADMIN roles)
- Password hashing with BCrypt
- JWT-style session management
- Protected endpoints

### ✅ Posts (Social Feed)
- Create posts (seller only)
- Delete own posts
- View all posts or own posts
- Image support

### ✅ Products (Marketplace)
- Create/edit/delete products (seller only)
- Multiple images per product
- Price, category, description
- View all products or seller's products

### ✅ Orders
- Create orders (buyer)
- Confirm order (seller)
- Ship order (seller)
- Deliver order (buyer)
- Cancel order (buyer)

### ✅ Messaging
- Send/receive messages
- Unread count notifications
- Email notifications for new messages

## Deployment

### On VM:
```bash
cd /home/ubuntu/marketsocial
docker compose up -d
```

### On Local Machine:
```bash
git push origin main
```

Watch VM logs:
```bash
sudo journalctl -u webhook.service -f
```

## Security Checklist

- [x] `.env` not in repo
- [x] `hooks.json` not in repo
- [x] `deploy.sh` not in repo
- [x] UFW firewall allows ports 80, 443, 8080, 9000
- [x] BCrypt password hashing
- [x] HTTPS (Caddy reverse proxy)
- [x] Webhook secret (`ERIN`) configured

## Future Enhancements

- User profiles with avatars
- Follow/unfollow system
- Product search with filters
- Order history pagination
- Real-time messaging (WebSocket)
- Email verification on registration

---

**Built by CoRust AI Assistant** 🤖
