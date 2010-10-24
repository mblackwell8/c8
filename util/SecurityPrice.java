package c8.util;

import java.util.Currency;

public final class SecurityPrice extends Amount implements
        TimeStampedPrice {
    Security m_sec;

    TickPrice m_tick;

    public SecurityPrice(Security sec, TickPrice tick) {
        super(tick.getAmount(), tick.getCcy());
        m_sec = sec;
        m_tick = tick;
    }

    public SecurityPrice(Security sec, long dt, double bid, double ask,
            Currency ccy) {
        super((bid + ask) / 2.0, ccy);
        m_sec = sec;
        m_tick = new TickPrice(dt, bid, ask, ccy);
    }

    public SecurityPrice(String ticker, long dt, double bid, double ask,
            Currency ccy) {
        super((bid + ask) / 2.0, ccy);
        m_sec = new Security(ticker);
        m_tick = new TickPrice(dt, bid, ask, ccy);
    }

    public Security getSecurity() {
        return m_sec;
    }

    public TickPrice getTickPrice() {
        return m_tick;
    }

    public String toString() {
        return m_sec.getTicker() + " " + m_tick.toString();
    }

    public long getTimeStamp() {
        return m_tick.getTimeStamp();
    }
}
