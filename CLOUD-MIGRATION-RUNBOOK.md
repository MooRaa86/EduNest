# EduNest Cloud Migration & Production Deployment Runbook (Non-Railway / VPS)

**Document ID:** `CLOUD-MIGRATION-RUNBOOK`  
**Target:** Production Backend Hosting Migration (PaaS $\to$ VPS / Independent Cloud Infrastructure)  
**Author:** Omar Medhat (Tech Lead)  
**Date:** 2026-11-14  
**Status:** Ready for Execution  

---

## 1. Executive Summary & Migration Objective

This runbook provides the complete, production-grade guide to migrating the EduNest backend (`backend.edunest.dpdns.org`) from Railway to an independent Virtual Private Server (VPS) or cloud container service.

### Key Architectural Advantage:
Because EduNest's database is managed on **Supabase PostgreSQL** and static files are on **Supabase Storage S3**, the backend application is completely **stateless**. The VPS only needs to run:
1. The pre-built, digest-pinned Docker container pulled from **GitHub Container Registry (GHCR)**.
2. A lightweight reverse proxy (**Caddy** or **Nginx**) for automated SSL/TLS certificate renewal and WebSocket connection upgrades.

---

## 2. Hardware Sizing & Recommended Specifications

### Application Resource Profile:
- **Runtime:** Spring Boot 3.x, Java 21 (Temurin), Embedded Tomcat.
- **Memory Footprint:** Base JVM Heap ~400MB–800MB. With G1GC and headroom for concurrent STOMP WebSocket sessions, 2GB–4GB RAM is the optimal range.
- **Database / Storage:** 0 GB local disk needed for DB/uploads (managed externally). Local disk is only used for OS, Docker image cache (~1.5 GB), and rotated logs (~2 GB).
- **Concurrency:** 2 vCPU easily handles hundreds of active student/mentor WebSocket rooms and HTTP requests.

### Specification Recommendations:

| Tier | vCPU | RAM | NVMe / SSD | Bandwidth | Monthly Cost | Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Minimum** | 2 vCPU | 2 GB | 20 GB | 1 TB+ | ~$3.50 – $5 | Suitable for staging or low-traffic dev |
| **Optimal (Recommended)** | **2 to 4 vCPU** | **4 GB** | **40 GB** | **20 TB** | **~€3.80 – €7** | **Ideal for production stability & STOMP rooms** |
| **High Growth** | 4 vCPU | 8 GB | 80 GB | 20 TB | ~€10 – €14 | Heavy concurrent exam submissions & analytics |

---

## 3. Best Value-for-Money Cloud VPS Providers

### 🏆 1. Hetzner Cloud (Top Recommendation — Best Price/Performance Globally)
- **Plan:** `CX22` (x86 AMD/Intel) or `CAX11` (Ampere ARM64)
- **Specs:** 2 vCPU, 4 GB RAM, 40 GB NVMe SSD, 20 TB Traffic, 1 IPv4 + IPv6.
- **Cost:** **~€3.79 to €4.40 / month (~$4.10)**.
- **Data Centers:** Nuremberg/Falkenstein (Germany), Helsinki (Finland), Ashburn/Hillsboro (USA).
- **Why Hetzner?** Unmatched reliability, ultra-fast NVMe storage, European enterprise network, zero hidden fees.

### 🥈 2. Contabo
- **Plan:** `Cloud VPS 1`
- **Specs:** 4 vCPU, 6 GB RAM, 100 GB NVMe SSD, 32 TB Traffic.
- **Cost:** **~€4.50 to €5.50 / month**.
- **Why Contabo?** Highest raw specs per dollar, though network latency and neighbor isolation can vary more than Hetzner.

### 🥉 3. DigitalOcean / Linode (Akamai)
- **Plan:** `Basic Droplet` (2 vCPU, 2 GB or 4 GB RAM)
- **Cost:** **$12 to $24 / month**.
- **Why?** Very popular, excellent UI, high network availability, but 3x–4x more expensive than Hetzner for identical specs.

---

## 4. Server Initialization & Security Hardening (Ubuntu 24.04 LTS)

Run these commands once when provisioning the fresh server:

```bash
# 1. Update system packages
sudo apt update && sudo apt upgrade -y

# 2. Install essential utilities & Docker
sudo apt install -y curl wget git ufw fail2ban ca-certificates gnupg

# 3. Install Docker Engine & Compose plugin
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 4. Enable Docker for non-root deploy user
sudo usermod -aG docker $USER

# 5. Configure Firewall (UFW) - Open only SSH, HTTP, HTTPS
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP (Let's Encrypt verification)
sudo ufw allow 443/tcp   # HTTPS
sudo ufw --force enable
```

---

## 5. Production Docker Compose & Reverse Proxy Setup

Create a dedicated directory for EduNest:
```bash
sudo mkdir -p /opt/edunest && cd /opt/edunest
```

### A. Reverse Proxy: Caddy (`/opt/edunest/Caddyfile`)
Caddy is chosen over Nginx because it **automatically obtains and renews TLS/SSL certificates** from Let's Encrypt and handles WebSocket streaming natively with zero extra header boilerplate.

Create `/opt/edunest/Caddyfile`:
```caddy
backend.edunest.dpdns.org {
    # Reverse proxy to the Spring Boot container
    reverse_proxy app:8080 {
        # WebSocket / STOMP support is native in Caddy
        header_up Host {host}
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }

    # Security Headers
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
        Referrer-Policy "strict-origin-when-cross-origin"
    }

    # Enable compression
    encode gzip zstd
}
```

### B. Multi-Container Orchestration (`/opt/edunest/docker-compose.yml`)

Create `/opt/edunest/docker-compose.yml`:
```yaml
services:
  app:
    image: ${IMAGE_REF:-ghcr.io/mooraa86/edunest-backend:latest}
    restart: always
    env_file:
      - .env
    environment:
      - PORT=8080
      - SPRING_PROFILES_ACTIVE=prod
      - JAVA_TOOL_OPTIONS=-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0
    expose:
      - "8080"
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
    logging:
      driver: "json-file"
      options:
        max-size: "50m"
        max-file: "5"

  caddy:
    image: caddy:2-alpine
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      app:
        condition: service_healthy

volumes:
  caddy_data:
  caddy_config:
```

---

## 6. Environment Variables Contract (`/opt/edunest/.env`)

Create `/opt/edunest/.env` (protected with `chmod 600 .env`):

```bash
# Database (Supabase PostgreSQL 17)
DATABASE_URL=jdbc:postgresql://db.ciogysvcykzxrjuskxmk.supabase.co:5432/postgres?sslmode=verify-full
DATABASE_NAME=postgres
DATABASE_PASSWORD=<SUPABASE_PASSWORD>

# Application Security
JWT_SECRET=<PRODUCTION_JWT_SECRET_MIN_256_BITS>

# Email / SMTP
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=edunest.official@gmail.com
MAIL_PASSWORD=<GMAIL_APP_PASSWORD>

# Storage (Supabase S3)
SUPABASE_URL=https://ciogysvcykzxrjuskxmk.supabase.co
SUPABASE_KEY=<SUPABASE_SERVICE_ROLE_KEY>

# Payments (Stripe - when enabled in P7)
STRIPE_API_KEY=<STRIPE_LIVE_OR_TEST_SECRET_KEY>
STRIPE_WEBHOOK_SECRET=<STRIPE_ENDPOINT_SECRET>
STRIPE_ENABLED=false

# Platform Defaults
app.commission.default=0.10
```

---

## 7. Connecting GitHub Container Registry (GHCR)

Because `ghcr.io/mooraa86/edunest-backend` is private:
1. Generate a GitHub Personal Access Token (PAT) with `read:packages` scope.
2. Authenticate Docker on the server:
   ```bash
   echo "<GITHUB_PAT>" | docker login ghcr.io -u MooRaa86 --password-stdin
   ```

---

## 8. Updating GitHub Actions Pipeline (`deploy-production.yml`)

In `.github/workflows/deploy-production.yml`, replace Stage 5 (Railway CLI deployment) with the automated SSH deployment action:

```yaml
    # Stage 5: Deploy to Cloud VPS via SSH
    - name: Deploy Container to VPS
      uses: appleboy/ssh-action@v1.0.3
      with:
        host: ${{ secrets.PROD_VPS_HOST }}
        username: ${{ secrets.PROD_VPS_USER }}
        key: ${{ secrets.PROD_VPS_SSH_KEY }}
        script: |
          cd /opt/edunest
          echo "Pulling verified image: ${{ steps.prepare.outputs.image_digest_ref }}"
          export IMAGE_REF="${{ steps.prepare.outputs.image_digest_ref }}"
          docker compose pull app
          docker compose up -d --no-deps --build app
          docker image prune -f
```

### GitHub Secrets to Add:
- `PROD_VPS_HOST`: Public IPv4 of the VPS.
- `PROD_VPS_USER`: Deploy user (e.g. `ubuntu` or `deploy`).
- `PROD_VPS_SSH_KEY`: Private SSH Key generated for GitHub Actions deployment.

---

## 9. Zero-Downtime DNS Cutover Runbook

1. **Test the new VPS directly via IP:**
   ```bash
   curl -I http://<VPS_IP>:8080/actuator/health
   ```
2. **Update DNS Records:**
   - Log into your DNS provider (e.g. Cloudflare / Namecheap / DNSimple).
   - Locate the record for `backend.edunest.dpdns.org`.
   - Update the **A Record** to point to the new `<VPS_IPv4>`.
   - Set TTL to 120 seconds for fast propagation.
3. **Verify SSL & Healthcheck:**
   - Caddy automatically requests the Let's Encrypt certificate as soon as DNS points to the server.
   - Run verification probe:
     ```powershell
     powershell -ExecutionPolicy Bypass -File ops/runbooks/Invoke-DeployVerification.ps1 `
       -BaseUrl "https://backend.edunest.dpdns.org" -ExpectedCommit "<DEPLOYED_SHA>"
     ```
4. **Decommission Railway:**
   - Once DNS has fully propagated and all traffic routes to the VPS, safely delete or suspend the Railway service.

---

## 10. Summary Checklist

- [ ] VPS provisioned (Hetzner CAX11 / CX22 recommended).
- [ ] Docker & Docker Compose installed.
- [ ] `/opt/edunest` directory initialized with `Caddyfile`, `docker-compose.yml`, and `.env`.
- [ ] GHCR credentials authenticated via `docker login ghcr.io`.
- [ ] Container booted and tested locally on the VPS (`/actuator/health` UP).
- [ ] DNS `A Record` updated to point `backend.edunest.dpdns.org` to the new VPS IP.
- [ ] GitHub Actions updated with SSH deployment secrets.
- [ ] `PROD_AUTO_DEPLOY_ENABLED` set to `true`.
