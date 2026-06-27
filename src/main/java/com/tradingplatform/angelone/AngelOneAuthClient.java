package com.tradingplatform.angelone;

import com.tradingplatform.angelone.dto.AngelOneApiResponse;
import com.tradingplatform.angelone.dto.LoginRequest;
import com.tradingplatform.angelone.dto.LoginResponseData;
import com.tradingplatform.angelone.dto.RefreshTokenRequest;
import com.tradingplatform.config.AngelOneProperties;
import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.repository.BrokerAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Angel One authentication — supports BOTH:
 *
 * 1. Per-user login: loginForAccount(brokerAccountId)
 *    → reads credentials from broker_accounts table (each user's own Angel One)
 *
 * 2. Legacy single-account login: login() / ensureLoggedIn()
 *    → reads from .env (your account — used by test endpoints)
 */
@Component
public class AngelOneAuthClient {

    private static final Logger log = LoggerFactory.getLogger(AngelOneAuthClient.class);

    private static final String LOGIN_PATH   = "/rest/auth/angelbroking/user/v1/loginByPassword";
    private static final String REFRESH_PATH = "/rest/auth/angelbroking/jwt/v1/generateTokens";
    private static final String LOGOUT_PATH  = "/rest/secure/angelbroking/user/v1/logout";

    private final RestClient restClient;
    private final AngelOneProperties props;
    private final TotpGenerator totpGenerator;
    private final AngelOneTokenStore tokenStore;
    private final BrokerAccountRepository brokerAccountRepository;

    public AngelOneAuthClient(RestClient angelOneRestClient,
                               AngelOneProperties props,
                               TotpGenerator totpGenerator,
                               AngelOneTokenStore tokenStore,
                               BrokerAccountRepository brokerAccountRepository) {
        this.restClient = angelOneRestClient;
        this.props = props;
        this.totpGenerator = totpGenerator;
        this.tokenStore = tokenStore;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    // ─────────────────────────────────────────────────────
    // PER-USER LOGIN — reads credentials from DB
    // ─────────────────────────────────────────────────────

    /**
     * Login for a specific user's broker account.
     * Called from trade execution, signal generation etc.
     */
    public synchronized void ensureLoggedIn(Long brokerAccountId) {
        if (!tokenStore.hasValidSession(brokerAccountId)) {
            loginForAccount(brokerAccountId);
        }
    }

    public synchronized void loginForAccount(Long brokerAccountId) {
        BrokerAccount account = brokerAccountRepository.findById(brokerAccountId)
                .orElseThrow(() -> new AngelOneApiException(
                        "Broker account not found: " + brokerAccountId, "ACCOUNT_NOT_FOUND"));

        String totp = totpGenerator.generate(account.getTotpSecret());
        LoginRequest body = new LoginRequest(
                account.getClientCode(),
                account.getPassword(),
                totp
        );

        log.info("Logging in to Angel One for broker account {} (client: {})",
                brokerAccountId, account.getClientCode());

        AngelOneApiResponse<LoginResponseData> response = restClient.post()
                .uri(LOGIN_PATH)
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .header("apikey", account.getApiKey())
                .body(body)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<
                        AngelOneApiResponse<LoginResponseData>>() {});

        requireSuccess(response, "Login failed for account " + brokerAccountId);

        LoginResponseData data = response.getData();
        tokenStore.save(brokerAccountId, data.getJwtToken(), data.getRefreshToken(), data.getFeedToken());
        log.info("Angel One login successful for broker account {} (client: {})",
                brokerAccountId, account.getClientCode());
    }

    // ─────────────────────────────────────────────────────
    // LEGACY SINGLE-ACCOUNT LOGIN — reads from .env
    // Used by test endpoints (/api/test/angelone/*)
    // ─────────────────────────────────────────────────────

    public synchronized void ensureLoggedIn() {
        if (!tokenStore.hasValidSession()) {
            login();
        }
    }

    public synchronized void login() {
        String totp = totpGenerator.generate(props.getTotpSecret());
        LoginRequest body = new LoginRequest(props.getClientCode(), props.getPassword(), totp);

        log.info("Logging in to Angel One (legacy/test mode) for client {}", props.getClientCode());

        AngelOneApiResponse<LoginResponseData> response = restClient.post()
                .uri(LOGIN_PATH)
                .body(body)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<
                        AngelOneApiResponse<LoginResponseData>>() {});

        requireSuccess(response, "Login failed");

        LoginResponseData data = response.getData();
        tokenStore.save(data.getJwtToken(), data.getRefreshToken(), data.getFeedToken());
        log.info("Angel One login successful (legacy/test) for client {}", props.getClientCode());
    }

    public synchronized void refresh(Long brokerAccountId) {
        RefreshTokenRequest body = new RefreshTokenRequest(tokenStore.getRefreshToken(brokerAccountId));

        AngelOneApiResponse<LoginResponseData> response = restClient.post()
                .uri(REFRESH_PATH)
                .body(body)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<
                        AngelOneApiResponse<LoginResponseData>>() {});

        requireSuccess(response, "Token refresh failed");

        LoginResponseData data = response.getData();
        tokenStore.save(brokerAccountId, data.getJwtToken(),
                tokenStore.getRefreshToken(brokerAccountId),
                tokenStore.getFeedToken(brokerAccountId));
    }

    public synchronized void refresh() {
        RefreshTokenRequest body = new RefreshTokenRequest(tokenStore.getRefreshToken());
        AngelOneApiResponse<LoginResponseData> response = restClient.post()
                .uri(REFRESH_PATH)
                .body(body)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<
                        AngelOneApiResponse<LoginResponseData>>() {});
        requireSuccess(response, "Token refresh failed");
        LoginResponseData data = response.getData();
        tokenStore.save(data.getJwtToken(), tokenStore.getRefreshToken(), tokenStore.getFeedToken());
    }

    public synchronized void logout() {
        if (!tokenStore.hasValidSession()) return;
        try {
            restClient.post()
                    .uri(LOGOUT_PATH)
                    .header("Authorization", "Bearer " + tokenStore.getJwtToken())
                    .body(new ClientCodeBody(props.getClientCode()))
                    .retrieve()
                    .toBodilessEntity();
        } finally {
            tokenStore.clear();
        }
    }

    private void requireSuccess(AngelOneApiResponse<?> response, String contextMessage) {
        if (response == null || !response.isStatus() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "null response";
            String errorCode = response != null ? response.getErrorcode() : "UNKNOWN";
            throw new AngelOneApiException(contextMessage + ": " + message, errorCode);
        }
    }

    private record ClientCodeBody(String clientcode) {}
}