package org.pente.subscriptions.apple.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.pente.subscriptions.apple.AppleApi;
import org.pente.subscriptions.apple.AppleApiException;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleNotificationPage;
import org.pente.subscriptions.apple.AppleSubscriptionStatus;
import org.pente.subscriptions.apple.AppleTransaction;

/**
 * Apple's answers held in memory. Every answer is a copy, so the service cannot change the test's data.
 * The fail* counters make that many calls of a kind throw a retryable AppleApiException first.
 */
public class FakeAppleApi implements AppleApi {

    /** Get All Subscription Statuses answers, by the original transaction id asked for */
    public final Map<String, List<AppleSubscriptionStatus>> statuses = new HashMap<String, List<AppleSubscriptionStatus>>();
    /** Get Transaction Info answers by transaction id; a missing id is a non-retryable 404 */
    public final Map<String, AppleTransaction> transactions = new HashMap<String, AppleTransaction>();
    /** Get Transaction History answers, by the transaction id asked for */
    public final Map<String, List<AppleTransaction>> histories = new HashMap<String, List<AppleTransaction>>();
    /** Get Notification History pages, by env + ":" + paginationToken ("" for the first page) */
    public final Map<String, AppleNotificationPage> pages = new HashMap<String, AppleNotificationPage>();
    /** the [startMs, endMs] of every notification history call */
    public final List<long[]> historyWindows = new ArrayList<long[]>();
    /** "originalTransactionId:token" per successful setAppAccountToken */
    public final List<String> stamped = new ArrayList<String>();
    /** "method:id:ENV" per call */
    public final List<String> calls = new ArrayList<String>();
    public int failStatuses;
    public int failTransactionInfo;
    public int failHistory;
    public int failNotificationHistory;
    public int failStamps;

    private static AppleApiException transientFailure(String what) {
        return new AppleApiException("fake: " + what + " failed (503)", true);
    }

    public synchronized List<AppleSubscriptionStatus> subscriptionStatuses(AppleEnvironment env, String originalTransactionId)
            throws AppleApiException {
        calls.add("statuses:" + originalTransactionId + ":" + env);
        if (failStatuses > 0) {
            failStatuses--;
            throw transientFailure("statuses");
        }
        List<AppleSubscriptionStatus> answer = statuses.get(originalTransactionId);
        if (answer == null) {
            throw new AppleApiException("fake: 404 original transaction id not found", false);
        }
        List<AppleSubscriptionStatus> copy = new ArrayList<AppleSubscriptionStatus>();
        for (AppleSubscriptionStatus s : answer) {
            copy.add(s.copy());
        }
        return copy;
    }

    public synchronized AppleTransaction transactionInfo(AppleEnvironment env, String transactionId)
            throws AppleApiException {
        calls.add("transactionInfo:" + transactionId + ":" + env);
        if (failTransactionInfo > 0) {
            failTransactionInfo--;
            throw transientFailure("transactionInfo");
        }
        AppleTransaction t = transactions.get(transactionId);
        if (t == null) {
            throw new AppleApiException("fake: 404 transaction id not found", false);
        }
        return t.copy();
    }

    public synchronized List<AppleTransaction> transactionHistory(AppleEnvironment env, String anyTransactionId,
                                                                  String productId) throws AppleApiException {
        calls.add("history:" + anyTransactionId + ":" + env);
        if (failHistory > 0) {
            failHistory--;
            throw transientFailure("history");
        }
        List<AppleTransaction> copy = new ArrayList<AppleTransaction>();
        List<AppleTransaction> answer = histories.get(anyTransactionId);
        if (answer != null) {
            for (AppleTransaction t : answer) {
                if (productId.equals(t.productId)) {
                    copy.add(t.copy());
                }
            }
        }
        return copy;
    }

    public synchronized AppleNotificationPage notificationHistory(AppleEnvironment env, long startMs, long endMs,
                                                                  String paginationToken) throws AppleApiException {
        calls.add("notificationHistory:" + (paginationToken == null ? "" : paginationToken) + ":" + env);
        historyWindows.add(new long[] {startMs, endMs});
        if (failNotificationHistory > 0) {
            failNotificationHistory--;
            throw transientFailure("notificationHistory");
        }
        AppleNotificationPage page = pages.get(env + ":" + (paginationToken == null ? "" : paginationToken));
        return page == null ? new AppleNotificationPage() : page;
    }

    public synchronized void setAppAccountToken(AppleEnvironment env, String originalTransactionId, UUID token)
            throws AppleApiException {
        calls.add("setAppAccountToken:" + originalTransactionId + ":" + env);
        if (failStamps > 0) {
            failStamps--;
            throw transientFailure("setAppAccountToken");
        }
        stamped.add(originalTransactionId + ":" + token);
    }

    public synchronized String requestTestNotification(AppleEnvironment env) {
        calls.add("testNotification::" + env);
        return "test-token";
    }

    /** test helper: how many calls start with prefix */
    public synchronized int count(String prefix) {
        int n = 0;
        for (String c : calls) {
            if (c.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }
}
