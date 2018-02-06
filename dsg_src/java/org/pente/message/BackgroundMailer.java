package org.pente.message;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;


public class BackgroundMailer extends BackgroundWorker {

    private static Category log4j = 
        Category.getInstance(BackgroundMailer.class.getName());
    
    private String smtpHost;
    private int smtpPort;
    private String smtpUser;
    private String smtpPassword;
    
	private DSGPlayerStorer dsgPlayerStorer;
	public BackgroundMailer(String smtpHost, int smtpPort,
		String smtpUser, String smtpPassword, DSGPlayerStorer dsgPlayerStorer) {
		super("Mailer");
		
		this.smtpHost = smtpHost;
		this.smtpPort = smtpPort;
		this.smtpUser = smtpUser;
		this.smtpPassword = smtpPassword;
		this.dsgPlayerStorer = dsgPlayerStorer;
	}

	class Work {
		DSGMessage message;
		boolean ccSender;
		public Work(DSGMessage message, boolean ccSender) {
			this.message = message;
			this.ccSender = ccSender;
		}
	}
	public void mail(DSGMessage message, boolean ccSender) {
		doWork(new Work(message, ccSender));
	}
	public void internalDoWork(Object obj) {
		Work w = (Work) obj;
		DSGMessage m = w.message;

		try {
			log4j.info("Email message " + m.getMid() + ", cc=" + w.ccSender);
			
			Properties props = new Properties();
			props.put("mail.smtp.host", smtpHost);
			props.put("mail.smtp.user", smtpUser);
			props.put("mail.smtp.password", smtpPassword);
			props.put("mail.smtp.auth", "true");

	        Session session = Session.getDefaultInstance(props, null);
	        MimeMessage message = new MimeMessage(session);

		    DSGPlayerData fromData = dsgPlayerStorer.loadPlayer(m.getFromPid());
		    DSGPlayerData toData = dsgPlayerStorer.loadPlayer(m.getToPid());
		    
		    if (!toData.isActive()) {
		        return;
            }

	        message.setFrom(new InternetAddress(smtpUser, fromData.getName()));
	        message.addRecipient(Message.RecipientType.TO, 
	        	new InternetAddress(toData.getEmail(), toData.getName()));
	        if (w.ccSender) {
	        	message.addRecipient(Message.RecipientType.BCC,
	        		new InternetAddress(fromData.getEmail(), fromData.getName()));
	        }
	        //message.addRecipient(Message.RecipientType.BCC, 
	        //	new InternetAddress("dweebo@pente.org", "dweebo"));
	        
	        message.setSubject("Pente.org Message: " + m.getSubject());
	        message.setSentDate(m.getCreationDate());
	        message.setReplyTo(new Address[] { new InternetAddress("noreply@pente.org") });

	        String body = m.getBody() + "\n\n" +
	        	"This message was sent through http://pente.org.\n" +
	        	"DO NOT REPLY VIA EMAIL, instead view and reply at " +
	        	"https://pente.org/gameServer/mymessages?command=view&mid=" + m.getMid() + 
	        	"\n \n Email settings can be changed at https://pente.org/gameServer/myprofile/prefs";
		    message.setText(body);

	        Transport transport = session.getTransport("smtp");
	        transport.connect(smtpHost, smtpPort, smtpUser, smtpPassword);
	        transport.sendMessage(message, message.getAllRecipients());
	        transport.close();

       } catch (DSGPlayerStoreException dpse) {
    		log4j.error("Problem loading players to send dsg message " +
        		m.getMid(), dpse);
        } catch (Throwable t) {
        	log4j.error("Problem sending dsg message " + m.getMid(), t);
        }
	}
}
