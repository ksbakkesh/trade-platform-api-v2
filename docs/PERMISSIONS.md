# Permission System

## Roles
- ADMIN: Platform owner. Full access to everything. Cannot be restricted.
- USER: Client/trader. Sees only tabs assigned by ADMIN.
- VIEWER: Read-only observer. Sees only tabs assigned by ADMIN.

## Tab Permissions (14 total)
| Permission Key    | Tab Label        | Default for USER |
|------------------|-----------------|-----------------|
| dashboard        | Dashboard        | ✅ enabled |
| marketOverview   | Market Overview  | ❌ disabled |
| liveSignals      | Live Signals     | ✅ enabled |
| positions        | Positions        | ✅ enabled |
| tradeHistory     | Trade History    | ✅ enabled |
| orders           | Orders           | ❌ disabled |
| riskManagement   | Risk Management  | ✅ enabled |
| fundsMargin      | Funds & Margin   | ❌ disabled |
| brokerSetup      | Broker Setup     | ❌ disabled (admin connects broker) |
| strategySettings | Strategy Settings| ❌ disabled (hidden from clients) |
| configuration    | Configuration    | ❌ disabled |
| logs             | Logs             | ❌ disabled |
| reports          | Reports          | ❌ disabled |
| userManagement   | User Management  | ❌ disabled |

## Flow: Admin creates a client
1. Go to User Management → Add User
2. Enter username, email, password, role=USER
3. Select tab permissions (checkboxes)
4. Click "Create User"
5. Backend creates user + saves permissions
6. Click Manage → 🔌 Broker Connection
7. Enter client's Angel One credentials
8. Click "Connect {username}'s Angel One"
9. Optionally click "🔌 Test" to verify connection
10. Share login credentials with client
11. Client logs in → sees only allowed tabs → trades automatically

## DB Schema
```sql
CREATE TABLE user_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    perm_dashboard BOOLEAN NOT NULL DEFAULT true,
    perm_market_overview BOOLEAN NOT NULL DEFAULT false,
    perm_live_signals BOOLEAN NOT NULL DEFAULT true,
    perm_positions BOOLEAN NOT NULL DEFAULT true,
    perm_trade_history BOOLEAN NOT NULL DEFAULT true,
    perm_orders BOOLEAN NOT NULL DEFAULT false,
    perm_risk_management BOOLEAN NOT NULL DEFAULT true,
    perm_funds_margin BOOLEAN NOT NULL DEFAULT false,
    perm_broker_setup BOOLEAN NOT NULL DEFAULT false,
    perm_strategy_settings BOOLEAN NOT NULL DEFAULT false,
    perm_configuration BOOLEAN NOT NULL DEFAULT false,
    perm_logs BOOLEAN NOT NULL DEFAULT false,
    perm_reports BOOLEAN NOT NULL DEFAULT false,
    perm_user_management BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_user_permissions UNIQUE (user_id)
);
```
