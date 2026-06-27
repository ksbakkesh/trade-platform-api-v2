package com.tradingplatform.angelone;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds Angel One session tokens in memory — one session per broker account.
 *
 * Key   = brokerAccountId (Long)
 * Value = Session (jwt + refresh + feed tokens)
 *
 * When we add Redis later, swap ConcurrentHashMap for Redis keyed by brokerAccountId.
 * This supports multiple users each with their own Angel One account simultaneously.
 */
@Component
public class AngelOneTokenStore {

    private static final long JWT_TTL_SECONDS = 6 * 60 * 60; // 6 hours

    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public void save(Long brokerAccountId, String jwtToken, String refreshToken, String feedToken) {
        sessions.put(brokerAccountId, new Session(jwtToken, refreshToken, feedToken, Instant.now()));
    }

    public void clear(Long brokerAccountId) {
        sessions.remove(brokerAccountId);
    }

    public boolean hasValidSession(Long brokerAccountId) {
        Session session = sessions.get(brokerAccountId);
        if (session == null) return false;
        return Instant.now().isBefore(session.issuedAt.plusSeconds(JWT_TTL_SECONDS));
    }

    public String getJwtToken(Long brokerAccountId) {
        return requireSession(brokerAccountId).jwtToken;
    }

    public String getRefreshToken(Long brokerAccountId) {
        return requireSession(brokerAccountId).refreshToken;
    }

    public String getFeedToken(Long brokerAccountId) {
        return requireSession(brokerAccountId).feedToken;
    }

    // ── Legacy single-account support (used by existing test endpoints) ──
    // Falls back to the first active session found
    public boolean hasValidSession() {
        return sessions.values().stream()
                .anyMatch(s -> Instant.now().isBefore(s.issuedAt.plusSeconds(JWT_TTL_SECONDS)));
    }

    public String getJwtToken() {
        return sessions.values().stream()
                .filter(s -> Instant.now().isBefore(s.issuedAt.plusSeconds(JWT_TTL_SECONDS)))
                .findFirst()
                .map(s -> s.jwtToken)
                .orElseThrow(() -> new IllegalStateException("No active Angel One session"));
    }

    public String getRefreshToken() {
        return sessions.values().stream()
                .filter(s -> Instant.now().isBefore(s.issuedAt.plusSeconds(JWT_TTL_SECONDS)))
                .findFirst()
                .map(s -> s.refreshToken)
                .orElseThrow(() -> new IllegalStateException("No active Angel One session"));
    }

    public String getFeedToken() {
        return sessions.values().stream()
                .filter(s -> Instant.now().isBefore(s.issuedAt.plusSeconds(JWT_TTL_SECONDS)))
                .findFirst()
                .map(s -> s.feedToken)
                .orElseThrow(() -> new IllegalStateException("No active Angel One session"));
    }

    public void save(String jwtToken, String refreshToken, String feedToken) {
        // Legacy single-account save — uses account ID 1
        save(1L, jwtToken, refreshToken, feedToken);
    }

    public void clear() {
        sessions.clear();
    }

    private Session requireSession(Long brokerAccountId) {
        Session session = sessions.get(brokerAccountId);
        if (session == null) {
            throw new IllegalStateException(
                    "No Angel One session for account " + brokerAccountId + " — call login() first");
        }
        return session;
    }

    private record Session(String jwtToken, String refreshToken, String feedToken, Instant issuedAt) {}
}