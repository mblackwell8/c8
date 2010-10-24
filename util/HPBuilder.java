package c8.util;

import c8.util.*;

public class HPBuilder {
    HistoryPoint m_hp;

    public HPBuilder(long barStart, Interval ival) {
        
	//BUG: assertions in the HP constructor fail at this...
	m_hp = new HistoryPoint(barStart, TickPrice.Zero, TickPrice.Zero,
                TickPrice.Zero, TickPrice.Zero, ival);
        m_hp.setNumTicks(0);
        m_hp.setAverageSpread(0);
        m_hp.setMaxSpread(0);
    }

    public void tick(TickPrice tp) {
        if (tp == null)
            return;

        TickPrice open = null, high = null, low = null, close = null;
        if (m_hp.getOpen() == null || m_hp.getOpen().getMean() == 0) {
            open = tp;
            high = tp;
            low = tp;
        } else {
            open = m_hp.getOpen();
            high = (tp.getMean() > m_hp.getHigh().getMean() ? tp : m_hp.getHigh());
            low = (tp.getMean() < m_hp.getLow().getMean() ? tp : m_hp.getLow());
        }

        close = tp;

        int numTicks = m_hp.getNumTicks() + 1;
        double maxSpread = Math.max(m_hp.getMaxSpread(), tp.getSpread());

        assert (m_hp.getAverageSpread() != -1);
        double avgSpread = (m_hp.getAverageSpread() * m_hp.getNumTicks() + 
        	tp.getSpread()) / numTicks;

        m_hp = new HistoryPoint(m_hp.getTimeStamp(), open, high, low, close,
                m_hp.getGranularity());
        m_hp.setNumTicks(numTicks);
        m_hp.setMaxSpread(maxSpread);
        m_hp.setAverageSpread(avgSpread);
    }

    public HistoryPoint getCurrent() {
        return m_hp;
    }

    public String toString() {
        return m_hp.toString();
    }
}
