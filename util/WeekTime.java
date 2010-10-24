package c8.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


public class WeekTime implements Comparable<WeekTime> {
    public static final int FIRST_DAY = Calendar.SUNDAY; // = 1

    public static final int LAST_DAY = Calendar.SATURDAY; // = 7

    public static final int HOURS_PER_DAY = 24;

    public static final int DAYS_PER_WEEK = 7;

    private int m_day;

    private long m_time; // since midnight on the day
    
    private String m_desc;

    public static final WeekTime WEEK_START;

    public static final WeekTime WEEK_FINISH;

    public static final long MILLIS_PER_DAY = 86400000;

    // public WeekTime()
    // : this(FIRSTDAY, TimeSpan.MinValue)
    // {
    // }

    public WeekTime(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        
        m_day = c.get(Calendar.DAY_OF_WEEK);
        m_time = c.get(Calendar.HOUR_OF_DAY) * 3600000 + c.get(Calendar.MINUTE)
                * 60000 + c.get(Calendar.SECOND) * 1000
                + c.get(Calendar.MILLISECOND);
    }

    public WeekTime(int day, long time) {
        m_day = day;
        
        //setting time to midnight will cause this to become midnight
        //on the day prior
        m_time = time % MILLIS_PER_DAY;
    }

    public WeekTime(int day, int hourOfDay) {
        m_day = day;
        m_time = hourOfDay * 60 * 60 * 1000;
    }

    public WeekTime(Date d) {
        this(d.getTime());
    }

    static {
        WEEK_START = new WeekTime(FIRST_DAY, 0);
        WEEK_FINISH = new WeekTime(LAST_DAY, MILLIS_PER_DAY - 1);
    }

    // / <summary>
    // / Gets the number of whole days
    // / </summary>
    public int getDay() {
        return m_day;
    }

    public long getTime() {
        return m_time;
    }

    // / <summary>
    // / Gets the number of ticks (always positive)
    // / </summary>
    public long getMillis() {
        return (m_day - 1) * MILLIS_PER_DAY + m_time;
    }

    public static long duration(WeekTime start, WeekTime finish) {
        return finish.getMillis() - start.getMillis();
    }

    // / <summary>
    // / Utility method to calculate the date and time of the first tick
    // / in the week that includes the specified date and time
    // / </summary>
    public static long beginningOfWeek(long time) {
        Calendar bow = Calendar.getInstance();
        bow.setTimeInMillis(time);
        bow.set(Calendar.DAY_OF_WEEK, FIRST_DAY);
        bow.set(Calendar.HOUR_OF_DAY, 0);
        bow.set(Calendar.MINUTE, 0);
        bow.set(Calendar.SECOND, 0);
        bow.set(Calendar.MILLISECOND, 0);

        return bow.getTimeInMillis();
    }

    // /// <summary>
    // /// Utility method to calculate the date and time of the last tick
    // /// in the week that includes the specified date and time
    // /// </summary>
    // public static DateTime EndOfWeek(DateTime dt)
    // {
    // return BeginningOfWeek(dt).AddTicks(TicksPerWeek - 1);
    // }

    public static WeekTime now() {
        return new WeekTime(System.currentTimeMillis());
    }

    public static WeekTime fromTime(long time) {
        return new WeekTime(time);
    }

    public static WeekTime parse(String source) throws ParseException {
        // format is dddd,HH(:mm(:ss))
        SimpleDateFormat df = new SimpleDateFormat("E,H:m:s");
        Date d = df.parse(source);

        return new WeekTime(d);
    }

    public boolean before(WeekTime other) {
        return (this.getMillis() < other.getMillis());
    }

    public boolean after(WeekTime other) {
        return (this.getMillis() > other.getMillis());
    }

    public int hashCode() {
        return Long.valueOf(this.getMillis()).hashCode();
    }

    public String toString() {
        if (m_desc == null) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.DAY_OF_WEEK, m_day);
            int hour = (int)(m_time % (60 * 60 * 1000));
            c.set(Calendar.HOUR_OF_DAY, hour);
            int minute = (int)((m_time - hour * 60) % (60 * 1000));
            c.set(Calendar.MINUTE, minute);
            int second = (int)((m_time - hour * 60 - minute * 60) % 1000);
            c.set(Calendar.SECOND, second);
            int milli = (int)(m_time - hour * 60 - minute * 60 - second * 1000);
            c.set(Calendar.MILLISECOND, milli);
            
            Date d = c.getTime();
            m_desc  = String.format("%1$tA, %1$tT", d);
        }
        
        return m_desc;
    }

    public boolean equals(Object other) {
        if (!(other instanceof WeekTime))
            return false;

        return this.equals((WeekTime) other);
    }

    public boolean equals(WeekTime other) {
        return (this.getMillis() == other.getMillis());
    }

    public int compareTo(WeekTime other) {
        if (this.m_day < other.m_day)
            return -1;
        if (this.m_day > other.m_day)
            return 1;

        // must be equal days, so go on time
        assert m_day == other.m_day;
        if (this.m_time < other.m_time)
            return -1;
        if (this.m_time > other.m_time)
            return 1;

        assert m_time == other.m_time;

        return 0;
    }

}
