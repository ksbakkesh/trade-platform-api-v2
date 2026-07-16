package com.tradingplatform.angelone;

import com.tradingplatform.angelone.dto.AngelOneApiResponse;
import com.tradingplatform.angelone.dto.ProfileResponse;
import com.tradingplatform.angelone.dto.QuoteRequest;
import com.tradingplatform.angelone.dto.QuoteResponse;
import com.tradingplatform.angelone.dto.FundsResponse;
import org.springframework.core.ParameterizedTypeReference;
import com.tradingplatform.repository.BrokerAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class AngelOneMarketClient {

    private static final String PROFILE_PATH = "/rest/secure/angelbroking/user/v1/getProfile";
    private static final String QUOTE_PATH   = "/rest/secure/angelbroking/market/v1/quote/";
    private static final String FUNDS_PATH   = "/rest/secure/angelbroking/user/v1/getRMS";
    private static final String FUNDS_PATH_V2 = "/rest/secure/angelbroking/user/v1/getRMS";
    private static final String POSITION_PATH = "/rest/secure/angelbroking/order/v1/getPosition";
    private static final String ORDER_BOOK_PATH = "/rest/secure/angelbroking/order/v1/getOrderBook";

    private final RestClient restClient;
    private final AngelOneAuthClient authClient;
    private final AngelOneTokenStore tokenStore;
    private final BrokerAccountRepository brokerAccountRepository;

    public AngelOneMarketClient(RestClient angelOneRestClient,
                                 AngelOneAuthClient authClient,
                                 AngelOneTokenStore tokenStore,
                                 BrokerAccountRepository brokerAccountRepository) {
        this.restClient  = angelOneRestClient;
        this.authClient  = authClient;
        this.tokenStore  = tokenStore;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    // ─── PER-ACCOUNT METHODS (use these everywhere) ───────────────────────



    public String getRawQuote(Long brokerAccountId, String exchange, String token, String mode) {
        authClient.ensureLoggedIn(brokerAccountId);
        com.tradingplatform.angelone.dto.QuoteRequest body = new com.tradingplatform.angelone.dto.QuoteRequest(mode, java.util.Map.of(exchange, java.util.List.of(token)));
        return restClient.post()
                .uri(QUOTE_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .header("X-PrivateKey", getApiKey(brokerAccountId))
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public String getRawFunds(Long brokerAccountId) {
        authClient.ensureLoggedIn(brokerAccountId);
        return restClient.get()
                .uri(FUNDS_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .header("X-PrivateKey", getApiKey(brokerAccountId))
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .retrieve()
                .body(String.class);
    }

    public FundsResponse getFunds(Long brokerAccountId) {
        authClient.ensureLoggedIn(brokerAccountId);
        AngelOneApiResponse<FundsResponse> response = restClient.get()
                .uri(FUNDS_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .header("X-PrivateKey", getApiKey(brokerAccountId))
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        requireSuccess(response, "getFunds failed");
        return response.getData();
    }

    public List<Map<String, Object>> getLivePositions(Long brokerAccountId) {
        authClient.ensureLoggedIn(brokerAccountId);
        AngelOneApiResponse<List<Map<String, Object>>> response = restClient.get()
                .uri(POSITION_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .header("X-PrivateKey", getApiKey(brokerAccountId))
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) return List.of();
        return response.getData();
    }

    public ProfileResponse getProfile(Long brokerAccountId) {
        authClient.ensureLoggedIn(brokerAccountId);
        AngelOneApiResponse<ProfileResponse> response = restClient.get()
                .uri(PROFILE_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .header("X-PrivateKey", getApiKey(brokerAccountId))
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        requireSuccess(response, "getProfile failed");
        return response.getData();
    }

    public QuoteResponse getQuote(Long brokerAccountId, String exchange,
                                   List<String> symbolTokens, String mode) {
        authClient.ensureLoggedIn(brokerAccountId);
        QuoteRequest body = new QuoteRequest(mode, Map.of(exchange, symbolTokens));
        AngelOneApiResponse<QuoteResponse> response = restClient.post()
                .uri(QUOTE_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        requireSuccess(response, "getQuote failed");
        return response.getData();
    }

    public List<Map<String, Object>> getOrderBook(Long brokerAccountId) {
        authClient.ensureLoggedIn(brokerAccountId);
        AngelOneApiResponse<List<Map<String, Object>>> response = restClient.get()
                .uri(ORDER_BOOK_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken(brokerAccountId))
                .header("X-PrivateKey", getApiKey(brokerAccountId))
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) return List.of();
        return response.getData();
    }

    // ─── LEGACY SINGLE-ACCOUNT (kept for test endpoints only) ─────────────

    /** @deprecated Use getFunds(brokerAccountId) instead */
    @Deprecated
    public FundsResponse getFunds() {
        authClient.ensureLoggedIn();
        AngelOneApiResponse<FundsResponse> response = restClient.get()
                .uri(FUNDS_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        requireSuccess(response, "getFunds failed");
        return response.getData();
    }

    /** @deprecated Use getProfile(brokerAccountId) instead */
    @Deprecated
    public ProfileResponse getProfile() {
        authClient.ensureLoggedIn();
        AngelOneApiResponse<ProfileResponse> response = restClient.get()
                .uri(PROFILE_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        requireSuccess(response, "getProfile failed");
        return response.getData();
    }

    /** @deprecated Use getQuote(brokerAccountId, ...) instead */
    @Deprecated
    public QuoteResponse getQuote(String exchange, List<String> symbolTokens, String mode) {
        authClient.ensureLoggedIn();
        QuoteRequest body = new QuoteRequest(mode, Map.of(exchange, symbolTokens));
        AngelOneApiResponse<QuoteResponse> response = restClient.post()
                .uri(QUOTE_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken())
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        requireSuccess(response, "getQuote failed");
        return response.getData();
    }

    /** @deprecated Use getOrderBook(brokerAccountId) instead */
    @Deprecated
    public List<Map<String, Object>> getOrderBook() {
        authClient.ensureLoggedIn();
        AngelOneApiResponse<List<Map<String, Object>>> response = restClient.get()
                .uri(ORDER_BOOK_PATH)
                .header("Authorization", "Bearer " + tokenStore.getJwtToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.getData() == null) return List.of();
        return response.getData();
    }

    private String getApiKey(Long brokerAccountId) {
        return brokerAccountRepository.findById(brokerAccountId)
                .map(a -> a.getApiKey())
                .orElseThrow(() -> new AngelOneApiException("Broker account not found: " + brokerAccountId, "ACCOUNT_NOT_FOUND"));
    }

    private void requireSuccess(AngelOneApiResponse<?> response, String contextMessage) {
        if (response == null || !response.isStatus()) {
            String message   = response != null ? response.getMessage()  : "null response";
            String errorCode = response != null ? response.getErrorcode(): "UNKNOWN";
            throw new AngelOneApiException(contextMessage + ": " + message, errorCode);
        }
    }
}