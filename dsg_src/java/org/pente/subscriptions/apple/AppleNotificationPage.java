package org.pente.subscriptions.apple;

import java.util.ArrayList;
import java.util.List;

/** One page of Get Notification History: still-signed payloads, verified one by one by the replay. */
public class AppleNotificationPage {

    public final List<String> signedPayloads = new ArrayList<String>();
    public String paginationToken;
    public boolean hasMore;
}
