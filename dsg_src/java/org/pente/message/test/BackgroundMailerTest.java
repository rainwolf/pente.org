package org.pente.message.test;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.message.*;

import org.apache.log4j.*;

public class BackgroundMailerTest {

    private static Category log4j = Category.getInstance(
		BackgroundMailerTest.class.getName());

	public static void main(String[] args) throws Throwable {

		BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
            args[0], args[1], args[2], args[3]);

		GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
		DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(dbHandler,
			gameVenueStorer);
		//GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
		//DSGMessageStorer dsgMessageStorer = new MySQLDSGMessageStorer(dbHandler);

		BackgroundMailer mailer = new BackgroundMailer(args[4], 
			Integer.parseInt(args[5]),
			args[6], args[7], dsgPlayerStorer);
		DSGMessage message = new DSGMessage();
		message.setBody("This is a test message #1.");
		message.setSubject("This is test subject.");
		message.setCreationDate(new java.util.Date());
		message.setFromPid(22000000000144L);
		message.setToPid(22000000000145L);

		mailer.mail(message, true);
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException ex) {}
		
		mailer.destroy();
	}
}
