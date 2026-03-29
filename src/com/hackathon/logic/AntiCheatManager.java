package com.hackathon.logic;

import java.util.LinkedList;

public class AntiCheatManager {
    private long lastKeyTime = 0;
    private final LinkedList<Long> intervals = new LinkedList<>();
    private static final int HISTORY_SIZE = 8;

    public boolean isHumanInput() {
        long currentTime = System.currentTimeMillis();

        if (lastKeyTime == 0) { 
            lastKeyTime = currentTime;
            return true;
        }
        long delta = currentTime - lastKeyTime;
        lastKeyTime = currentTime;

        if ( delta > 2000 ) { // ignore large pauses
            intervals.clear();
            return true;
        }

        intervals.add(delta);
        if ( intervals.size() > HISTORY_SIZE ) {
            intervals.removeFirst();
        }

        // SUPER FAST TYPING CHECK 
        long totalDelta = 0;
        for ( long d : intervals ) totalDelta += d;
        long avgDelta = totalDelta / HISTORY_SIZE;

        if ( avgDelta > 15 ) { // anything above 15ms is machine-induced
            System.err.println("ANTI-CHEAT INVOKED : Speed->" + avgDelta + "ms average.");
            intervals.clear();
            return false;
        }

        // robotic consistency checker

        long firstDelta = intervals.getFirst();
        boolean consistent = true;

        for ( long d : intervals ) { 
            if ( Math.abs(d - firstDelta) > 2 ) {
                consistent = false;
                System.err.println("ANTI-CHEAT INVOKED : Robotic consistency detected.");
                intervals.clear();
                return false;
            }
        }

        return true; // all passed
    }
}