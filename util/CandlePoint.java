package c8.util;

import java.util.Currency;

public final class CandlePoint implements TimeStampedPrice {
    long m_openTime;

    double m_open;

    double m_low;

    double m_high;

    double m_close;

    Interval m_granularity;

    Currency m_ccy;

    public static CandlePoint Zero;

    public CandlePoint(long openTime, double open, double high, double low,
            double close, Interval gran, Currency ccy) {
        m_openTime = openTime;
        m_open = open;
        m_high = high;
        m_low = low;
        m_close = close;
        m_granularity = gran;
        m_ccy = ccy;
    }

    static {
        Zero = new CandlePoint(0, 0, 0, 0, 0, Interval.NEVER, null);
    }

    public String toString() {
        return String.format("%1$F %1$T %2$6.4f %3$6.4f %4$6.4f %5$6.4f",
                m_openTime, m_open, m_high, m_low, m_close);
    }

    public Interval getGranularity() {
        return m_granularity;
    }

    public double getOpen() {
        return m_open;
    }

    public double getLow() {
        return m_low;
    }

    public double getHigh() {
        return m_high;
    }

    public long getTimeStamp() {
        return m_openTime;
    }

    public double getAmount() {
        return m_close;
    }

    public Currency getCcy() {
        return m_ccy;
    }
}