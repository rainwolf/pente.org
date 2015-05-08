package org.pente.gameServer.client;

import java.awt.event.ActionListener;
import java.awt.Component;

// allows components to use buttons from both swing,awt
public interface DSGButton {

	public DSGButton createButton(String text, GameStyles gameStyles);
	public void addActionListener(ActionListener listener);
	public Component getButton();
	
}
