/**
 * DSGUpdatePlayerDataEvent.java
 * Copyright (C) 2003 Dweebo's Stone Games (http://pente.org/)
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
package org.pente.gameServer.event;

import org.pente.gameServer.core.DSGPlayerData;

public class DSGUpdatePlayerDataEvent extends AbstractDSGEvent {
    private static final long serialVersionUID = -2187851276040693882L;

    private DSGPlayerData data;

    public DSGUpdatePlayerDataEvent(DSGPlayerData data) {
        DSGPlayerData dataCopy = (DSGPlayerData) data.clone();
        dataCopy.setPassword("");
        dataCopy.setEmail("");
        dataCopy.setParentPid(null);
        dataCopy.setMobileAgeBand(null);
        this.data = dataCopy;
    }

    public DSGPlayerData getDSGPlayerData() {
        return data;
    }

    public String toString() {
        String s = "update player data for: " +
                ((data == null) ? "null" : data.getName());
        return s;
    }
}
