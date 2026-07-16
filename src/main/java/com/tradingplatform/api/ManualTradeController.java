package com.tradingplatform.api;

import com.tradingplatform.angelone.AngelOneAuthClient;
import com.tradingplatform.angelone.AngelOneOrderClient;
import com.tradingplatform.angelone.AngelOneTokenStore;
import com.tradingplatform.angelone.dto.PlaceOrderRequest;
import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.domain.StrategySettings;
import com.tradingplatform.domain.enums.IndexName;
import com.tradingplatform.repository.BrokerAccountRepository;
import com.tradingplatform.repository.StrategySettingsRepository;
import com.tradingplatform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class ManualTradeController {

    private final BrokerAccountRepository brokerAccountRepository;
    private final UserRepository userRepository;
    private final StrategySettingsRepository strategySettingsRepository;
    private final AngelOneOrderClient orderClient;
    private final AngelOneAuthClient authClient;
    private final AngelOneTokenStore tokenStore;

    public ManualTradeController(BrokerAccountRepository brokerAccountRepository,
                                  UserRepository userRepository,
                                  StrategySettingsRepository strategySettingsRepository,
                                  AngelOneOrderClient orderClient,
                                  AngelOneAuthClient authClient,
                                  AngelOneTokenStore tokenStore) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.userRepository = userRepository;
        this.strategySettingsRepository = strategySettingsRepository;
        this.orderClient = orderClient;
        this.authClient = authClient;
        this.tokenStore = tokenStore;
    }

    /** Start auto-trading for logged-in user */
    @PostMapping("/start")
    public ResponseEntity<?> startTrading(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            updateAutoTrading(account.getId(), true);
            return ResponseEntity.ok(Map.of("message", "Auto-trading started", "status", "ACTIVE"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Stop auto-trading for logged-in user */
    @PostMapping("/stop")
    public ResponseEntity<?> stopTrading(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            updateAutoTrading(account.getId(), false);
            return ResponseEntity.ok(Map.of("message", "Auto-trading stopped", "status", "STOPPED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Manual buy order */
    @PostMapping("/buy")
    public ResponseEntity<?> manualBuy(@RequestBody ManualOrderRequest req, Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            authClient.ensureLoggedIn(account.getId());

            PlaceOrderRequest order = new PlaceOrderRequest();
            order.setTradingsymbol(req.tradingSymbol());
            order.setSymboltoken(req.symbolToken());
            order.setTransactiontype("BUY");
            order.setExchange(req.exchange() != null ? req.exchange() : "NFO");
            order.setQuantity(String.valueOf(req.quantity()));
            order.setOrdertype("MARKET");
            order.setProducttype("INTRADAY");

            // Use per-account token
            var response = orderClient.placeOrderForAccount(account.getId(), order);
            return ResponseEntity.ok(Map.of("message", "Buy order placed", "orderId", response.getOrderid()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Manual exit/sell order */
    @PostMapping("/exit")
    public ResponseEntity<?> manualExit(@RequestBody ManualOrderRequest req, Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            authClient.ensureLoggedIn(account.getId());

            PlaceOrderRequest order = new PlaceOrderRequest();
            order.setTradingsymbol(req.tradingSymbol());
            order.setSymboltoken(req.symbolToken());
            order.setTransactiontype("SELL");
            order.setExchange(req.exchange() != null ? req.exchange() : "NFO");
            order.setQuantity(String.valueOf(req.quantity()));
            order.setOrdertype("MARKET");
            order.setProducttype("INTRADAY");

            var response = orderClient.placeOrderForAccount(account.getId(), order);
            return ResponseEntity.ok(Map.of("message", "Exit order placed", "orderId", response.getOrderid()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Get current auto-trading status */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            var nifty = strategySettingsRepository.findByBrokerAccountIdAndIndexName(account.getId(), IndexName.NIFTY);
            var sensex = strategySettingsRepository.findByBrokerAccountIdAndIndexName(account.getId(), IndexName.SENSEX);
            return ResponseEntity.ok(Map.of(
                "niftyAutoTrading", nifty.map(StrategySettings::isAutoTradingEnabled).orElse(false),
                "sensexAutoTrading", sensex.map(StrategySettings::isAutoTradingEnabled).orElse(false)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void updateAutoTrading(Long accountId, boolean enabled) {
        strategySettingsRepository.findByBrokerAccountIdAndIndexName(accountId, IndexName.NIFTY)
                .ifPresent(s -> { s.setAutoTradingEnabled(enabled); strategySettingsRepository.save(s); });
        strategySettingsRepository.findByBrokerAccountIdAndIndexName(accountId, IndexName.SENSEX)
                .ifPresent(s -> { s.setAutoTradingEnabled(enabled); strategySettingsRepository.save(s); });
    }

    private BrokerAccount resolveAccount(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .flatMap(u -> brokerAccountRepository.findByUserIdAndBrokerName(u.getId(), "ANGEL_ONE"))
                .orElseThrow(() -> new RuntimeException("No broker account found"));
    }

    public record ManualOrderRequest(
        String tradingSymbol,
        String symbolToken,
        String exchange,
        int quantity
    ) {}
}
