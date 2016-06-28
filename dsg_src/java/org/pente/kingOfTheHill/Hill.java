package org.pente.kingOfTheHill;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Created by waliedothman on 25/06/16.
 */
public class Hill {
    private List<Step> steps;
    private int hillID;

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public int myStep(long pid) {
        int idx = 0;
        for (Step step : steps) {
            if (step.hasPlayer(pid)) {
                return idx;
            }
            idx += 1;
        }
        return -1;
    }

    public void addPlayer(long playerID) {
        if (steps == null) {
            steps = new ArrayList<Step>();
            Step step = new Step();
            step.addPlayer(playerID);
            steps.add(step);
        } else {
            boolean alreadyAdded = false;
            for (Step step : steps) {
                for (long pid : step.getPlayers()) {
                    if (pid == playerID) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (alreadyAdded) {
                    break;
                }
            }
            if (!alreadyAdded) {
                steps.get(0).addPlayer(playerID);
            }
        }
    }
    public boolean removePlayer(long playerID) {
        if (steps != null) {
            for (Iterator<Step> iterator = steps.iterator(); iterator.hasNext();) {
                Step step = iterator.next();
                if (step.getPlayers().remove(playerID)) {
                    if (step.getPlayers().size() == 0) {
                        iterator.remove();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasPlayer(long pid) {
        for (Step step : steps) {
            for (long playerID : step.getPlayers()) {
                if (playerID == pid) {
                    return true;
                }
            }
        }
        return false;
    }

    public void movePlayersUpDown(long winner, long loser) {
        Step currentStep = null, previousStep = null, nextStep = null;
        if (steps != null) {
            for (Step step : steps) {
                previousStep = currentStep;
                currentStep = step;
                if (previousStep != null) {
                    if (currentStep.removePlayer(loser)) {
                        previousStep.addPlayer(loser);
                        break;
                    }
                }
            }
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).removePlayer(winner)) {
                    if (i + 1 < steps.size()) {
                        steps.get(i + 1).addPlayer(winner);
                    } else {
                        Step step = new Step();
                        step.addPlayer(winner);
                        steps.add(step);
                    }
                    break;
                }
            }
            for (Iterator<Step> iterator = steps.iterator(); iterator.hasNext();) {
                Step step = iterator.next();
                if (step != null && step.getPlayers().size() == 0) {
                    iterator.remove();
                }
            }
        }
    }

    public int stepsBetween(long pid1, long pid2) {
        int step1 = 0, step2 = 0, idx = 0;
        boolean found1 = false, found2 = false;
        for (Step step : steps) {
            for (long pid : step.getPlayers()) {
                if (pid == pid1) {
                    step1 = idx;
                    found1 = true;
                }
                if (pid == pid2) {
                    step2 = idx;
                    found2 = true;
                }
            }
            if (found1 && found2) {
                break;
            }
            idx += 1;
        }
        return step1 - step2;
//        if (step1 > step2) {
//            return step1 - step2;
//        } else {
//            return step2 - step1;
//        }
    }

    public int getHillID() {
        return hillID;
    }

    public void setHillID(int hillID) {
        this.hillID = hillID;
    }

    public List<Long> getMembers() {
        List<Long> members = new ArrayList<>();
        for (Step step : steps) {
            members.addAll(step.getPlayers());
        }
        return members;
    }

}
