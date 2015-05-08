package org.pente.gameServer.event;

public abstract class AbstractDSGTableErrorEvent extends AbstractDSGTableEvent implements DSGTableErrorEvent {

	private int error;

	public AbstractDSGTableErrorEvent() {
	}

	public AbstractDSGTableErrorEvent(String player, int table, int error) {
		super(player, table);
		
		setError(error);
	}
	
	public void setError(int error) {
		this.error = error;
	}
	public int getError() {
		return error;
	}
	
	public String toString() {
		return super.toString() + " error " + getError();
	}
}

