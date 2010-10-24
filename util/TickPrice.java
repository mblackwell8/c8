package c8.util;

import java.util.Currency;

import c8.util.*;

public final class TickPrice extends Amount implements
        TimeStampedPrice, Cloneable {
    private long m_timestamp;

    private double m_ask;

    private double m_bid;

    public static TickPrice Zero;

    static {
        Zero = new TickPrice(0, 0, 0, null);
    }

    public TickPrice(long time, double bid, double ask, Currency curr) {
        super((bid + ask) / 2.0, curr);
        m_timestamp = time;
        m_bid = bid;
        m_ask = ask;
    }

    public Object clone() {
        return new TickPrice(m_timestamp, m_bid, m_ask, this.getCcy());
    }

    public String toString() {
        return String.format("%1$TF %1$TT %2$6.4f %3$6.4f", this.getTimeStamp(),
                m_bid, m_ask);
    }

    public double getAsk() {
        return m_ask;
    }

    public Price getAskPrice() {
        return new Price(m_ask, this.getCcy());
    }

    public double getBid() {
        return m_bid;
    }

    public Price getBidPrice() {
        return new Price(m_bid, this.getCcy());
    }

    public double getSpread() {
        return m_ask - m_bid;
    }
    
    public double getSpreadPips() {
//	typical situation, where the currency is a small
        //denomination... like AUD/USD
	double multiplier = 10000.0;
        if (m_ask > 10.0) {
            //yen exchanges rates mostly i guess
            multiplier = 100.0;
        }
	
	return (m_ask - m_bid) * multiplier;
    }

    public double getMean() {
        return (m_bid + m_ask) / 2.0;
    }

    public long getTimeStamp() {
        return m_timestamp;
    }
    
    public boolean equals(TickPrice tp) {
	//implements amount style equals...
	//this means that TickPrices from diff times
	//are considered equal
	//this fulfils the "Amount" style contract
	return (m_bid == tp.m_bid &&
		m_ask == tp.m_ask &&
		getCcy().equals(tp.getCcy()));
    }
}