package org.pente.subscriptions.play;

import java.util.ArrayList;
import java.util.List;

/** One page of purchases.voidedpurchases.list. */
public class VoidedPage {

    public List<String> orderIds = new ArrayList<String>();
    /** tokenPagination.nextPageToken, null on the last page */
    public String nextPageToken;
}
