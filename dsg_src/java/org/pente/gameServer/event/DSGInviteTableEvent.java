package org.pente.gameServer.event;

public class DSGInviteTableEvent extends AbstractDSGTableEvent {

    private String toInvite;
	private String inviteText;
    
    public DSGInviteTableEvent() {
        super();
    }

    public DSGInviteTableEvent(String player, int table, String toInvite,
		String inviteText) {
        super(player, table);
        
        this.toInvite = toInvite;
		this.inviteText = inviteText;
    }

    public String getPlayerToInvite() {
        return toInvite;
    }
	public String getInviteText() {
		return inviteText;
	}
    
    public String toString() {
        return "invite " + toInvite + ", " + inviteText + " " + super.toString();
    }
}
