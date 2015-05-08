/** SimpleDSGDonationData.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.core;

import java.util.Date;

public class SimpleDSGDonationData implements DSGDonationData {

	private long pid;
	private String name;
	private double amount;
	private Date donationDate;


    public long getPid() {
		return pid;
	}

	public void setPid(long pid) {
		this.pid = pid;
	}

	public void setName(String name) {
    	this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setAmount(double amount) {
    	this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    public void setDonationDate(Date donationDate) {
    	this.donationDate = donationDate;
    }

    public Date getDonationDate() {
        return donationDate;
    }
}

