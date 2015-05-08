package org.pente.message;

import java.util.Date;

public class DSGMessage {
	
	private int mid;
	private long fromPid;
	private long toPid;
	private String subject;
	private String body;
	private Date creationDate;
	private boolean read;
	private boolean viewable;
	
	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}
	public Date getCreationDate() {
		return creationDate;
	}
	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}
	public long getFromPid() {
		return fromPid;
	}
	public void setFromPid(long fromPid) {
		this.fromPid = fromPid;
	}
	public int getMid() {
		return mid;
	}
	public void setMid(int mid) {
		this.mid = mid;
	}
	public boolean isRead() {
		return read;
	}
	public void setRead(boolean read) {
		this.read = read;
	}
	public String getSubject() {
		return subject;
	}
	public void setSubject(String subject) {
		this.subject = subject;
	}
	public long getToPid() {
		return toPid;
	}
	public void setToPid(long toPid) {
		this.toPid = toPid;
	}
	public boolean isViewable() {
		return viewable;
	}
	public void setViewable(boolean viewable) {
		this.viewable = viewable;
	}
}
