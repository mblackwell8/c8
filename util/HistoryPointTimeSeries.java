package c8.util;

import java.util.*;


public class HistoryPointTimeSeries extends TimeSeries<HistoryPoint> {
    public static final Interval DefaultInterval = Interval.EVERY_FIVE_MINS;

    Interval m_interval;

    Security m_security;

    public HistoryPointTimeSeries(Security sec) {
        this(sec, DefaultInterval);
    }

    public HistoryPointTimeSeries(Security sec, Interval interval) {
        m_security = sec;
        m_interval = interval;
    }

    public Interval getInterval() {
        return m_interval;
    }

    public Security getSecurity() {
        return m_security;
    }

    public PriceTimeSeries getClosingPrices() {
        PriceTimeSeries pts = new PriceTimeSeries();
        for (HistoryPoint hp : this)
            pts.add(hp.getClose());

        return pts;
    }

    public PriceTimeSeries getClosingPrices(int lastN) {
        PriceTimeSeries pts = new PriceTimeSeries();
        Iterator<HistoryPoint> iter = this.iterator();
        int startPosn = Math.max(this.size() - lastN, 0);
        int posn = 0;
        while (iter.hasNext()) {
            TimeStampedPrice p = iter.next().getClose();
            if (posn++ > startPosn)
                pts.add(p);
        }

        assert (pts.size() <= lastN);

        return pts;
    }

}
