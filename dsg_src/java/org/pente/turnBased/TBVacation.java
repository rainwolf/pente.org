package org.pente.turnBased;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

public class TBVacation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private int hoursLeft;
    private Date lastPinched;

    public int getHoursLeft() {
        return hoursLeft;
    }

    public void setHoursLeft(int hoursLeft) {
        this.hoursLeft = hoursLeft;
    }

    public Date getLastPinched() {
        return lastPinched;
    }

    public void setLastPinched(Date lastPinched) {
        this.lastPinched = lastPinched;
    }
}
