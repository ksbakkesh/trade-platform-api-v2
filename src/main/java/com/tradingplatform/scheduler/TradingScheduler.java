package com.tradingplatform.scheduler;

import com.tradingplatform.angelone.AngelOneAuthClient;
import com.tradingplatform.angelone.AngelOneMarketClient;
import com.tradingplatform.angelone.dto.QuoteResponse;
import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.domain.DailyOpenPrice;
import com.tradingplatform.signal.MarketSnapshot;
import com.tradingplatform.repository.DailyOpenPriceRepository;
import com.tradingplatform.domain.Position;
import com.tradingplatform.domain.Signal;
import com.tradingplatform.domain.StrategySettings;
import com.tradingplatform.domain.Trade;
import com.tradingplatform.domain.enums.IndexName;
import com.tradingplatform.domain.enums.SignalStatus;
import com.tradingplatform.domain.enums.TradeStatus;
import com.tradingplatform.exit.ExitResult;
import com.tradingplatform.exit.ExitStrategyService;
import com.tradingplatform.reentry.ReEntryResult;
import com.tradingplatform.reentry.ReEntryService;
import com.tradingplatform.repository.BrokerAccountRepository;
import com.tradingplatform.repository.PositionRepository;
import com.tradingplatform.repository.StrategySettingsRepository;
import com.tradingplatform.repository.TradeRepository;
import com.tradingplatform.market.OptionDataService;
import com.tradingplatform.strategy.gann.GannCalculationService;
import com.tradingplatform.strategy.gann.GannLevels;
import com.tradingplatform.signal.SignalGenerationService;
import com.tradingplatform.trade.TradeExecutionResult;
import com.tradingplatform.trade.TradeExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core trading scheduler — runs every 15 minutes during market hours (IST).
 *
 * Schedule:
 *   09:15 — market open, capture open prices, first signal check
 *   09:30, 09:45 ... 15:00 — entry checks + position monitoring
 *   15:15 — force square-off all open positions
 *
 * Per cycle, for each active broker account:
 *   1. Login/refresh Angel One session
 *   2. Fetch live NIFTY + SENSEX spot prices
 *   3. For each index with auto-trading ON:
 *      a. Generate signal (Gann + entry conditions)
 *      b. If GENERATED → execute trade
 *   4. Monitor open positions for SL/T1/T2 hits
 *   5. Evaluate re-entry if SL hit and re-entry enabled
 */
@Component
public class TradingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradingScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Angel One instrument tokens for index spot prices
    private static final String NIFTY_SPOT_TOKEN  = "26000"; // NSE NIFTY 50
    private static final String SENSEX_SPOT_TOKEN = "1";     // BSE SENSEX

    // Open prices captured at 9:15 AM per (accountId:index)
    private final Map<String, BigDecimal> openPriceCache = new ConcurrentHashMap<>();

    private final BrokerAccountRepository brokerAccountRepository;
    private final StrategySettingsRepository strategySettingsRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final AngelOneAuthClient angelOneAuthClient;
    private final AngelOneMarketClient marketClient;
    private final SignalGenerationService signalGenerationService;
    private final TradeExecutionService tradeExecutionService;
    private final ExitStrategyService exitStrategyService;
    private final ReEntryService reEntryService;
    private final OptionDataService optionDataService;
    private final GannCalculationService gannCalculationService;
    private final DailyOpenPriceRepository dailyOpenPriceRepository;

    public TradingScheduler(BrokerAccountRepository brokerAccountRepository,
                             StrategySettingsRepository strategySettingsRepository,
                             PositionRepository positionRepository,
                             TradeRepository tradeRepository,
                             AngelOneAuthClient angelOneAuthClient,
                             AngelOneMarketClient marketClient,
                             SignalGenerationService signalGenerationService,
                             TradeExecutionService tradeExecutionService,
                             ExitStrategyService exitStrategyService,
                             ReEntryService reEntryService,
                             OptionDataService optionDataService,
                             GannCalculationService gannCalculationService,
                             DailyOpenPriceRepository dailyOpenPriceRepository) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.strategySettingsRepository = strategySettingsRepository;
        this.positionRepository = positionRepository;
        this.tradeRepository = tradeRepository;
        this.angelOneAuthClient = angelOneAuthClient;
        this.marketClient = marketClient;
        this.signalGenerationService = signalGenerationService;
        this.tradeExecutionService = tradeExecutionService;
        this.exitStrategyService = exitStrategyService;
        this.reEntryService = reEntryService;
        this.optionDataService = optionDataService;
        this.gannCalculationService = gannCalculationService;
        this.dailyOpenPriceRepository = dailyOpenPriceRepository;
    }

    /**
     * 9:15 AM — opening candle (capture open prices + first signal check)
     */
    @Scheduled(cron = "0 15 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void openingCandle() {
        log.info("=== 9:15 AM Opening candle ===");
        runCycle(true, false);
    }

    /**
     * Every 15 minutes from 9:30 AM to 3:00 PM
     */
    @Scheduled(cron = "0 30,45 9 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 0,15,30,45 10,11,12,13,14 * * MON-FRI", zone = "Asia/Kolkata")
    @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void regularCandle() {
        log.info("=== Regular 15-min cycle at {} IST ===", LocalTime.now(IST));
        runCycle(false, false);
    }

    /**
     * 3:15 PM — force square-off all open positions
     */
    @Scheduled(cron = "0 15 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void squareOffCandle() {
        log.info("=== 3:15 PM SQUARE-OFF ===");
        runCycle(false, true);
    }

    private void runCycle(boolean isOpeningCandle, boolean isSquareOff) {
        // Skip weekends (extra safety)
        DayOfWeek dow = LocalDate.now(IST).getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return;

        List<BrokerAccount> accounts = brokerAccountRepository.findAll().stream()
                .filter(BrokerAccount::isActive)
                .toList();

        log.info("Processing {} active account(s)", accounts.size());

        // Fetch shared spot prices once using first available account
        // NIFTY and SENSEX open prices are the same for all accounts
        BigDecimal sharedNiftySpot = null;
        BigDecimal sharedSensexSpot = null;
        if (isOpeningCandle && !accounts.isEmpty()) {
            BrokerAccount first = accounts.get(0);
            try {
                sharedNiftySpot = fetchSpotPrice(first.getId(), "NSE", NIFTY_SPOT_TOKEN, "NIFTY");
                sharedSensexSpot = fetchSpotPrice(first.getId(), "BSE", SENSEX_SPOT_TOKEN, "SENSEX");
                log.info("Shared open prices fetched — NIFTY={} SENSEX={}", sharedNiftySpot, sharedSensexSpot);
                // Broadcast open prices to all accounts
                if (sharedNiftySpot != null || sharedSensexSpot != null) {
                    for (BrokerAccount account : accounts) {
                        if (sharedNiftySpot != null) saveOpenPrice(account, "NIFTY", sharedNiftySpot, "AUTO");
                        if (sharedSensexSpot != null) saveOpenPrice(account, "SENSEX", sharedSensexSpot, "AUTO");
                        openPriceCache.put(key(account.getId(), "NIFTY"), sharedNiftySpot != null ? sharedNiftySpot : BigDecimal.ZERO);
                        openPriceCache.put(key(account.getId(), "SENSEX"), sharedSensexSpot != null ? sharedSensexSpot : BigDecimal.ZERO);
                    }
                    log.info("Open prices broadcast to {} accounts", accounts.size());
                }
            } catch (Exception e) {
                log.warn("Could not fetch shared spot prices: {}", e.getMessage());
            }
        }

        for (BrokerAccount account : accounts) {
            try {
                processAccount(account, isOpeningCandle, isSquareOff);
            } catch (Exception e) {
                log.error("[Account {}] Cycle error: {}", account.getId(), e.getMessage(), e);
            }
        }
    }

    private void processAccount(BrokerAccount account, boolean isOpeningCandle, boolean isSquareOff) {
        // Step 1: ensure valid Angel One session
        try {
            angelOneAuthClient.loginForAccount(account.getId());
        } catch (Exception e) {
            log.error("[Account {}] Login failed — skipping: {}", account.getId(), e.getMessage());
            return;
        }

        // Step 2: square-off at 3:15 PM
        if (isSquareOff) {
            squareOffAllPositions(account);
            return;
        }

        // Step 3: fetch live spot prices
        BigDecimal niftySpot  = fetchSpotPrice(account.getId(), "NSE", NIFTY_SPOT_TOKEN, "NIFTY");
        BigDecimal sensexSpot = fetchSpotPrice(account.getId(), "BSE", SENSEX_SPOT_TOKEN, "SENSEX");

        // Step 4: cache open prices at 9:15 AM and save to DB
        if (isOpeningCandle) {
            if (niftySpot != null) {
                openPriceCache.put(key(account.getId(), "NIFTY"), niftySpot);
                saveOpenPrice(account, "NIFTY", niftySpot, "AUTO");
            }
            if (sensexSpot != null) {
                openPriceCache.put(key(account.getId(), "SENSEX"), sensexSpot);
                saveOpenPrice(account, "SENSEX", sensexSpot, "AUTO");
            }
            log.info("[Account {}] Open prices captured — NIFTY={} SENSEX={}", account.getId(), niftySpot, sensexSpot);
        } else {
            // Load from DB if not in cache (e.g. after restart)
            if (!openPriceCache.containsKey(key(account.getId(), "NIFTY"))) {
                dailyOpenPriceRepository.findByBrokerAccountIdAndIndexNameAndTradeDate(
                        account.getId(), "NIFTY", java.time.LocalDate.now())
                        .ifPresent(p -> openPriceCache.put(key(account.getId(), "NIFTY"), p.getOpenPrice()));
            }
            if (!openPriceCache.containsKey(key(account.getId(), "SENSEX"))) {
                dailyOpenPriceRepository.findByBrokerAccountIdAndIndexNameAndTradeDate(
                        account.getId(), "SENSEX", java.time.LocalDate.now())
                        .ifPresent(p -> openPriceCache.put(key(account.getId(), "SENSEX"), p.getOpenPrice()));
            }
        }

        // Step 5: signal + trade for each index
        if (niftySpot  != null) tryEntry(account, IndexName.NIFTY,  niftySpot);
        if (sensexSpot != null) tryEntry(account, IndexName.SENSEX, sensexSpot);

        // Step 6: monitor open positions
        monitorPositions(account, niftySpot, sensexSpot);
    }

    private void tryEntry(BrokerAccount account, IndexName index, BigDecimal spotPrice) {
        StrategySettings settings = strategySettingsRepository
                .findByBrokerAccountIdAndIndexName(account.getId(), index)
                .orElse(null);

        if (settings == null || !settings.isAutoTradingEnabled()) {
            log.debug("[Account {}][{}] Auto-trading OFF", account.getId(), index);
            return;
        }

        // Skip if already have open position for this index today
        boolean hasOpenTrade = tradeRepository
                .findByBrokerAccountIdAndStatus(account.getId(), TradeStatus.OPEN)
                .stream().anyMatch(t -> t.getIndexName() == index);
        if (hasOpenTrade) {
            log.debug("[Account {}][{}] Already has open position", account.getId(), index);
            return;
        }

        BigDecimal openPrice = openPriceCache.getOrDefault(key(account.getId(), index.name()), spotPrice);

        // Calculate Gann levels to determine CE/PE direction and strike
        GannLevels levels = gannCalculationService.calculate(index, openPrice);

        // Determine direction
        com.tradingplatform.domain.enums.OptionType direction = null;
        BigDecimal strike = null;
        if (spotPrice.compareTo(levels.buyAbove()) > 0) {
            direction = com.tradingplatform.domain.enums.OptionType.CE;
            strike = levels.ceStrike();
        } else if (spotPrice.compareTo(levels.sellBelow()) < 0) {
            direction = com.tradingplatform.domain.enums.OptionType.PE;
            strike = levels.peStrike();
        }

        if (direction == null) {
            log.debug("[Account {}][{}] Spot {} between levels — no direction", account.getId(), index, spotPrice);
            return;
        }

        // Fetch REAL option data from Angel One
        MarketSnapshot snapshot = optionDataService.fetchOptionData(account.getId(), index, direction, strike, spotPrice);

        Signal signal = signalGenerationService.generate(
                account, index, openPrice, spotPrice,
                snapshot, snapshot.tradingSymbol(), snapshot.symbolToken());

        log.info("[Account {}][{}] Signal={} reason={}",
                account.getId(), index, signal.getStatus(), signal.getRejectionReason());

        if (signal.getStatus() == SignalStatus.GENERATED) {
            BigDecimal capital = fetchAvailableCapital(account.getId());
            TradeExecutionResult result = tradeExecutionService.execute(
                    signal, account, snapshot.ltp(), capital, false, false);

            if (result.executed()) {
                log.info("[Account {}][{}] ✅ Trade placed! orderId={} symbol={}",
                        account.getId(), index,
                        result.trade().getBrokerOrderId(),
                        result.trade().getTradingSymbol());
            } else {
                log.warn("[Account {}][{}] ❌ Trade blocked: {}", account.getId(), index, result.reason());
            }
        }
    }

    private void monitorPositions(BrokerAccount account,
                                   BigDecimal niftySpot, BigDecimal sensexSpot) {
        List<Trade> openTrades = tradeRepository
                .findByBrokerAccountIdAndStatus(account.getId(), TradeStatus.OPEN);

        if (openTrades.isEmpty()) return;
        log.info("[Account {}] Monitoring {} open trade(s)", account.getId(), openTrades.size());

        for (Trade trade : openTrades) {
            try {
                Position position = positionRepository.findByTradeId(trade.getId()).orElse(null);
                if (position == null) continue;

                // Fetch current LTP of the option from Angel One
                BigDecimal ltp = fetchOptionLtp(account.getId(), trade.getSymbolToken());
                if (ltp == null) {
                    // Fallback: use spot price as proxy if option LTP unavailable
                    ltp = trade.getIndexName() == IndexName.SENSEX ? sensexSpot : niftySpot;
                }
                if (ltp == null) continue;

                ExitResult exitResult = exitStrategyService.monitor(trade, position, ltp);
                log.info("[Account {}][{}] Monitor: ltp={} action={}",
                        account.getId(), trade.getIndexName(), ltp, exitResult.actionTaken());

                // Re-entry check on SL hit
                if (exitResult.trigger() == com.tradingplatform.exit.ExitTrigger.STOP_LOSS) {
                    checkReEntry(account, trade, exitResult, niftySpot, sensexSpot);
                }

            } catch (Exception e) {
                log.error("[Account {}] Monitor error for trade {}: {}",
                        account.getId(), trade.getId(), e.getMessage());
            }
        }
    }

    private void checkReEntry(BrokerAccount account, Trade trade,
                               ExitResult exitResult,
                               BigDecimal niftySpot, BigDecimal sensexSpot) {
        BigDecimal spotPrice = trade.getIndexName() == IndexName.SENSEX ? sensexSpot : niftySpot;
        if (spotPrice == null) return;

        BigDecimal openPrice = openPriceCache.getOrDefault(
                key(account.getId(), trade.getIndexName().name()), spotPrice);

        // Determine direction from original trade
        com.tradingplatform.domain.enums.OptionType optionType =
                trade.getTradingSymbol() != null && trade.getTradingSymbol().endsWith("CE")
                        ? com.tradingplatform.domain.enums.OptionType.CE
                        : com.tradingplatform.domain.enums.OptionType.PE;

        GannLevels levels = gannCalculationService.calculate(trade.getIndexName(), openPrice);
        BigDecimal strike = optionType == com.tradingplatform.domain.enums.OptionType.CE
                ? levels.ceStrike() : levels.peStrike();

        MarketSnapshot snapshot = optionDataService.fetchOptionData(account.getId(), 
                trade.getIndexName(), optionType, strike, spotPrice);

        ReEntryResult reEntry = reEntryService.evaluate(
                exitResult, trade, account, openPrice, spotPrice, snapshot, fetchAvailableCapital(account.getId()));

        if (reEntry.allowed()) {
            log.info("[Account {}][{}] Re-entry allowed — generating new signal",
                    account.getId(), trade.getIndexName());
            tryEntry(account, trade.getIndexName(), spotPrice);
        } else {
            log.info("[Account {}][{}] Re-entry blocked: {}", account.getId(), trade.getIndexName(), reEntry.reason());
        }
    }

    private void squareOffAllPositions(BrokerAccount account) {
        List<Trade> openTrades = tradeRepository
                .findByBrokerAccountIdAndStatus(account.getId(), TradeStatus.OPEN);
        if (openTrades.isEmpty()) return;

        log.info("[Account {}] Squaring off {} position(s)", account.getId(), openTrades.size());

        for (Trade trade : openTrades) {
            try {
                Position position = positionRepository.findByTradeId(trade.getId()).orElse(null);
                if (position == null) continue;

                BigDecimal ltp = fetchOptionLtp(account.getId(), trade.getSymbolToken());
                exitStrategyService.forceSquareOff(trade, position, ltp != null ? ltp : BigDecimal.ZERO);
                log.info("[Account {}] Squared off: {}", account.getId(), trade.getTradingSymbol());
            } catch (Exception e) {
                log.error("[Account {}] Square-off error for trade {}: {}",
                        account.getId(), trade.getId(), e.getMessage());
            }
        }
    }

    private BigDecimal fetchSpotPrice(Long brokerAccountId, String exchange, String token, String label) {
        try {
            QuoteResponse quote = marketClient.getQuote(brokerAccountId, exchange, List.of(token), "LTP");
            if (quote != null && quote.getFetched() != null && !quote.getFetched().isEmpty()) {
                Double ltp = quote.getFetched().get(0).getLtp();
                if (ltp != null) return BigDecimal.valueOf(ltp);
            }
        } catch (Exception e) {
            log.warn("Could not fetch {} spot: {}", label, e.getMessage());
        }
        return null;
    }

    private BigDecimal fetchOptionLtp(Long brokerAccountId, String symbolToken) {
        if (symbolToken == null) return null;
        try {
            QuoteResponse quote = marketClient.getQuote(brokerAccountId, "NFO", List.of(symbolToken), "LTP");
            if (quote != null && quote.getFetched() != null && !quote.getFetched().isEmpty()) {
                Double ltp = quote.getFetched().get(0).getLtp();
                if (ltp != null) return BigDecimal.valueOf(ltp);
            }
        } catch (Exception e) {
            log.warn("Could not fetch option LTP for token {}: {}", symbolToken, e.getMessage());
        }
        return null;
    }

    private BigDecimal fetchAvailableCapital(Long brokerAccountId) {
        try {
            var funds = marketClient.getFunds(brokerAccountId);
            if (funds != null && funds.getAvailableCash() != null) {
                return new BigDecimal(funds.getAvailableCash());
            }
        } catch (Exception e) {
            log.warn("Could not fetch funds: {}", e.getMessage());
        }
        return BigDecimal.valueOf(100000); // fallback ₹1L
    }

    private String key(Long accountId, String index) {
        return accountId + ":" + index;
    }

    private void saveOpenPrice(BrokerAccount account, String indexName,
                                BigDecimal price, String source) {
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            DailyOpenPrice op = dailyOpenPriceRepository
                    .findByBrokerAccountIdAndIndexNameAndTradeDate(account.getId(), indexName, today)
                    .orElse(new DailyOpenPrice());
            op.setBrokerAccount(account);
            op.setIndexName(indexName);
            op.setOpenPrice(price);
            op.setTradeDate(today);
            op.setFetchedAt(java.time.Instant.now());
            op.setSource(source);
            dailyOpenPriceRepository.save(op);
        } catch (Exception e) {
            log.warn("Failed to save open price for {}: {}", indexName, e.getMessage());
        }
    }
}