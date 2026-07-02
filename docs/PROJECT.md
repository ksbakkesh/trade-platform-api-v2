# Options Auto Trader — Project Overview

## What This Is
A full-stack algorithmic options auto-trading platform for Indian equity markets (NIFTY 50 and SENSEX indices). The platform automates the complete trading lifecycle using the Gann Square Root Technique for signal generation and Angel One SmartAPI for order execution.

## Business Model
- Platform owner (ADMIN) manages all clients
- Owner connects each client's Angel One account on their behalf
- Owner controls which UI tabs each client can see
- Clients log in and see only their own trades/signals — fully automated
- No manual intervention needed after 9:15 AM open price entry

## Repositories
- Backend: `trade-platform-api-v2` (Spring Boot 3 / Java 21)
- Frontend: `trade-platform-ui-v2` (Next.js 16 / TypeScript / Tailwind / pnpm)

## Tech Stack
### Backend
- Spring Boot 3.3.4 / Java 21
- PostgreSQL (native Mac, user: root, DB: trading_platform)
- Flyway migrations (V1–V5)
- JWT authentication (jjwt 0.12.6, HS512)
- Spring Security (stateless)
- AES-256-GCM encryption for broker credentials

### Frontend
- Next.js 16.2.9 (Turbopack)
- TypeScript
- Tailwind CSS v4
- pnpm 11.9.0
- lucide-react, recharts

## Running Locally
```bash
# Backend
cd /Applications/codebase/trade-platform-api-v2
mvn clean spring-boot:run

# Frontend
cd /Applications/codebase/trade-platform-ui-v2
pnpm dev   # runs on port 3001 (3000 may be taken)
```

## Environment
Backend `.env` at `/Applications/codebase/trade-platform-api-v2/.env`:
- ANGELONE_CLIENT_CODE, ANGELONE_API_KEY, ANGELONE_PASSWORD, ANGELONE_TOTP_SECRET
- ENCRYPTION_KEY (AES-256-GCM base64)
- DB_URL, DB_USERNAME, DB_PASSWORD
- No JWT_SECRET in .env (uses default in application.yml)

Frontend `.env.local`:
- NEXT_PUBLIC_API_URL=http://localhost:8080
