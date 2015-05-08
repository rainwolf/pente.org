/** SendMail2.java
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

package org.pente.gameServer.server;

import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;

public class SendMail2 {

    private static Category log4j = 
        Category.getInstance(SendMail2.class.getName());

    public static void sendMailSaveInDb(
        String fromName,
        String fromEmail,
        long toPid,
        String toName,
        String toEmail,
        String subject,
        String messageStr,
        boolean sendAsAttachment,
        String attachmentTitle,
        MySQLDSGReturnEmailStorer returnEmailStorer) throws Throwable {
        
        String messageID = 
            sendMail(fromName, fromEmail, toName, toEmail, subject, messageStr,
                     sendAsAttachment, attachmentTitle);
                     
        returnEmailStorer.insertEmail(toPid, messageID, toEmail);
    }
    
    public static String sendMail(
        String fromName,
        String fromEmail,
        String toName,
        String toEmail,
        String subject,
        String messageStr,
        boolean sendAsAttachment,
        String attachmentTitle) throws Throwable {
        
        return sendMail(fromName, fromEmail, toName, toEmail, null, null,
        	subject, messageStr, sendAsAttachment, attachmentTitle);
    }
        
    public static String sendMail(
        String fromName,
        String fromEmail,
        String toName,
        String toEmail,
    	String ccName,
        String ccEmail,
        String subject,
        String messageStr,
        boolean sendAsAttachment,
        String attachmentTitle) throws Throwable {
                    
        String mailHost = System.getProperty("mail.smtp.host");
        String user = System.getProperty("mail.smtp.user");
        String password = System.getProperty("mail.smtp.password");
        System.setProperty("mail.smtp.auth", "true");

        Session session = Session.getDefaultInstance(System.getProperties(), null);
        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(fromEmail, fromName));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail, toName));
        if (ccName != null && ccEmail != null) {
            message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccEmail, ccName));
        }
        message.setSubject(subject);
        message.setSentDate(new java.util.Date());
        
        if (sendAsAttachment) {
        	BodyPart messageBodyPart = new MimeBodyPart();
        	messageBodyPart.setText(messageStr);

        	BodyPart attachmentBodyPart = new MimeBodyPart();
        	attachmentBodyPart.setText(messageStr);
        	attachmentBodyPart.setFileName(attachmentTitle);
        	
        	Multipart multipart = new MimeMultipart();
        	multipart.addBodyPart(messageBodyPart);
        	multipart.addBodyPart(attachmentBodyPart);
        	
        	message.setContent(multipart);
        }
        else {
	        message.setText(messageStr);
        }

        Transport transport = session.getTransport("smtp");
        transport.connect(mailHost, user, password);
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();
        
        return message.getMessageID();
    }
}