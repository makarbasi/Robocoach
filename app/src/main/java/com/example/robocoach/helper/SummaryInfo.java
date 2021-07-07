package com.example.robocoach.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SummaryInfo {
    public static final int DANDASANA = 0;
    public static final int DOWNDOG = 1;
    public static final int TREE_POSE = 2;
    public static final int WARRIOR_2 = 3;
    public static final int POSTURE = 4;
    public static final int REPETITION = 5;

    private static long YOGA_SUMMARY_LIVE_COUNTER[] = null;
    private static long YOGA_SUMMARY_VIDEO_COUNTER[] = null;

    private static List<List<Long>> LOG_TIMES_LIVE = null;
    private static List<List<Long>> LOG_TIMES_VIDEO = null;

    public static List<List<Long>> getLogTimesLive() {
        if (LOG_TIMES_LIVE == null) {
            LOG_TIMES_LIVE = new ArrayList<List<Long>>();

            LOG_TIMES_LIVE.add(new ArrayList<>());
            LOG_TIMES_LIVE.add(new ArrayList<>());
            LOG_TIMES_LIVE.add(new ArrayList<>());
            LOG_TIMES_LIVE.add(new ArrayList<>());
        }

        return LOG_TIMES_LIVE;
    }

    public static List<List<Long>> getLogTimesVideo() {
        if (LOG_TIMES_VIDEO == null) {
            LOG_TIMES_VIDEO = new ArrayList<List<Long>>();

            LOG_TIMES_VIDEO.add(new ArrayList<>());
            LOG_TIMES_VIDEO.add(new ArrayList<>());
            LOG_TIMES_VIDEO.add(new ArrayList<>());
            LOG_TIMES_VIDEO.add(new ArrayList<>());
        }

        return LOG_TIMES_VIDEO;
    }

    public static long[] getYogaSummaryCounterLive() {
        if (YOGA_SUMMARY_LIVE_COUNTER == null) {
            YOGA_SUMMARY_LIVE_COUNTER = new long[REPETITION + 1];
        }

        return YOGA_SUMMARY_LIVE_COUNTER;
    }

    public static long[] getYogaSummaryCounterVideo() {
        if (YOGA_SUMMARY_VIDEO_COUNTER == null) {
            YOGA_SUMMARY_VIDEO_COUNTER = new long[REPETITION + 1];
        }

        return YOGA_SUMMARY_VIDEO_COUNTER;
    }

    public static void reset(String mode) {
        if (mode.equals("LIVE")) {
            YOGA_SUMMARY_LIVE_COUNTER = null;
            LOG_TIMES_LIVE = null;
        } else {
            YOGA_SUMMARY_VIDEO_COUNTER = null;
            LOG_TIMES_VIDEO = null;
        }
    }
}
