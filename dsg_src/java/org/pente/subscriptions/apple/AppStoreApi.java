package org.pente.subscriptions.apple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Category;

import com.apple.itunes.storekit.client.APIError;
import com.apple.itunes.storekit.client.APIException;
import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.client.GetTransactionHistoryVersion;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.HistoryResponse;
import com.apple.itunes.storekit.model.LastTransactionsItem;
import com.apple.itunes.storekit.model.NotificationHistoryRequest;
import com.apple.itunes.storekit.model.NotificationHistoryResponse;
import com.apple.itunes.storekit.model.NotificationHistoryResponseItem;
import com.apple.itunes.storekit.model.StatusResponse;
import com.apple.itunes.storekit.model.SubscriptionGroupIdentifierItem;
import com.apple.itunes.storekit.model.TransactionHistoryRequest;
import com.apple.itunes.storekit.model.UpdateAppAccountTokenRequest;

/**
 * AppleApi over Apple's AppStoreServerAPIClient (spec 2026-10-15 §4.1, §4.5): one client per environment, and every
 * signed transaction and renewal info Apple returns is verified with that environment's verifier before use.
 * Retryable: IOException, HTTP 429 and 5xx, the *_RETRYABLE API errors, and retryable verification failures.
 */
public class AppStoreApi implements AppleApi {

    private static final Category log4j = Category.getInstance(AppStoreApi.class.getName());

    /** safety stop for transaction-history paging (20 transactions a page) */
    static final int MAX_HISTORY_PAGES = 50;

    static final Set<APIError> RETRYABLE_ERRORS = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(
            APIError.GENERAL_INTERNAL_RETRYABLE, APIError.ACCOUNT_NOT_FOUND_RETRYABLE, APIError.APP_NOT_FOUND_RETRYABLE,
            APIError.ORIGINAL_TRANSACTION_ID_NOT_FOUND_RETRYABLE, APIError.RATE_LIMIT_EXCEEDED)));

    private final AppStoreServerAPIClient production;
    private final AppStoreServerAPIClient sandbox;
    private final AppStoreVerifier verifier;

    public AppStoreApi(AppStoreServerAPIClient production, AppStoreServerAPIClient sandbox, AppStoreVerifier verifier) {
        this.production = production;
        this.sandbox = sandbox;
        this.verifier = verifier;
    }

    /**
     * @throws IOException              the .p8 key file cannot be read
     * @throws IllegalArgumentException a key id, issuer id or bundle id is blank
     */
    public static AppStoreApi fromKeyFile(String keyFile, String keyId, String issuerId, String bundleId,
                                          AppStoreVerifier verifier) throws IOException {
        if (blank(keyFile) || blank(keyId) || blank(issuerId) || blank(bundleId)) {
            throw new IllegalArgumentException("appleIapKeyFile, appleIapKeyId, appleIapIssuerId and appleBundleId must be set");
        }
        String key = new String(Files.readAllBytes(Paths.get(keyFile.trim())), StandardCharsets.UTF_8);
        return new AppStoreApi(
                new AppStoreServerAPIClient(key, keyId.trim(), issuerId.trim(), bundleId.trim(), Environment.PRODUCTION),
                new AppStoreServerAPIClient(key, keyId.trim(), issuerId.trim(), bundleId.trim(), Environment.SANDBOX),
                verifier);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private AppStoreServerAPIClient client(AppleEnvironment env) {
        return env == AppleEnvironment.SANDBOX ? sandbox : production;
    }

    /** the AppleApiException for a failed call; public for AppStoreMappingTest */
    public static AppleApiException map(String what, Exception e) {
        if (e instanceof APIException) {
            APIException a = (APIException) e;
            int http = a.getHttpStatusCode();
            APIError error = a.getApiError();
            boolean retryable = http == 429 || http >= 500 || (error != null && RETRYABLE_ERRORS.contains(error));
            return new AppleApiException(what + " failed: HTTP " + http + " "
                    + (error != null ? error.name() : String.valueOf(a.getRawApiError())), retryable, e);
        }
        if (e instanceof AppleVerificationException) {
            return new AppleApiException(what + ": Apple's answer did not verify: " + e.getMessage(),
                    ((AppleVerificationException) e).isRetryable(), e);
        }
        if (e instanceof IOException) {
            return new AppleApiException(what + " failed: " + e, true, e);
        }
        // anything unexpected (a bug, bad input) will not fix itself: final, cause kept in the message
        log4j.warn("AppStoreApi: unexpected " + e.getClass().getName() + " in " + what, e);
        return new AppleApiException(what + " failed unexpectedly: " + e, false, e);
    }

    public List<AppleSubscriptionStatus> subscriptionStatuses(AppleEnvironment env, String originalTransactionId)
            throws AppleApiException {
        try {
            StatusResponse response = client(env).getAllSubscriptionStatuses(originalTransactionId, null);
            List<AppleSubscriptionStatus> result = new ArrayList<AppleSubscriptionStatus>();
            if (response.getData() == null) {
                return result;
            }
            for (SubscriptionGroupIdentifierItem group : response.getData()) {
                if (group.getLastTransactions() == null) {
                    continue;
                }
                for (LastTransactionsItem item : group.getLastTransactions()) {
                    AppleSubscriptionStatus s = new AppleSubscriptionStatus();
                    s.originalTransactionId = item.getOriginalTransactionId();
                    s.status = item.getStatus() != null ? item.getStatus().getValue().intValue()
                            : (item.getRawStatus() != null ? item.getRawStatus().intValue() : 0);
                    s.transaction = verifier.verifyTransaction(item.getSignedTransactionInfo(), env);
                    s.renewal = item.getSignedRenewalInfo() == null ? null : verifier.verifyRenewal(item.getSignedRenewalInfo(), env);
                    result.add(s);
                }
            }
            return result;
        } catch (Exception e) {
            throw map("getAllSubscriptionStatuses " + originalTransactionId, e);
        }
    }

    public AppleTransaction transactionInfo(AppleEnvironment env, String transactionId) throws AppleApiException {
        try {
            return verifier.verifyTransaction(client(env).getTransactionInfo(transactionId).getSignedTransactionInfo(), env);
        } catch (Exception e) {
            throw map("getTransactionInfo " + transactionId, e);
        }
    }

    public List<AppleTransaction> transactionHistory(AppleEnvironment env, String anyTransactionId, String productId)
            throws AppleApiException {
        try {
            TransactionHistoryRequest request = new TransactionHistoryRequest()
                    .productIds(Collections.singletonList(productId))
                    .sort(TransactionHistoryRequest.Order.ASCENDING);
            Map<String, AppleTransaction> byId = new LinkedHashMap<String, AppleTransaction>();
            String revision = null;
            boolean more;
            int pages = 0;
            do {
                HistoryResponse response = client(env).getTransactionHistory(anyTransactionId, revision, request,
                        GetTransactionHistoryVersion.V2);
                if (response.getSignedTransactions() != null) {
                    for (String signed : response.getSignedTransactions()) {
                        AppleTransaction t = verifier.verifyTransaction(signed, env);
                        byId.put(t.transactionId, t);
                    }
                }
                revision = response.getRevision();
                more = Boolean.TRUE.equals(response.getHasMore());
                pages++;
            } while (more && pages < MAX_HISTORY_PAGES);
            if (more) {
                log4j.warn("AppStoreApi: transaction history of " + anyTransactionId + " stopped at " + MAX_HISTORY_PAGES
                        + " pages with more still available");
            }
            return new ArrayList<AppleTransaction>(byId.values());
        } catch (Exception e) {
            throw map("getTransactionHistory " + anyTransactionId, e);
        }
    }

    public AppleNotificationPage notificationHistory(AppleEnvironment env, long startMs, long endMs, String paginationToken)
            throws AppleApiException {
        try {
            NotificationHistoryRequest request = new NotificationHistoryRequest()
                    .startDate(Long.valueOf(startMs)).endDate(Long.valueOf(endMs));
            NotificationHistoryResponse response = client(env).getNotificationHistory(paginationToken, request);
            AppleNotificationPage page = new AppleNotificationPage();
            if (response.getNotificationHistory() != null) {
                for (NotificationHistoryResponseItem item : response.getNotificationHistory()) {
                    page.signedPayloads.add(item.getSignedPayload());
                }
            }
            page.paginationToken = response.getPaginationToken();
            page.hasMore = Boolean.TRUE.equals(response.getHasMore());
            return page;
        } catch (Exception e) {
            throw map("getNotificationHistory " + env, e);
        }
    }

    public void setAppAccountToken(AppleEnvironment env, String originalTransactionId, UUID token) throws AppleApiException {
        try {
            client(env).setAppAccountToken(originalTransactionId, new UpdateAppAccountTokenRequest(token));
        } catch (Exception e) {
            throw map("setAppAccountToken " + originalTransactionId, e);
        }
    }

    public String requestTestNotification(AppleEnvironment env) throws AppleApiException {
        try {
            return client(env).requestTestNotification().getTestNotificationToken();
        } catch (Exception e) {
            throw map("requestTestNotification " + env, e);
        }
    }
}
