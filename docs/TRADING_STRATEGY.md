# Trading Strategy (BRD)

## Strategy: Gann Square Root Technique

### Open Price Capture (9:15 AM IST)
- NIFTY open price fetched from Angel One (NSE token: 26000)
- SENSEX open price fetched from Angel One (BSE token: 1)
- Or entered manually on dashboard

### Gann Level Calculation
NIFTY (offset = 0.1562, lot = 75, strike adj = 50):
- Buy Above = (√openPrice + 0.1562)²
- Sell Below = (√openPrice - 0.1562)²

SENSEX (offset = 0.3124, lot = 10, strike adj = 100):
- Buy Above = (√openPrice + 0.3124)²
- Sell Below = (√openPrice - 0.3124)²

### Entry Signal (checked every 15 min)
Entry triggered when ALL conditions pass:
1. Spot price > Buy Above → BUY CE (Call option)
   OR Spot price < Sell Below → BUY PE (Put option)
2. Option premium ≥ ₹125
3. RSI (14-period) ≥ 60
4. Current 15-min candle volume ≥ 2× previous candle
5. Option delta between 0.45 and 0.65
6. Risk checks pass (max 2 trades/day, daily loss ≤ ₹4,500)
7. Fund validation passes (available margin ≥ estimated cost)

### Position Sizing
Mode: Capital Based (default)
- Available Capital × Allocation% = Capital Per Trade
- Capital Per Trade ÷ (Premium × Lot Size) = Number of Lots
- Order Quantity = Lots × Lot Size

Example: ₹1,00,000 × 20% = ₹20,000
₹20,000 ÷ (₹125 × 75) = 2.13 → 2 lots → 150 qty

### Exit Strategy: Option 1 (default)
1. Target 1 hit (premium up 160 points):
   - Exit 50% of position
   - Move Stop Loss to cost price (protected)
2. Target 2 hit (premium up 200 points):
   - Exit remaining 50%
3. Stop Loss hit (premium down 100 points):
   - Exit 100% immediately
   - Evaluate re-entry

### Exit Strategy: Option 2
- Exit 100% at Target 1

### Re-entry (if enabled)
After SL hit, re-entry if:
1. Re-entry enabled in strategy settings
2. Max trades not exceeded
3. Daily loss limit not exceeded
4. All entry conditions still pass

### Risk Management
- Max 2 trades per day (combined NIFTY + SENSEX)
- Daily loss limit: ₹4,500
- Both reset at 9:15 AM next trading day
- If either limit hit → no new trades for the day

### Square-off
- 3:15 PM IST: All open positions force-closed at market price
- Applies every trading day regardless of P&L
