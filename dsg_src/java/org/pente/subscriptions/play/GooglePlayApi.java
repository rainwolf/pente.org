package org.pente.subscriptions.play;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

/**
 * PlayApi over google-api-services-androidpublisher v3 with a service-account key
 * (spec 2026-10-11 §4.1), the same credential pattern as the FCM key in DSGContextListener.
 */
public class GooglePlayApi implements PlayApi {

    private final AndroidPublisher publisher;
    private final String packageName;

    public GooglePlayApi(AndroidPublisher publisher, String packageName) {
        this.publisher = publisher;
        this.packageName = packageName;
    }

    public static GooglePlayApi fromKeyFile(String keyFile, String packageName) throws IOException, GeneralSecurityException {
        GoogleCredentials credentials;
        try (InputStream in = new FileInputStream(keyFile)) {
            credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER));
        }
        AndroidPublisher publisher = new AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("pente.org")
                .build();
        return new GooglePlayApi(publisher, packageName);
    }

    public PlaySubscription getSubscription(String purchaseToken) throws PlayApiException {
        try {
            return GooglePlayApiMapping.toSubscription(
                    publisher.purchases().subscriptionsv2().get(packageName, purchaseToken).execute());
        } catch (HttpResponseException e) {
            if (GooglePlayApiMapping.isNotFound(e.getStatusCode())) {
                return null;
            }
            throw new PlayApiException("subscriptionsv2.get failed with HTTP " + e.getStatusCode(), e);
        } catch (IOException e) {
            throw new PlayApiException("subscriptionsv2.get failed", e);
        }
    }

    public void acknowledgeSubscription(String purchaseToken) throws PlayApiException {
        try {
            publisher.purchases().subscriptions()
                    .acknowledge(packageName, PlayProducts.YEARLY, purchaseToken, new SubscriptionPurchasesAcknowledgeRequest())
                    .execute();
        } catch (IOException e) {
            throw new PlayApiException("subscriptions.acknowledge failed", e);
        }
    }

    public PlayProduct getProduct(String productId, String purchaseToken) throws PlayApiException {
        try {
            return GooglePlayApiMapping.toProduct(
                    publisher.purchases().products().get(packageName, productId, purchaseToken).execute());
        } catch (HttpResponseException e) {
            if (GooglePlayApiMapping.isNotFound(e.getStatusCode())) {
                return null;
            }
            throw new PlayApiException("products.get failed with HTTP " + e.getStatusCode(), e);
        } catch (IOException e) {
            throw new PlayApiException("products.get failed", e);
        }
    }

    public void consumeProduct(String productId, String purchaseToken) throws PlayApiException {
        try {
            publisher.purchases().products().consume(packageName, productId, purchaseToken).execute();
        } catch (IOException e) {
            throw new PlayApiException("products.consume failed", e);
        }
    }

    public void refundOrder(String orderId) throws PlayApiException {
        try {
            publisher.orders().refund(packageName, orderId).setRevoke(Boolean.TRUE).execute();
        } catch (IOException e) {
            throw new PlayApiException("orders.refund failed", e);
        }
    }

    public VoidedPage listVoided(long startTimeMs, String pageToken) throws PlayApiException {
        try {
            AndroidPublisher.Purchases.Voidedpurchases.List request = publisher.purchases().voidedpurchases()
                    .list(packageName)
                    .setStartTime(Long.valueOf(startTimeMs))
                    .setType(Integer.valueOf(1))
                    .setMaxResults(Long.valueOf(1000));
            if (pageToken != null) {
                request.setToken(pageToken);
            }
            return GooglePlayApiMapping.toVoidedPage(request.execute());
        } catch (IOException e) {
            throw new PlayApiException("voidedpurchases.list failed", e);
        }
    }
}
