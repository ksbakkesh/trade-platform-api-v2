package com.tradingplatform.api;

import com.tradingplatform.domain.User;
import com.tradingplatform.domain.UserPermissions;
import com.tradingplatform.repository.UserPermissionsRepository;
import com.tradingplatform.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
public class PermissionsController {

    private final UserPermissionsRepository permissionsRepository;
    private final UserRepository userRepository;

    public PermissionsController(UserPermissionsRepository permissionsRepository,
                                  UserRepository userRepository) {
        this.permissionsRepository = permissionsRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/my")
    public ResponseEntity<?> myPermissions(Authentication auth) {
        if (auth == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();;
        return userRepository.findByEmail(auth.getName())
                .flatMap(u -> permissionsRepository.findByUserId(u.getId()))
                .map(p -> ResponseEntity.ok(toMap(p)))
                .orElse(ResponseEntity.ok(defaultPermissions()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getPermissions(@PathVariable Long userId) {
        return permissionsRepository.findByUserId(userId)
                .map(p -> ResponseEntity.ok(toMap(p)))
                .orElse(ResponseEntity.ok(defaultPermissions()));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<?> savePermissions(@PathVariable Long userId,
                                              @RequestBody Map<String, Boolean> body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        UserPermissions p = permissionsRepository.findByUserId(userId)
                .orElse(new UserPermissions());
        p.setUser(user);
        applyMap(p, body);
        p.setUpdatedAt(Instant.now());
        permissionsRepository.save(p);
        return ResponseEntity.ok(toMap(p));
    }

    private Map<String, Object> toMap(UserPermissions p) {
        return Map.ofEntries(
                Map.entry("dashboard",        p.isDashboard()),
                Map.entry("marketOverview",   p.isMarketOverview()),
                Map.entry("liveSignals",      p.isLiveSignals()),
                Map.entry("positions",        p.isPositions()),
                Map.entry("tradeHistory",     p.isTradeHistory()),
                Map.entry("orders",           p.isOrders()),
                Map.entry("riskManagement",   p.isRiskManagement()),
                Map.entry("fundsMargin",      p.isFundsMargin()),
                Map.entry("brokerSetup",      p.isBrokerSetup()),
                Map.entry("strategySetup",    p.isStrategySetup()),
                Map.entry("strategySettings", p.isStrategySettings()),
                Map.entry("configuration",    p.isConfiguration()),
                Map.entry("logs",             p.isLogs()),
                Map.entry("reports",          p.isReports()),
                Map.entry("userManagement",   p.isUserManagement())
        );
    }

    private void applyMap(UserPermissions p, Map<String, Boolean> m) {
        if (m.containsKey("dashboard"))        p.setDashboard(m.get("dashboard"));
        if (m.containsKey("marketOverview"))   p.setMarketOverview(m.get("marketOverview"));
        if (m.containsKey("liveSignals"))      p.setLiveSignals(m.get("liveSignals"));
        if (m.containsKey("positions"))        p.setPositions(m.get("positions"));
        if (m.containsKey("tradeHistory"))     p.setTradeHistory(m.get("tradeHistory"));
        if (m.containsKey("orders"))           p.setOrders(m.get("orders"));
        if (m.containsKey("riskManagement"))   p.setRiskManagement(m.get("riskManagement"));
        if (m.containsKey("fundsMargin"))      p.setFundsMargin(m.get("fundsMargin"));
        if (m.containsKey("brokerSetup"))      p.setBrokerSetup(m.get("brokerSetup"));
        if (m.containsKey("strategySetup"))    p.setStrategySetup(m.get("strategySetup"));
        if (m.containsKey("strategySettings")) p.setStrategySettings(m.get("strategySettings"));
        if (m.containsKey("configuration"))    p.setConfiguration(m.get("configuration"));
        if (m.containsKey("logs"))             p.setLogs(m.get("logs"));
        if (m.containsKey("reports"))          p.setReports(m.get("reports"));
        if (m.containsKey("userManagement"))   p.setUserManagement(m.get("userManagement"));
    }

    private Map<String, Object> defaultPermissions() {
        return Map.ofEntries(
                Map.entry("dashboard", true), Map.entry("marketOverview", false),
                Map.entry("liveSignals", true), Map.entry("positions", true),
                Map.entry("tradeHistory", true), Map.entry("orders", false),
                Map.entry("riskManagement", true), Map.entry("fundsMargin", false),
                Map.entry("brokerSetup", false), Map.entry("strategySetup", true), Map.entry("strategySettings", false),
                Map.entry("configuration", false), Map.entry("logs", false),
                Map.entry("reports", false), Map.entry("userManagement", false)
        );
    }
}