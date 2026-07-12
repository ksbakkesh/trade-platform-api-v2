# Architecture

Consolidated, up-to-date view of the backend: system design, API surface, data model, and
broker integration. Complements the narrower docs in this folder (`PROJECT.md` — business model,
`PERMISSIONS.md` — permission system detail, `TRADING_STRATEGY.md` — strategy BRD, `FRONTEND.md` —
the separate `trade-platform-ui-v2` repo). Where those docs disagree with this one (e.g. the Gann
formula), this file reflects the current code.

## Tech Stack

- **Java 21**, Spring Boot 3.3.4, Maven (`pom.xml`, no `package.json` — this is not a Node project)
- **PostgreSQL** via Spring Data JPA/Hibernate (`ddl-auto: validate` — schema owned by Flyway, not Hibernate)
- **Flyway** migrations, `spring-boot-starter-security` + `jjwt` for stateless JWT auth
- **spring-dotenv** auto-loads `.env` (no manual `export` needed)
- Redis dependency exists in `pom.xml` but is commented out — not wired up yet
- `@EnableScheduling` on `TradingPlatformApplication` — cron-driven trading loop is core to the app

## Package Layout (`com.tradingplatform`)

```
auth/          JWT auth: AuthController, AuthService, JwtService, JwtAuthFilter
api/           REST controllers (dashboard, broker, admin, permissions, notifications...)
angelone/      Angel One SmartAPI client layer (auth, market data, orders, token store)
config/        Spring config: SecurityConfig, CorsConfig, AngelOneConfig, BrokerSessionInitializer
controller/    Dev/test controllers (/api/test/**) + ApiExceptionHandler
domain/        JPA entities + domain/enums
repository/    Spring Data JPA repositories (one per entity)
security/      EncryptedStringConverter (AES-256-GCM field encryption)
scheduler/     TradingScheduler — the cron-driven orchestration loop
signal/        Signal generation / entry-condition logic
strategy/gann/ Gann square-root strike calculation engine
trade/         Order execution orchestration
exit/          Exit / stop-loss / target monitoring
reentry/       Re-entry-after-stop-loss logic
risk/          Daily trade count / loss limit enforcement
position/      Position sizing + fund validation
market/        OptionDataService — live option LTP/RSI/delta from Angel One
notification/  In-app notification persistence
common/        Shared ErrorMessages
```

## Request/Trade Flow

Boot: `TradingPlatformApplication.main()` → Flyway migrates → `BrokerSessionInitializer`
pre-logs-in every active `BrokerAccount` to Angel One on `ApplicationReadyEvent`.

Trading cycle (`scheduler/TradingScheduler`, cron `Asia/Kolkata`, Mon–Fri, 9:15 then every 15 min
9:30–15:00, square-off at 15:15), per active `BrokerAccount`:

```
AngelOneAuthClient.loginForAccount(accountId)
  → AngelOneMarketClient.getQuote(...)        spot price (NIFTY token 26000/NSE, SENSEX token 1/BSE)
  → DailyOpenPrice captured/cached at 9:15
  → GannCalculationService.calculate()        CE/PE direction + strike
  → OptionDataService.fetchOptionData()       live option LTP/RSI/volume/delta
  → SignalGenerationService.generate()        premium ≥ threshold, RSI ≥ 60, volume ≥ 2x, delta 0.45-0.65
       │
       ├─ REJECTED → Signal persisted, stop
       └─ GENERATED
            → RiskManagementService.checkCanTrade()   max trades/day, daily loss cap (per account, combined)
            → PositionSizingService.calculate()       FIXED_LOTS / CAPITAL_BASED / FIXED_QUANTITY
            → validateFunds() against live Angel One RMS
            → AngelOneOrderClient.placeOrder()        MARKET/INTRADAY BUY
            → persists Trade + Position

Every cycle also:
  → ExitStrategyService.monitor()   SL/target hits per open Position
       └─ on SL hit → ReEntryService.evaluate() → maybe re-run entry
15:15 → ExitStrategyService.forceSquareOff()   close all open positions
```

## API / Endpoint Structure

Spring MVC, `@RestController`, stateless (no server sessions). All routes are under `/api`.

| Prefix | Controller | Purpose |
|---|---|---|
| `/api/auth` | `auth/AuthController` | login, register, `/me`, profile, change-password, delete user |
| `/api/broker` | `api/BrokerController` | self-service: view/connect/test/disconnect own Angel One account |
| `/api/admin/broker` | `api/AdminBrokerController` | admin manages any user's broker connection |
| `/api/admin/users` | `api/UserManagementController` | list/edit users |
| `/api/admin/strategy-settings` | `api/StrategySettingsController` | per-account/per-index thresholds, auto-trading toggle |
| `/api/admin/risk-settings` | `api/RiskSettingsController` | per-account risk limits |
| `/api/permissions` | `api/PermissionsController` | get/save the 15 tab-permission flags |
| `/api/notifications` | `api/NotificationController` | list, unread count, mark-read, delete |
| `/api/open-price` | `api/OpenPriceController` | today's captured open price, manual override |
| `/api/dashboard` | `api/DashboardController` | funds, live positions, order book, quote |
| `/api/dashboard/market` | `api/MarketOverviewController` | Gann levels for dashboard |
| `/api/dashboard/positions` | `api/PositionDashboardController` | open positions |
| `/api/dashboard/risk` | `api/RiskDashboardController` | risk summary, daily P&L |
| `/api/dashboard/signals` | `api/SignalDashboardController` | today's signals, by status |
| `/api/dashboard/trades` | `api/TradeDashboardController` | open/today trades |
| `/api/test/**` | `controller/*TestController` | dev-only manual verification for angelone/gann/exit/position/risk/signal/trade |

**Auth:** `auth/JwtAuthFilter` (`OncePerRequestFilter`) validates the `Bearer` token via
`JwtService`, sets `SecurityContextHolder` with `ROLE_<role>` + `userId`. `JwtService` claims:
`userId`, `email` (subject), `role`; default expiry 24h.

**Security config gap (`config/SecurityConfig.java`):** CSRF disabled, stateless sessions. CORS
origins are now a single source of truth (`CorsConfig.corsConfigurationSource()`, bound from
`cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` env var, defaulting to `localhost:3000/3001` plus
the production frontend) — `SecurityConfig` no longer has its own duplicate hardcoded CORS block.
`permitAll` currently covers `/api/test/**`, `/api/broker/**`,
`/api/admin/**`, `/api/dashboard/**`, `/api/open-price/**`, `/api/permissions/**`,
`/api/notifications/**` plus login/register — i.e. almost every prefix above is open without a
JWT today. Only routes outside that list fall to `anyRequest().authenticated()`. Treat this as a
known gap, not intended long-term behavior.

**Error handling:** `controller/ApiExceptionHandler` (`@RestControllerAdvice`) — `AngelOneApiException`
→ 502, `IllegalStateException` → 500. No handler yet for validation/`IllegalArgumentException`.

## Database / Data Model

PostgreSQL, schema owned by Flyway (`src/main/resources/db/migration/`):

```
V1  create_core_tables        users, broker_accounts, trades, positions, signals,
                               strategy_settings, risk_settings, daily_pnl, fund_management
V2  add_broker_type           broker_accounts: broker_type, display_name, is_active, is_enabled, notes
V3  unique_client_code        broker_accounts.client_code unique
V4  notifications             notifications table
V5  user_permissions          user_permissions table
V6  open_price                daily_open_prices table
V7  add_strategy_setup_permission   user_permissions.perm_strategy_setup
```

Entities (`domain/`), relationships:

- **`User`** — `username`, `email`, `passwordHash`, `role` (ADMIN/USER/VIEWER), `active`. → many `BrokerAccount` (typically one), one `UserPermissions`.
- **`BrokerAccount`** — belongs to `User`; `brokerName` (default `ANGEL_ONE`), `clientCode` (globally unique), `apiKey`/`password`/`totpSecret` encrypted via `EncryptedStringConverter`. Parent (cascade delete) of `StrategySettings`, `Signal`, `Trade`, `RiskSettings`, `DailyPnl`, `FundManagement`, `Notification`, `DailyOpenPrice`.
- **`UserPermissions`** — one-to-one with `User`; 15 boolean tab flags, including `strategySetup` (V7, default `TRUE`) and the separate, pre-existing `strategySettings` (default `FALSE`) — easy to confuse, they gate different tabs. See `PERMISSIONS.md`.
- **`StrategySettings`** — one row per `(broker_account, index_name)`; premium/RSI/volume/delta thresholds, stop-loss/target points, `exitStrategyMode`, `reEntryEnabled`, `quantityMode`, `autoTradingEnabled`.
- **`Signal`** — belongs to `BrokerAccount`; `signalType` (CE/PE), `strikePrice`, `premiumAtSignal`, `rsiValue`, `volumeRatio`, `deltaValue`, `status` (GENERATED/EXECUTED/REJECTED/EXPIRED), `rejectionReason`.
- **`Trade`** — belongs to `BrokerAccount`, optional FK to `Signal` (`SET NULL` on delete); `transactionType`, `quantity`, `entryPrice`, SL/target prices, `exitPrice`, `brokerOrderId`, `status`, `exitReason`, `realizedPnl`, `isReentry`.
- **`Position`** — 1:1 with `Trade`, deliberately separate (high-frequency LTP ticks shouldn't churn the trade audit row): `quantityRemaining`, `currentLtp`, `currentStopLoss`, `unrealizedPnl`, `slMovedToCost`.
- **`DailyPnl`** — one row per `(broker_account, trade_date)`, combined across NIFTY+SENSEX; `totalTrades`, `totalPnl`, `dailyLossLimitHit`, `maxTradesHit`, `tradingDisabled`.
- **`FundManagement`** — periodic snapshots of available funds/margin and today's P&L.
- **`RiskSettings`** — one per `BrokerAccount`; `maxTradesPerDay` (default 2), `dailyLossLimit` (default ₹4500), `scope` (default `COMBINED`).
- **`SystemLog`** — general audit log, JSONB `context`.
- **`Notification`** — belongs to `BrokerAccount`; `title`, `message`, `type`, `isRead`.
- **`DailyOpenPrice`** — belongs to `BrokerAccount`; `indexName`, `openPrice`, `tradeDate`, `source` (AUTO/MANUAL), unique per `(broker_account, index, trade_date)`.

One `JpaRepository` per entity under `repository/`.

**Encryption:** `security/EncryptedStringConverter` — AES-256-GCM, stores `base64(IV || ciphertext)`,
key from `ENCRYPTION_KEY` env (32-byte base64); app fails fast at startup if the key is missing or
the wrong length.

## Broker Integration (Angel One)

`angelone/` package. Per-account ("multi-client") support was added alongside older single-account
("legacy", `.env`-backed) methods — the two paths currently coexist:

- **`AngelOneAuthClient`** — `loginForAccount(brokerAccountId)` reads credentials from `BrokerAccount`
  (DB), generates a TOTP (`TotpGenerator`, RFC 6238 HMAC-SHA1, no external lib), calls Angel One's
  `loginByPassword`, stores tokens keyed by account ID. Legacy no-arg `login()`/`ensureLoggedIn()`
  reads from `.env` (`AngelOneProperties`) and only backs `/api/test/angelone/**`.
- **`AngelOneTokenStore`** — in-memory `ConcurrentHashMap<brokerAccountId, Session>` (JWT/refresh/feed
  tokens, 6h TTL). Designed to support multiple concurrent client accounts; intended to move to Redis
  later. Legacy no-arg getters fall back to "first active session found."
- **`AngelOneMarketClient`** — per-account methods (`getFunds`, `getLivePositions`, `getProfile`,
  `getQuote`, `getOrderBook`) each call `ensureLoggedIn(brokerAccountId)` and attach that account's
  JWT/API key. Legacy no-arg overloads are `@Deprecated`.
- **`AngelOneOrderClient`** — **still legacy-only**: `placeOrder()` uses the no-arg
  `ensureLoggedIn()`/`getJwtToken()` ("first active session"), i.e. actual order placement is not
  yet account-scoped even though login and market data are. Flag this when working on multi-client
  order routing — it's the one piece of the migration still outstanding.
- **`BrokerSessionInitializer`** — on `ApplicationReadyEvent`, logs in every active `BrokerAccount`
  to warm the token store; the scheduler also re-logs-in per cycle.
- **`domain/enums/BrokerType`** — `ANGEL_ONE` (implemented); `ZERODHA`/`UPSTOX`/`DHAN` exist as
  stubs (`implemented=false`) marking the multi-broker extension point — no real client code yet.
- **`config/AngelOneConfig`** — shared `RestClient` with static headers (`X-PrivateKey`,
  `X-ClientLocalIP/PublicIP`, `X-MACAddress`) from `config/AngelOneProperties` (`angelone.*` in
  `application.yml`), base URL `https://apiconnect.angelone.in`.

Sessions are held in memory only — lost on app restart, re-established automatically by
`BrokerSessionInitializer` and the scheduler.

## Gann Strike Calculation (`strategy/gann/`)

- **`GannConstants`** — per-index offsets: NIFTY `0.1562`, SENSEX `0.3124`; `spotStopLossPoints = 60`
  for both. `ceStrikeAdjustment`/`peStrikeAdjustment` fields still exist (NIFTY 100/200, SENSEX
  400/400) but are dead code — no longer applied since the CEILING/FLOOR rewrite.
- **`GannCalculationService.calculate(indexName, openPrice)`** → `GannLevels`:
  ```
  root      = sqrt(openPrice)                    (20 sig-digit MathContext)
  buyAbove  = (root + offset)²
  sellBelow = (root - offset)²
  spotStopLoss = openPrice - 60
  ceStrike  = CEILING(buyAbove, 100)
  peStrike  = FLOOR(sellBelow, 100)
  ```
  This CEILING/FLOOR formula replaced the older `ROUND(±100, -2)` approach (see commits
  `4596660`, `d20522a`). **`README.md`, `docs/BACKEND.md`, and `docs/TRADING_STRATEGY.md` still
  describe the old ROUND-based formula and should be treated as stale on this point** — this file
  and the code (`GannCalculationServiceTest`) are the source of truth.
- **`GannRounding.roundToNearestHundred`** — leftover HALF_UP helper from the pre-CEILING/FLOOR
  implementation; only exercised by its own unit test now, not called by the service.

## Config/Env

- `.env` (gitignored) / `.env.example` — `ANGELONE_API_KEY`, `ANGELONE_CLIENT_CODE`,
  `ANGELONE_PASSWORD`, `ANGELONE_TOTP_SECRET`, `ENCRYPTION_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- `application.yml` — server port 8080, datasource (Aiven cloud Postgres default, env-overridable),
  `ddl-auto: validate`, Flyway locations, `jwt.secret`/`jwt.expiration-hours` (default secret is
  hardcoded as a fallback — **not safe for production**, must be overridden), `angelone.*` block.
- `application-local.yml` / `application-cloud.yml` — datasource overrides only (local Docker
  Postgres via `docker-compose.yml` vs Aiven cloud).

## Known Gaps (worth tracking)

- Most API prefixes are `permitAll` in `SecurityConfig` — JWT enforcement doesn't yet cover most
  routes despite the auth infrastructure being in place.
- `AngelOneOrderClient.placeOrder()` isn't account-scoped yet, unlike login/market data — the last
  piece of the multi-client migration.
- `README.md`, `docs/BACKEND.md`, `docs/TRADING_STRATEGY.md` describe the pre-CEILING/FLOOR Gann
  formula; `docs/BACKEND.md` migration list stops at V5; `docs/PERMISSIONS.md` DDL/count (14)
  predates the V7 `strategySetup` permission (now 15).
