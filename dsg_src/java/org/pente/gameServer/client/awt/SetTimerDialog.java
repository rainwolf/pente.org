package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.*;

public class SetTimerDialog extends Dialog {

    private Checkbox timerCheck;
    private TextField timerInitial;
    private TextField timerIncremental;

    private final static String SET_TIMER = "Set Timer";
    private final static String CANCEL = "Cancel";

    private Frame parent;

    public SetTimerDialog(Frame parent, final SetTimerListener setTimerListener,
                          GameStyles gameStyle, boolean timed, int initialTime, int incrementalTime) {

        super(parent, "Set Timer", false);

        this.parent = parent;

        setLayout(new BorderLayout());
        setBackground(gameStyle.boardBack);
        InsetPanel panel = new InsetPanel(3, 3, 3, 3);
        panel.setLayout(new BorderLayout());
        panel.setBackground(gameStyle.boardBack);

        timerCheck = new Checkbox("Timer on");
        timerCheck.setBackground(gameStyle.boardBack);
        timerCheck.setForeground(gameStyle.foreGround);
        timerCheck.setState(timed);
        timerCheck.addItemListener(e -> enableTimeFields(timerCheck.getState()));

        Label timerInitialLabel = new Label("Initial Time (min)");
        timerInitialLabel.setBackground(gameStyle.boardBack);
        timerInitialLabel.setForeground(gameStyle.foreGround);
        timerInitial = new TextField(Integer.toString(initialTime));
        timerInitial.setBackground(Color.white);


        Label timerIncrementalLabel = new Label("Incremental Time (sec)");
        timerIncrementalLabel.setBackground(gameStyle.boardBack);
        timerIncrementalLabel.setForeground(gameStyle.foreGround);

        timerIncremental = new TextField(Integer.toString(incrementalTime));
        timerIncremental.setBackground(Color.white);
        enableTimeFields(timed);

        Button setTimeButton = gameStyle.createDSGButton(SET_TIMER);
        setTimeButton.addActionListener(e -> {
            if (isValidTime(timerInitial.getText(), 1, 999) &&
                    isValidTime(timerIncremental.getText(), 0, 59)) {
                setTimerListener.setTimer(timerCheck.getState(),
                        Integer.parseInt(timerInitial.getText()),
                        Integer.parseInt(timerIncremental.getText()));
                dispose();
            }
        });

        Button cancelButton = gameStyle.createDSGButton(CANCEL);
        cancelButton.addActionListener(e -> dispose());

        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        panel.setLayout(gridbag);

        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.anchor = GridBagConstraints.CENTER;
        buildConstraints(constraints, 0, 0, 2, 1, 0, 0);
        gridbag.setConstraints(timerCheck, constraints);
        panel.add(timerCheck);

        constraints.anchor = GridBagConstraints.NORTHWEST;
        buildConstraints(constraints, 0, 1, 1, 1, 8, 0);
        gridbag.setConstraints(timerInitialLabel, constraints);
        panel.add(timerInitialLabel);

        constraints.fill = GridBagConstraints.HORIZONTAL;
        buildConstraints(constraints, 1, 1, 1, 1, 2, 0);
        gridbag.setConstraints(timerInitial, constraints);
        panel.add(timerInitial);

        constraints.fill = GridBagConstraints.NONE;
        buildConstraints(constraints, 0, 2, 1, 1, 8, 0);
        gridbag.setConstraints(timerIncrementalLabel, constraints);
        panel.add(timerIncrementalLabel);

        constraints.fill = GridBagConstraints.HORIZONTAL;
        buildConstraints(constraints, 1, 2, 1, 1, 2, 0);
        gridbag.setConstraints(timerIncremental, constraints);
        panel.add(timerIncremental);


        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 1, 1));
        buttonPanel.add(setTimeButton);
        buttonPanel.add(cancelButton);

        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.CENTER;
        buildConstraints(constraints, 0, 3, 2, 1, 0, 0);
        gridbag.setConstraints(buttonPanel, constraints);
        panel.add(buttonPanel);


        add("Center", panel);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        setResizable(false);
        pack();
        centerDialog();
        setVisible(true);
    }

    public void buildConstraints(GridBagConstraints gbc, int gx, int gy, int gw, int gh, int wx, int wy) {

        gbc.gridx = gx;
        gbc.gridy = gy;
        gbc.gridwidth = gw;
        gbc.gridheight = gh;
        gbc.weightx = wx;
        gbc.weighty = wy;
    }


    public void centerDialog() {

        Point location = new Point();
        location.x = parent.getLocation().x +
                parent.getSize().width / 2 -
                getSize().width / 2;
        location.y = parent.getLocation().y +
                parent.getSize().height / 2 -
                getSize().height / 2;
        setLocation(location);
    }


    public void enableTimeFields(boolean b) {

        timerInitial.setEnabled(b);
        timerIncremental.setEnabled(b);
    }


    public boolean isValidTime(String timeStr, int min, int max) {
        if (timeStr == null || timeStr.isEmpty()) {
            return false;
        }

        for (int i = 0; i < timeStr.length(); i++) {

            if (!Character.isDigit(timeStr.charAt(i))) {
                return false;
            }
        }
        int timeInt = Integer.parseInt(timeStr);
        if (timeInt < min || timeInt > max) {
            return false;
        }

        return true;

    }
}
