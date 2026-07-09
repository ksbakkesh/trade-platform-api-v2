package com.tradingplatform.api;

import com.tradingplatform.angelone.AngelOneMarketClient;
import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.repository.BrokerAccountRepository;
import com.tradingplatform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AngelOneMarketClient marketClient;
    private final BrokerAccountRepository brokerAccountRepository;
    private final UserRepository userRepository;

    public DashboardController(AngelOneMarketClient marketClient,
                                BrokerAccountRepository brokerAccountRepository,
                                UserRepository userRepository) {
        this.marketClient = marketClient;
        this.brokerAccountRepository = brokerAccountRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/funds-raw")
    public ResponseEntity<?> getFundsRaw(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            return ResponseEntity.ok(marketClient.getRawFunds(account.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/funds")
    public ResponseEntity<?> getFunds(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            return ResponseEntity.ok(marketClient.getFunds(account.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/live-positions")
    public ResponseEntity<?> getLivePositions(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            return ResponseEntity.ok(marketClient.getLivePositions(account.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/order-book")
    public ResponseEntity<?> getOrderBook(Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            return ResponseEntity.ok(marketClient.getOrderBook(account.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/quote")
    public ResponseEntity<?> getQuote(@RequestParam String exchange,
                                       @RequestParam String token,
                                       @RequestParam String mode,
                                       Authentication auth) {
        try {
            BrokerAccount account = resolveAccount(auth);
            return ResponseEntity.ok(marketClient.getQuote(account.getId(), exchange, List.of(token), mode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private BrokerAccount resolveAccount(Authentication auth) {
        Long userId = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
        return brokerAccountRepository
                .findByUserIdAndBrokerName(userId, "ANGEL_ONE")
                .orElseThrow(() -> new RuntimeException("No broker account connected"));
    }
}
