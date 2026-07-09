package com.tradingplatform.config;

import com.tradingplatform.angelone.AngelOneAuthClient;
import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.repository.BrokerAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup, pre-login all active broker accounts so the token store
 * is warm and dashboard loads instantly without waiting for first request.
 */
@Component
public class BrokerSessionInitializer {

    private static final Logger log = LoggerFactory.getLogger(BrokerSessionInitializer.class);

    private final BrokerAccountRepository brokerAccountRepository;
    private final AngelOneAuthClient angelOneAuthClient;

    public BrokerSessionInitializer(BrokerAccountRepository brokerAccountRepository,
                                     AngelOneAuthClient angelOneAuthClient) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.angelOneAuthClient = angelOneAuthClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSessions() {
        List<BrokerAccount> accounts = brokerAccountRepository.findAll().stream()
                .filter(BrokerAccount::isActive)
                .toList();

        log.info("Pre-logging in {} active broker account(s) on startup...", accounts.size());

        for (BrokerAccount account : accounts) {
            try {
                angelOneAuthClient.loginForAccount(account.getId());
                log.info("✅ Pre-login success for account {} ({})", account.getId(), account.getClientCode());
            } catch (Exception e) {
                log.warn("⚠️ Pre-login failed for account {} ({}): {}", 
                    account.getId(), account.getClientCode(), e.getMessage());
            }
        }
    }
}
