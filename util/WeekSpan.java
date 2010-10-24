package c8.util;

public class WeekSpan {
    WeekTime m_start;

    WeekTime m_finish;

    long m_duration;
    
    public static WeekSpan TWENTYFOUR_SEVEN;
    
    static {
	TWENTYFOUR_SEVEN = new WeekSpan(WeekTime.WEEK_START, WeekTime.WEEK_FINISH);
    }

    // public static readonly WeekSpan MaxValue;
    // public static readonly WeekSpan MinValue;

    // static WeekSpan()
    // {
    // MaxValue = new WeekSpan(WeekTime.WeekStart, WeekTime.WeekFinish);
    // MinValue = new WeekSpan(
    // }

    public WeekSpan(WeekTime start, WeekTime finish) {
        m_start = start;
        m_finish = finish;
        m_duration = WeekTime.duration(m_start, m_finish);
    }

    public WeekSpan(int startDay, long startTime, int finishDay, long finishTime) {
        m_start = new WeekTime(startDay, startTime);
        m_finish = new WeekTime(finishDay, finishTime);
        m_duration = WeekTime.duration(m_start, m_finish);
    }

    public WeekSpan(long start, long finish) {
        // note: the weektime logic simply treats start and end as
        // a day and time combination, so end must always be after start
        // (and will be, through the logic of this class
        start = Math.min(start, finish);
        finish = Math.max(start, finish);
        m_start = new WeekTime(start);
        m_finish = new WeekTime(finish);
        m_duration = WeekTime.duration(m_start, m_finish);
    }

    public WeekTime getStart() {
        return m_start;
    }

    // / <summary>
    // / Returns the time at the start of the week which includes the specified
    // time
    // / </summary>
    // / <param name="dt"></param>
    // / <returns></returns>
    public long absoluteStart(long time) {
        long weekBeginning = WeekTime.beginningOfWeek(time);

        return weekBeginning + m_start.getMillis();
    }

    public WeekTime getFinish() {
        return m_finish;
    }

    public long absoluteFinish(long time) {
        long weekBeginning = WeekTime.beginningOfWeek(time);

        return weekBeginning + m_finish.getMillis();
    }

    // / <summary>
    // / The day component of this instance (from 0 to 6)
    // / </summary>
    public int getDays() {
        int days = (int) (m_duration / WeekTime.MILLIS_PER_DAY);
        assert days >= 0 && days < 7;

        return days;
    }

    // / <summary>
    // / The hour component of this instance (from 0 to 23)
    // / </summary>
    public int getHours() {
        long hoursInMillis = m_duration % WeekTime.MILLIS_PER_DAY;
        int hours = (int) (hoursInMillis / (60 * 60 * 1000));
        assert hours >= 0 && hours < 24;

        return hours;
    }

    // / <summary>
    // / The minutes component of this instance (from 0 to 59)
    // / </summary>
    public int getMinutes() {
        long minsInMillis = m_duration % (60 * 60 * 1000);
        int mins = (int) (minsInMillis / (60 * 1000));
        assert mins >= 0 && mins < 60;

        return mins;
    }

    public long getDuration() {
        return m_duration;
    }

    // / <summary>
    // / Tests if this WeekSpan includes the provided WeekTime
    // / </summary>
    // / <remarks>
    // / True if this WeekSpan begins or ends on precisely the provided WeekTime
    // / </remarks>
    // / <param name="wt">The WeekTime</param>
    // / <returns>True, if this WeekSpan includes the provided
    // WeekTime</returns>
    public boolean spans(WeekTime wt) {
        if ((wt.after(m_start) || wt.equals(m_start))) {
            return (wt.before(m_finish) || wt.equals(m_finish));
        }
        

        return false;
    }

    // / <summary>
    // / Tests if this WeekSpan includes the provided DateTime
    // / </summary>
    // / <remarks>
    // / True if this WeekSpan begins or ends on precisely the provided
    // WeekTime.FromTime
    // / </remarks>
    // / <param name="wt">The DateTime</param>
    // / <returns>True, if this WeekSpan includes the provided
    // DateTime</returns>
    public boolean spans(long time) {
        return spans(new WeekTime(time));
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof WeekSpan))
            return false;

        return this.equals((WeekSpan) obj);
    }

    public boolean equals(WeekSpan other) {
        if (this.m_start.equals(other.m_start)) {
            return this.m_finish.equals(other.m_finish);
        }

        return false;
    }

    public int hashCode() {
        return Long.valueOf(m_duration).hashCode();
    }

    public String toString() {
        return String.format("%1$s to %2$s", m_start.toString(),
                m_finish.toString());
    }

}
