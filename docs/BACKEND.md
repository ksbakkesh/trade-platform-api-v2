# Backend Architecture

## Package Structure
```
com.tradingplatform/
├── auth/           JWT auth, Spring Security, UserDetailsService
├── api/            REST controllers (AuthController, BrokerController, AdminBrokerController, PermissionsController, NotificationController, UserManagementController)
├── angelone/       Angel One SmartAPI client (AngelOneAuthClient, AngelOneMarketClient, AngelOneOrderClient, AngelOneTokenStore)
├── scheduler/      TradingScheduler (15-min cron, 9:15–3:15 IST Mon-Fri)
├── signal/         SignalGenerationService, MarketSnapshot, EntryConditionChecker
├── trade/          TradeExecutionService, TradeExecutionResult
├── exit/           ExitStrategyService, ExitResult, ExitTrigger
├── reentry/        ReEntryService, ReEntryResult
├── risk/           RiskManagementService, PositionSizingService
├── position/       PositionSizingService, PositionSize
├── market/         OptionDataService (real option LTP/delta/RSI from Angel One)
├── notification/   NotificationService (in-app bell alerts)
├── strategy/gann/  GannCalculationService (NIFTY offset=0.01562, SENSEX offset=0.3124)
├── domain/         JPA entities (User, BrokerAccount, Trade, Position, Signal, StrategySettings, RiskSettings, Notification, UserPermissions)
├── repository/     Spring Data JPA repositories
├── config/         SecurityConfig (JWT + permitAll routes), AngelOneConfig
└── common/         ErrorMessages

```

## Key Endpoints
```
POST /api/auth/login              — email+password → JWT token
POST /api/auth/register           — create user
PUT  /api/auth/profile            — update own profile (ADMIN)
POST /api/auth/change-password    — change password (all users)
DELETE /api/auth/users/{id}       — delete user

GET  /api/broker/my-account       — get own broker account
POST /api/broker/connect/angel-one — connect own broker
POST /api/broker/test-connection  — test own broker

GET  /api/admin/users             — list all users
PUT  /api/admin/users/{id}        — edit user
GET  /api/admin/broker/{userId}   — get user's broker (admin)
POST /api/admin/broker/{userId}/connect  — connect broker for user
POST /api/admin/broker/{userId}/test     — test user's connection
DELETE /api/admin/broker/{userId}/disconnect

GET  /api/permissions/my          — get own permissions
GET  /api/permissions/{userId}    — get user's permissions
POST /api/permissions/{userId}    — save user's permissions

GET  /api/notifications           — get all notifications
GET  /api/notifications/unread-count
POST /api/notifications/mark-all-read
DELETE /api/notifications/{id}

GET  /api/dashboard/risk/summary?accountId=
GET  /api/dashboard/risk/daily-pnl?accountId=
GET  /api/dashboard/signals/today?accountId=
GET  /api/dashboard/trades/today?accountId=
GET  /api/dashboard/positions?accountId=
GET  /api/dashboard/market/levels?accountId=&index=&liveOpenPrice=

GET  /api/admin/strategy-settings/account/{accountId}/index/{index}
PUT  /api/admin/strategy-settings/{id}
PATCH /api/admin/strategy-settings/{id}/auto-trading?enabled=

GET  /api/test/angelone/login
GET  /api/test/angelone/funds
GET  /api/test/angelone/orders
GET  /api/test/angelone/quote?exchange=&token=&mode=
```

## Security Config
Public routes (no JWT needed):
- POST /api/auth/login, /api/auth/register
- /api/test/**
- /api/broker/**
- /api/admin/broker/**
- /api/permissions/**
- OPTIONS /**

All other routes require valid JWT Bearer token.

## Database Migrations
- V1: users, broker_accounts, trades, positions, signals, strategy_settings, risk_settings, daily_pnl, fund_management
- V2: add broker_type, is_active, display_name, is_enabled, notes to broker_accounts
- V3: unique constraint on broker_accounts.client_code
- V4: notifications table
- V5: user_permissions table

## Trading Scheduler
Runs Mon-Fri IST only:
- 9:15 AM: capture NIFTY+SENSEX open prices, first signal check
- 9:30, 9:45 ... 3:00 PM: signal check + position monitoring every 15 min
- 3:15 PM: force square-off all positions

Per cycle per account:
1. Login/refresh Angel One session
2. Fetch live NIFTY (token: 26000, NSE) and SENSEX (token: 1, BSE) spot prices
3. Calculate Gann levels → determine CE/PE direction and strike
4. Fetch real option LTP/volume from Angel One via OptionDataService
5. Generate signal (RSI ≥ 60, volume ≥ 2x, delta 0.45-0.65, premium ≥ ₹125)
6. If GENERATED → risk check → position sizing → fund validation → place order
7. Monitor open positions for SL/T1/T2 hits
8. Re-entry if SL hit and re-entry enabled
9. Save notification for each event

## Gann Calculation
- NIFTY: offset = 0.01562, lot size = 75, strike adj = 50
- SENSEX: offset = 0.3124, lot size = 10, strike adj = 100
- Buy Above = (√openPrice + offset)² 
- Sell Below = (√openPrice - offset)²
- CE Strike = round(buyAbove / adj) * adj
- PE Strike = round(sellBelow / adj) * adj

## Encryption
Broker credentials encrypted at rest with AES-256-GCM via @Converter on JPA entities.
Key in ENCRYPTION_KEY env var.
