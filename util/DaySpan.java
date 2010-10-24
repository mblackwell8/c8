package c8.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DaySpan {
    private long m_start;

    private long m_finish;
    
    private boolean m_isOvernight;
    
    public static DaySpan TWENTYFOUR_HOURS;
    
    
    public static final long MILLIS_PER_DAY = 86400000;

    static {
	TWENTYFOUR_HOURS = new DaySpan(0, MILLIS_PER_DAY);
    }

    public DaySpan(long start, long finish) {
	start %= MILLIS_PER_DAY;
	
	//allow users to specify 24:00:00
	if (finish != MILLIS_PER_DAY)
	    finish %= MILLIS_PER_DAY;
	
	//if the start time is 23:00, finishing at 05:00am for example,
	//then we want to remember that this day span is actually a night span!
	m_isOvernight = (start > finish);
	
	m_start = start;
	m_finish = finish;
	
//	System.out.format("Ctor: %1$TT\n", m_start);
//	System.out.format("Ctor: %1$TT\n", m_finish);
    }

    public long getStart() {
        return m_start;
    }

    public long getFinish() {
        return m_finish;
    }

    // / <summary>
    // / The hour component of this instance (from 0 to 23)
    // / </summary>
    public int getHours() {
	int hours = (int) (getDuration() / (60 * 60 * 1000));
        assert hours >= 0 && hours < 24;

        return hours;
    }

    // / <summary>
    // / The minutes component of this instance (from 0 to 59)
    // / </summary>
//    public int getMinutes() {
//        //long minsInMillis = getDuration() % (60 * 60 * 1000);
//        int mins = (int) (getDuration() / (60 * 1000));
//        assert mins >= 0 && mins < 60;
//
//        return mins;
//    }

    public long getDuration() {
	if (m_isOvernight) {
	    return (MILLIS_PER_DAY - m_start) + m_finish;
	}
	
	return m_finish - m_start;
    }


    // True, if the provided time is greater than start and less than finish, or equivalent to either
    public boolean spans(long time) {
	time %= MILLIS_PER_DAY;
	
	if (m_isOvernight) {
	    return (time >= m_start || time <= m_finish);
	}
		
        return (time >= m_start && time <= m_finish);
    }
    
    public DaySpan parse(String startArrowFinish) throws ParseException {
        // format is start>finish
        String[] parts = startArrowFinish.split(">");
        if (parts.length != 2)
            throw new ParseException("DaySpan.parse must be formatted as HH(:mm(:ss))>HH(:mm(:ss))", 
        	    (parts.length > 0 ? parts[0].length() : 0));
        
        return parse(parts[0], parts[1]);
    }
    
    public static DaySpan parse(String startTime, String finishTime) throws ParseException {
        // format is HH(:mm(:ss))
        SimpleDateFormat df = new SimpleDateFormat("H:m:s");
        Date start = df.parse(startTime);
        Date finish = df.parse(finishTime);
        
//        System.out.println(start.toString());
//        System.out.println(finish.toString());

        return new DaySpan(start.getTime(), finish.getTime());
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof DaySpan))
            return false;

        return this.equals((DaySpan) obj);
    }

    public boolean equals(DaySpan other) {
        if (this.m_start == other.m_start) {
            return this.m_finish == other.m_finish;
        }

        return false;
    }

    public int hashCode() {
        return Long.valueOf(m_finish - m_start).hashCode();
    }

    public String toString() {
        return String.format("%1$TT to %2$TT", m_start, m_finish);
    }
    
    public static void main(String[] args) {
	
	System.out.println("Starting DaySpan test");
	
	DaySpan dsDay = new DaySpan(1000*60*60*5, 1000*60*60*20);
	System.out.println("Made a DaySpan from 0500hrs to 2000hrs: " + dsDay.toString());
	System.out.println("Day should have 54,000,000 duration: " + Long.toString(dsDay.getDuration()));
	System.out.println("Day should have 15 hours: " + Integer.toString(dsDay.getHours()));
	System.out.println("Day should span 1500hrs: " + dsDay.spans(1000*60*60*15));
	System.out.println("Day should not span 2001hrs: " + !dsDay.spans(1000*60*60*20+1000*60*1));
	
	try {
	    DaySpan parsed = DaySpan.parse("09:00:00", "12:00:00");
	    System.out.println("Parsed a DaySpan from 0900hrs to 1200hrs: " + parsed.toString());
	}
	catch (ParseException e) {
	    System.err.println("Parse exception");
	}
	
	DaySpan nightDay = new DaySpan(1000*60*60*16, 1000*60*60*3);
	System.out.println("Made an overnight DaySpan from 1600hrs to 300hrs: " + nightDay.toString());
	System.out.println("Day should have 39,600,000 duration: " + Long.toString(nightDay.getDuration()));
	System.out.println("Day should have 11 hours: " + Integer.toString(nightDay.getHours()));
	System.out.println("Day should span 1800hrs: " + nightDay.spans(1000*60*60*18));
	System.out.println("Day should not span 1500hrs: " + !nightDay.spans(1000*60*60*15));
	
	long now = System.currentTimeMillis();
	System.out.format("DaySpan '%1$s' spans current time (%2$TT %2$TZ, %2$d)? %3$s\n", nightDay, now, nightDay.spans(now));
	System.out.format("DaySpan '%1$s' spans current time (%2$TT %2$TZ, %2$d)? %3$s\n", dsDay, now, dsDay.spans(now));
	
	now %= MILLIS_PER_DAY;
	System.out.format("DaySpan '%1$s' spans current time (%2$TT %2$TZ, %2$d)? %3$s\n", nightDay, now, nightDay.spans(now));
	System.out.format("DaySpan '%1$s' spans current time (%2$TT %2$TZ, %2$d)? %3$s\n", dsDay, now, dsDay.spans(now));
	
	System.out.println("Finishing DaySpan test");
    }
}
