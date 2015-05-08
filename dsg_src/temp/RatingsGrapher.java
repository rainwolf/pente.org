package org.pente.tools;

import java.sql.*;
import java.util.*;
import java.io.*;

import org.apache.log4j.BasicConfigurator;
import org.pente.database.*;

public class RatingsGrapher {

    public static void main(String[] args) throws Throwable {
        BasicConfigurator.configure();
        
        String user = args[0];
        String password = args[1];
        String db = args[2];
        String host = args[3];

        RatingsGrapher grapher = new RatingsGrapher(new MySQLDBHandler(
            user, password, db, host));
    }
    
    private DBHandler dbHandler;
    public RatingsGrapher(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }
    
    public void createGraph(File outputFile) {
        
    }
}
