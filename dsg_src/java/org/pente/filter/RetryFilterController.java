/**
 * RetryFilterController.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.filter;

import java.net.*;

public class RetryFilterController extends AbstractFilterController implements FilterListener {

    public static final int INFINITE_RETRIES = -1;
    public static final int NO_RETRIES = 0;

    private FilterController filterController;

    private int numRetries;
    private int maxRetries;
    private int retryInterval;

    public RetryFilterController(FilterController filterController, int maxRetries, int retryInterval) {
        this.filterController = filterController;
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;

        filterController.addListener(this);
    }

    public void run() {
        filterController.run();
    }

    public void lineFiltered(String line) {
        super.lineFiltered(line);
    }

    public void filteringComplete(boolean success, Exception ex) {

        if (success) {
            super.filteringComplete(success, ex);
        } else {
            if (ex instanceof SocketException) {

                if (maxRetries != INFINITE_RETRIES && numRetries++ >= maxRetries) {
                    super.filteringComplete(success, ex);
                } else {

                    try {
                        Thread.sleep(1000 * retryInterval);
                    } catch (InterruptedException ex2) {
                    }
                    // this isn't fool proof, lineFilters might need
                    // to be reset if some lines were already filtered
                    filterController.run();
                }
            } else {
                super.filteringComplete(success, ex);
            }
        }
    }
}