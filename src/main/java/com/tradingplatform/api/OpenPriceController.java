package com.tradingplatform.api;

import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.domain.DailyOpenPrice;
import com.tradingplatform.repository.BrokerAccountRepository;
import com.tradingplatform.repository.DailyOpenPriceRepository;
import com.tradingplatform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/open-price")
public class OpenPriceController {

    private final DailyOpenPriceRepository openPriceRepository;
    private final UserRepository userRepository;
    private final BrokerAccountRepository brokerAccountRepository;

    public OpenPriceController(DailyOpenPriceRepository openPriceRepository,
                                UserRepository userRepository,
                                BrokerAccountRepository brokerAccountRepository) {
        this.openPriceRepository = openPriceRepository;
        this.userRepository = userRepository;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    /** Get today's open prices for current user */
    @GetMapping("/today")
    public ResponseEntity<?> getTodayOpenPrices(Authentication auth) {
        java.util.HashMap<String, Object> result = new java.util.HashMap<>();
        result.put("nifty", null);
        result.put("sensex", null);

        if (auth == null) return ResponseEntity.ok(result);
        Long accountId = getAccountId(auth);
        if (accountId == null) return ResponseEntity.ok(result);

        LocalDate today = LocalDate.now();
        openPriceRepository.findByBrokerAccountIdAndIndexNameAndTradeDate(accountId, "NIFTY", today)
                .ifPresent(p -> result.put("nifty", Map.of(
                        "openPrice", p.getOpenPrice(),
                        "source", p.getSource(),
                        "fetchedAt", p.getFetchedAt().toString()
                )));
        openPriceRepository.findByBrokerAccountIdAndIndexNameAndTradeDate(accountId, "SENSEX", today)
                .ifPresent(p -> result.put("sensex", Map.of(
                        "openPrice", p.getOpenPrice(),
                        "source", p.getSource(),
                        "fetchedAt", p.getFetchedAt().toString()
                )));
        return ResponseEntity.ok(result);
    }

    /** Manually save open price (when user enters it on dashboard) */
    @PostMapping("/manual")
    public ResponseEntity<?> saveManualOpenPrice(@RequestBody ManualOpenPriceRequest req,
                                                  Authentication auth) {
        Long accountId = getAccountId(auth);
        if (accountId == null) return ResponseEntity.notFound().build();

        BrokerAccount account = brokerAccountRepository.findById(accountId).orElse(null);
        if (account == null) return ResponseEntity.notFound().build();

        LocalDate today = LocalDate.now();
        DailyOpenPrice price = openPriceRepository
                .findByBrokerAccountIdAndIndexNameAndTradeDate(accountId, req.indexName(), today)
                .orElse(new DailyOpenPrice());

        price.setBrokerAccount(account);
        price.setIndexName(req.indexName());
        price.setOpenPrice(req.openPrice());
        price.setTradeDate(today);
        price.setSource("MANUAL");
        openPriceRepository.save(price);

        return ResponseEntity.ok(Map.of("message", "Open price saved", "openPrice", req.openPrice()));
    }

    private Long getAccountId(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName())
                .flatMap(u -> brokerAccountRepository.findByUserIdAndBrokerName(u.getId(), "ANGEL_ONE"))
                .map(BrokerAccount::getId)
                .orElse(null);
    }


    /** Save open price for ALL active broker accounts (called by ADMIN) */
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcastOpenPrice(@RequestBody ManualOpenPriceRequest req) {
        LocalDate today = LocalDate.now();
        java.util.List<BrokerAccount> accounts = brokerAccountRepository.findAll().stream()
                .filter(BrokerAccount::isActive).toList();
        for (BrokerAccount account : accounts) {
            DailyOpenPrice price = openPriceRepository
                    .findByBrokerAccountIdAndIndexNameAndTradeDate(account.getId(), req.indexName(), today)
                    .orElse(new DailyOpenPrice());
            price.setBrokerAccount(account);
            price.setIndexName(req.indexName());
            price.setOpenPrice(req.openPrice());
            price.setTradeDate(today);
            price.setSource("MANUAL");
            openPriceRepository.save(price);
        }
        return ResponseEntity.ok(Map.of("message", "Open price broadcast to " + accounts.size() + " accounts"));
    }


    public record ManualOpenPriceRequest(String indexName, BigDecimal openPrice) {}
}