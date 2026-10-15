package org.pente.subscriptions.apple;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.pente.subscriptions.play.PlayProducts;

/**
 * The App Store Connect products and what each grants (spec 2026-10-15 §3). The product id that decides a
 * grant is the one in Apple's signed transaction, checked against this whitelist; anything else is refused.
 */
public final class AppleProducts {

    /** the existing auto-renewing yearly subscription, the same id the legacy verifyReceipt path uses */
    public static final String YEARLY = "1YRNOADSORLIMITS";
    public static final String MONTH_PASS = PlayProducts.MONTH_PASS;
    public static final String VACATION_10 = PlayProducts.VACATION_10;
    public static final String VACATION_30 = PlayProducts.VACATION_30;
    public static final String VACATION_60 = PlayProducts.VACATION_60;

    /** GET /gameServer/appStorePurchase "products", in display order */
    public static final List<String> ON_SALE = Collections.unmodifiableList(
            Arrays.asList(YEARLY, MONTH_PASS, VACATION_10, VACATION_30, VACATION_60));

    private AppleProducts() {
    }

    public static boolean isSubscription(String productId) {
        return YEARLY.equals(productId);
    }

    public static boolean isOneTime(String productId) {
        return MONTH_PASS.equals(productId) || vacationDays(productId) > 0;
    }

    public static boolean isKnown(String productId) {
        return isSubscription(productId) || isOneTime(productId);
    }

    /** 10, 30 or 60 for the vacation consumables, else 0 */
    public static int vacationDays(String productId) {
        return PlayProducts.vacationDays(productId);
    }

    /** the dsg_subscribers level a product grants: YEARLY_LEVEL, MONTH_LEVEL, or 0 for vacation days */
    public static int level(String productId) {
        if (isSubscription(productId)) {
            return PlayProducts.YEARLY_LEVEL;
        }
        if (MONTH_PASS.equals(productId)) {
            return PlayProducts.MONTH_LEVEL;
        }
        return 0;
    }
}
