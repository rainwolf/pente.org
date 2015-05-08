package org.pente.gameServer.event;

public class DSGEmailGameReplyTableEvent extends AbstractDSGTableEvent {

    private String reply;

	public DSGEmailGameReplyTableEvent() {
		super();
	}

	public DSGEmailGameReplyTableEvent(String player, int table, String reply) {
		super(player, table);
        
        this.reply = reply;
	}

    public String getReply() {
        return reply;
    }
    
    public String toString() {
        return "email game reply: " + reply + " " + super.toString();
    }
}
