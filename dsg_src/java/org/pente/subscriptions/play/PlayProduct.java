package org.pente.subscriptions.play;

/** The fields of purchases.products.get (ProductPurchase) this feature reads. */
public class PlayProduct {

    /** may be absent in the API response */
    public String productId;
    /** 0 purchased, 1 canceled, 2 pending */
    public Integer purchaseState;
    /** absent or 1; multi-quantity purchases stay disabled */
    public Integer quantity;
    /** 0 yet to be consumed, 1 consumed */
    public Integer consumptionState;
    /** 0 yet to be acknowledged, 1 acknowledged */
    public Integer acknowledgementState;
    /** absent for promo-code purchases */
    public String orderId;
    public String obfuscatedAccountId;
}
