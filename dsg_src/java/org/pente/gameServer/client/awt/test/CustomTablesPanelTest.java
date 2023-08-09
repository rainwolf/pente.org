package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.game.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;

public class CustomTablesPanelTest {

    private static final GameStyles gameStyle = new GameStyles(
            new Color(0, 0, 153), //board back
            new Color(188, 188, 188), //button back
            Color.white, //button fore
            new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
            Color.white, //player 1 back
            Color.black, //player 1 fore
            Color.black, //player 2 back
            Color.white, //player 2 fore
            new Color(51, 102, 204)); //watcher

    public static void main(String[] args) {

        final Frame f = new Frame("CustomTablePanel");
        f.setLayout(new BorderLayout(2, 2));

        DSGPlayerData me = new SimpleDSGPlayerData();
        me.setName("dweebo");
        me.setPlayerType(DSGPlayerData.HUMAN);
        final CustomTablesPanel panel = new CustomTablesPanel(me, gameStyle);
        f.add(panel, "Center");

        final TextField pName = new TextField(10);
        final TextField pTable = new TextField();
        final TextField pSeat = new TextField();
        Button addP = new Button("Add Player");
        addP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                DSGPlayerData d = new SimpleDSGPlayerData();
                d.setName(pName.getText());
                d.setPlayerType(DSGPlayerData.HUMAN);
                panel.addPlayer(Integer.parseInt(pTable.getText()), d);
            }
        });
        Button removeP = new Button("Remove Player");
        removeP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.removePlayer(Integer.parseInt(pTable.getText()), pName.getText());
            }
        });
        Button sitP = new Button("Sit Player");
        sitP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.sitPlayer(Integer.parseInt(pTable.getText()), pName.getText(),
                        Integer.parseInt(pSeat.getText()));
            }
        });
        Button standP = new Button("Stand Player");
        standP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.standPlayer(Integer.parseInt(pTable.getText()), pName.getText());
            }
        });

        Panel addPlayerPanel = new Panel();
        addPlayerPanel.add(new Label("Name"));
        addPlayerPanel.add(pName);
        addPlayerPanel.add(new Label("Table"));
        addPlayerPanel.add(pTable);
        addPlayerPanel.add(new Label("Seat"));
        addPlayerPanel.add(pSeat);
        addPlayerPanel.add(addP);
        addPlayerPanel.add(removeP);
        addPlayerPanel.add(sitP);
        addPlayerPanel.add(standP);


        Button addT = new Button("Add Table");
        addT.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.addTable(Integer.parseInt(pTable.getText()));
            }
        });
        Button removeT = new Button("Remove Table");
        removeT.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.removeTable(Integer.parseInt(pTable.getText()));
            }
        });
        Panel tablePanel = new Panel();
        tablePanel.add(addT);
        tablePanel.add(removeT);


        final Checkbox rated = new Checkbox("Rated");
        rated.setState(true);

        final Checkbox timed = new Checkbox("Timed");
        timed.setState(true);

        final Checkbox tableType = new Checkbox("Public");
        tableType.setState(true);

        final TextField initialT = new TextField();
        final TextField incrementalT = new TextField();
        final Choice gameChoice = new Choice();
        for (int i = 1; i <= GridStateFactory.getNumGames() + 1; i++) {
            gameChoice.add(GridStateFactory.getGameName(i));
        }
        Button changeState = new Button("Change State");
        changeState.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                DSGChangeStateTableEvent stateEvent = new DSGChangeStateTableEvent();
                stateEvent.setGame(gameChoice.getSelectedIndex());
                stateEvent.setIncrementalSeconds(Integer.parseInt(
                        incrementalT.getText()));
                stateEvent.setInitialMinutes(Integer.parseInt(
                        initialT.getText()));
                stateEvent.setRated(rated.getState());
                stateEvent.setTimed(timed.getState());
                stateEvent.setTableType(tableType.getState() ? DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC :
                        DSGChangeStateTableEvent.TABLE_TYPE_PRIVATE);

                panel.changeTableState(Integer.parseInt(pTable.getText()),
                        stateEvent);
            }
        });

        Panel statePanel = new Panel();
        statePanel.add(rated);
        statePanel.add(timed);
        statePanel.add(tableType);
        statePanel.add(new Label("Initial"));
        statePanel.add(initialT);
        statePanel.add(new Label("Incremental"));
        statePanel.add(incrementalT);
        statePanel.add(gameChoice);
        statePanel.add(changeState);


        Panel controls = new Panel();
        controls.setLayout(new GridLayout(3, 1));
        controls.add(tablePanel);
        controls.add(addPlayerPanel);
        controls.add(statePanel);

        f.add(controls, "South");


        panel.addTableJoinListener(new TableJoinListener() {
            public void joinTable(int tableNum) {
                System.out.println("join table " + tableNum);
            }
        });

        f.pack();
        f.setLocation(100, 100);
        f.setVisible(true);

        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
            }
        });
    }
}
