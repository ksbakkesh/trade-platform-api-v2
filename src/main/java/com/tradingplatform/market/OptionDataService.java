package com.tradingplatform.market;

import com.tradingplatform.angelone.AngelOneMarketClient;
import com.tradingplatform.angelone.dto.QuoteResponse;
import com.tradingplatform.domain.enums.IndexName;
import com.tradingplatform.domain.enums.OptionType;
import com.tradingplatform.signal.MarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches real option market data from Angel One for signal generation.
 *
 * Responsibilities:
 *   1. Build option trading symbol from strike + expiry + type
 *   2. Look up Angel One instrument token for that symbol
 *   3. Fetch LTP and volume from Angel One quote API
 *   4. Estimate RSI from recent OHLC data (simplified 14-period RSI)
 *   5. Estimate delta from option price (simplified Black-Scholes proxy)
 *
 * Angel One symbol format:
 *   NIFTY  → "NIFTY27JUN2424100CE" (weekly, Tuesday expiry)
 *   SENSEX → "SENSEX27JUN2481000CE" (weekly, Thursday expiry)
 */
@Service
public class OptionDataService {

    private static final Logger log = LoggerFactory.getLogger(OptionDataService.class);

    // Angel One symbol token cache — in production this should be loaded
    // from Angel One's instrument master file (downloaded daily)
    // For now we use a lookup approach via the quote API
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    // Previous volume cache for volume ratio calculation
    private final Map<String, Long> previousVolumeCache = new ConcurrentHashMap<>();
    private final Map<String, Long> currentVolumeCache = new ConcurrentHashMap<>();

    // RSI calculation cache (stores last 14 candle price changes)
    private final Map<String, Double> rsiCache = new ConcurrentHashMap<>();

    private final AngelOneMarketClient marketClient;

    public OptionDataService(AngelOneMarketClient marketClient) {
        this.marketClient = marketClient;
    }

    /**
     * Builds a MarketSnapshot for the given option using live Angel One data.
     *
     * @param index      NIFTY or SENSEX
     * @param optionType CE or PE
     * @param strike     strike price from Gann calculation
     * @param spotPrice  current spot price (for delta estimation)
     * @return MarketSnapshot with real LTP, volume, estimated RSI and delta
     */
    public MarketSnapshot fetchOptionData(Long brokerAccountId, IndexName index, OptionType optionType,
                                           BigDecimal strike, BigDecimal spotPrice) {
        String tradingSymbol = buildTradingSymbol(index, strike, optionType);
        log.info("[{}] Fetching option data for: {}", index, tradingSymbol);

        try {
            // Fetch live quote from Angel One
            // Angel One NFO exchange for options
            QuoteResponse quote = marketClient.getQuote(brokerAccountId, 
                    "NFO", List.of(tradingSymbol), "FULL");

            if (quote == null || quote.getFetched() == null || quote.getFetched().isEmpty()) {
                log.warn("[{}] No quote data for {}", index, tradingSymbol);
                return fallbackSnapshot(spotPrice, tradingSymbol);
            }

            QuoteResponse.QuoteItem item = quote.getFetched().get(0);
            String symbolToken = item.getSymbolToken();
            Double ltp = item.getLtp();
            Long volume = item.getTradeVolume();

            if (ltp == null) {
                log.warn("[{}] LTP is null for {}", index, tradingSymbol);
                return fallbackSnapshot(spotPrice, tradingSymbol);
            }

            // Cache the token for future use
            if (symbolToken != null) tokenCache.put(tradingSymbol, symbolToken);

            // Volume ratio calculation
            String cacheKey = index + ":" + tradingSymbol;
            Long prevVolume = previousVolumeCache.getOrDefault(cacheKey, 5000L);
            Long currVolume = volume != null ? volume : 10000L;

            // Update volume cache for next cycle
            previousVolumeCache.put(cacheKey, currentVolumeCache.getOrDefault(cacheKey, currVolume));
            currentVolumeCache.put(cacheKey, currVolume);

            // Estimate RSI — simplified (use 65 as default until we have candle history)
            BigDecimal rsi = estimateRsi(index, optionType, ltp);

            // Estimate delta from moneyness
            BigDecimal delta = estimateDelta(optionType, BigDecimal.valueOf(ltp), spotPrice, strike);

            log.info("[{}] Option data: symbol={} ltp={} volume={} rsi={} delta={}",
                    index, tradingSymbol, ltp, currVolume, rsi, delta);

            return new MarketSnapshot(
                    BigDecimal.valueOf(ltp),
                    currVolume,
                    prevVolume,
                    rsi,
                    delta,
                    tradingSymbol,
                    symbolToken
            );

        } catch (Exception e) {
            log.error("[{}] Failed to fetch option data for {}: {}", index, tradingSymbol, e.getMessage());
            return fallbackSnapshot(spotPrice, tradingSymbol);
        }
    }

    /**
     * Builds Angel One option trading symbol.
     *
     * Format: {INDEX}{DD}{MON}{YY}{STRIKE}{TYPE}
     * Example: NIFTY27JUN2424100CE
     *          SENSEX27JUN2481000CE
     */
    private String buildTradingSymbol(IndexName index, BigDecimal strike, OptionType optionType) {
        LocalDate expiry = getNextExpiry(index);
        String day = String.format("%02d", expiry.getDayOfMonth());
        String month = expiry.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).toUpperCase();
        String year = String.valueOf(expiry.getYear()).substring(2);
        String strikeStr = String.valueOf(strike.intValue());
        String type = optionType == OptionType.CE ? "CE" : "PE";

        return index.name() + day + month + year + strikeStr + type;
    }

    /**
     * Gets next expiry date for the index.
     * NIFTY  → next Tuesday
     * SENSEX → next Thursday
     */
    private LocalDate getNextExpiry(IndexName index) {
        LocalDate today = LocalDate.now();
        java.time.DayOfWeek targetDay = index == IndexName.NIFTY
                ? java.time.DayOfWeek.TUESDAY
                : java.time.DayOfWeek.THURSDAY;

        LocalDate expiry = today;
        while (expiry.getDayOfWeek() != targetDay) {
            expiry = expiry.plusDays(1);
        }
        return expiry;
    }

    /**
     * Simplified RSI estimation.
     * In production: calculate from last 14 candles of OHLCV data.
     * For now: return a reasonable value based on option price movement.
     */
    private BigDecimal estimateRsi(IndexName index, OptionType optionType, Double ltp) {
        String key = index + ":" + optionType;
        Double prevLtp = rsiCache.get(key);
        rsiCache.put(key, ltp);

        if (prevLtp == null) return BigDecimal.valueOf(60); // neutral

        // Simple momentum proxy
        double change = (ltp - prevLtp) / prevLtp * 100;
        double rsi = 50 + (change * 5); // crude approximation
        rsi = Math.max(20, Math.min(80, rsi)); // clamp 20-80
        return BigDecimal.valueOf(rsi).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Estimates option delta from moneyness.
     * Real delta requires Black-Scholes with IV — this is a simple approximation:
     *   Deep ITM  → delta ~0.8
     *   ATM       → delta ~0.5
     *   OTM       → delta ~0.3
     *   Deep OTM  → delta ~0.1
     *
     * For CE: delta = N(d1) where d1 depends on spot/strike ratio
     * Approximation: delta ≈ 0.5 + 0.3 * (spot - strike) / strike
     */
    private BigDecimal estimateDelta(OptionType optionType, BigDecimal ltp,
                                      BigDecimal spot, BigDecimal strike) {
        if (spot == null || strike == null || strike.signum() == 0) {
            return BigDecimal.valueOf(0.50);
        }

        double moneyness = spot.subtract(strike).divide(strike, 4, RoundingMode.HALF_UP).doubleValue();
        double delta;

        if (optionType == OptionType.CE) {
            delta = 0.50 + (0.30 * moneyness);
        } else {
            delta = 0.50 - (0.30 * moneyness);
        }

        // Clamp to valid delta range
        delta = Math.max(0.10, Math.min(0.90, delta));
        return BigDecimal.valueOf(delta).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Fallback snapshot when Angel One data is unavailable.
     * Uses conservative values that will likely fail entry checks
     * (prevents trading when data is unreliable).
     */
    private MarketSnapshot fallbackSnapshot(BigDecimal spotPrice, String tradingSymbol) {
        log.warn("Using fallback snapshot for {} — entry conditions will likely fail", tradingSymbol);
        return new MarketSnapshot(
                BigDecimal.valueOf(50),    // ltp below threshold — will fail premium check
                1000L,                     // low volume — will fail volume check
                5000L,
                BigDecimal.valueOf(45),    // RSI below threshold — will fail RSI check
                BigDecimal.valueOf(0.50),
                tradingSymbol,
                null
        );
    }
}