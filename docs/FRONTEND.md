# Frontend Architecture

## Structure (no src/ folder)
```
trade-platform-ui-v2/
├── app/
│   ├── layout.tsx          Root layout with AuthProvider + AppShell
│   ├── globals.css         CSS variables, .card, .badge-* classes
│   ├── page.tsx            Dashboard (Gann levels, risk gauges, P&L)
│   ├── login/page.tsx      Login page (skips AppShell)
│   ├── signals/page.tsx    Live Signals with indicator checks
│   ├── positions/page.tsx  Open Positions with SL status
│   ├── trades/page.tsx     Trade History with P&L filters
│   ├── orders/page.tsx     Angel One order book
│   ├── risk/page.tsx       Risk gauges, daily limits
│   ├── funds/page.tsx      Funds & Margin from Angel One
│   ├── broker/page.tsx     Broker Setup wizard (5 steps)
│   ├── settings/page.tsx   Strategy Settings (NIFTY/SENSEX tabs)
│   ├── users/page.tsx      User Management with permissions + broker
│   ├── market/page.tsx     Market Overview (placeholder)
│   ├── logs/page.tsx       Logs (placeholder)
│   ├── reports/page.tsx    Reports (placeholder)
│   └── config/page.tsx     Configuration (placeholder)
├── components/ui/
│   ├── AppShell.tsx        Layout wrapper, sidebar, mobile menu, BrokerAlert
│   ├── Sidebar.tsx         Permission-filtered navigation
│   ├── TopBar.tsx          Market status, NIFTY price, bell, profile
│   ├── RouteGuard.tsx      Redirects unauthorized routes
│   ├── NotificationBell.tsx Bell icon with dropdown, unread count
│   ├── ProfileMenu.tsx     ⚙️ icon — profile edit (ADMIN) + password change
│   ├── BrokerAlert.tsx     Warning banner when broker not connected
│   ├── RiskGauge.tsx       SVG arc gauge for risk display
│   └── PnlChart.tsx        Recharts line chart for P&L
└── lib/
    ├── auth.tsx            AuthProvider, useAuth(), getAuthHeaders(), getAccountId()
    └── api.ts              API helpers, TypeScript interfaces

```

## Auth Flow
1. Login → JWT stored in localStorage.tp_user
2. Permissions fetched → stored in localStorage.tp_permissions
3. Broker account ID fetched → stored in localStorage.tp_broker
4. Sidebar filters nav items by permissions
5. RouteGuard blocks direct URL access to unauthorized routes
6. All API calls send Authorization: Bearer <token> header
7. Logout clears all three localStorage keys

## Permission System
14 tab permissions per user, set by ADMIN in User Management:
dashboard, marketOverview, liveSignals, positions, tradeHistory, orders,
riskManagement, fundsMargin, brokerSetup, strategySettings, configuration,
logs, reports, userManagement

ADMIN role always gets all permissions (no DB lookup needed).
USER/VIEWER role fetches permissions from /api/permissions/my.

## Key Patterns
- `getAuthHeaders()` — returns {Authorization: "Bearer <token>"} from localStorage
- `getAccountId()` — returns broker account ID from localStorage.tp_broker
- All pages are 'use client' with useEffect for data fetching
- Styling uses inline style={{}} with hardcoded hex for reliability with Tailwind v4
- No src/ directory — paths are @/lib/auth, @/components/ui/Sidebar, etc.

## CSS
globals.css uses @import "tailwindcss" (v4 syntax).
Custom classes: .card, .badge, .badge-green, .badge-red, .badge-amber,
.badge-blue, .badge-purple, .badge-muted, .text-data, .text-muted,
.text-accent, .text-loss, .text-warn, .bg-surface, .bg-surface-2

Colors:
--bg: #0a0e1a
--surface: #111827
--surface-2: #1f2937
--accent: #10b981 (green)
--loss: #ef4444 (red)
--warn: #f59e0b (amber)
--data: #f9fafb (white)
--muted: #6b7280 (gray)
