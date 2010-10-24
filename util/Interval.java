package c8.util;

import c8.ExecEnviron;

public enum Interval {
    // numbers are daily frequency
    // note that they can all be easily converted into
    // the granularity denomination of all less granular denominations
    NEVER(0, -1), EVERY_DAY(1, 1), EVERY_THREE_HOURS(8, 2), EVERY_HOUR(24, 3), EVERY_THIRTY_MINS(
            48, 4), EVERY_FIVE_MINS(288, 5), EVERY_MINUTE(1440, 6), EVERY_THIRTY_SECS(
            2880, 7), EVERY_TEN_SECS(8640, 8), EVERY_FIVE_SECS(17280, 9), CONTINUOUS(
            Integer.MAX_VALUE, -1);

    int m_dailyFreq;

    int m_dbPK;

    Interval(int dailyFreq, int dbPrimaryKey) {
        m_dailyFreq = dailyFreq;
        m_dbPK = dbPrimaryKey;
    }

    public long duration() {
        if (this.equals(NEVER))
            throw new IllegalArgumentException(
                    "Duration for Interval.NEVER is not defined");

        if (this.equals(CONTINUOUS))
            return 0;

        long millisecsPerDay = 86400000;

        return millisecsPerDay / m_dailyFreq;
    }

    // public static /* TODO CanConvert */ (int from, int to) {
    // return /* TODO [ (int)from ] */ >= /* TODO [ (int)to ] */;
    // }

    public long nextRoundTime() {
        return nextRoundTime(System.currentTimeMillis());
    }

    public long nextRoundTime(long now) {
        if (this.equals(Interval.NEVER))
            throw new IllegalArgumentException(
                    "Next round time cannot be calculated for Interval.Never");

        if (this.equals(Interval.CONTINUOUS))
            return now;

        long intervalMs = 86400000 / m_dailyFreq;
        long intervalTicks = now / intervalMs;

        // note: now could be a round time already, but this method will always
        // pick the next round time
        long nextRoundTime = (intervalTicks + 1) * intervalMs;

        return nextRoundTime;
    }

    public boolean isRoundTime() {
        return isRoundTime(System.currentTimeMillis());
    }

    public boolean isRoundTime(long now) {
        if (this == Interval.NEVER) {
            return false;
        }
        if (this == Interval.CONTINUOUS) {
            return true;
        }

        // always a round number
        long intervalMs = 86400000 / m_dailyFreq;

        return now % intervalMs == 0;
    }

    public int getDbPrimaryKey() {
        return m_dbPK;
    }

    public int getDailyFrequency() {
        return m_dailyFreq;
    }

    public String getDurationString() {
        switch (this) {
        case NEVER:
            return "Infinite";
        case EVERY_DAY:
            return "24hr";
        case EVERY_THREE_HOURS:
            return "180min";
        case EVERY_HOUR:
            return "60min";
        case EVERY_THIRTY_MINS:
            return "30min";
        case EVERY_FIVE_MINS:
            return "5min";
        case EVERY_MINUTE:
            return "1min";
        case EVERY_THIRTY_SECS:
            return "30sec";
        case EVERY_TEN_SECS:
            return "10sec";
        case EVERY_FIVE_SECS:
            return "5sec";
        case CONTINUOUS:
            return "0sec";
        }

        return "NA";
    }
}