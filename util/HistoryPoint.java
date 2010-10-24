package c8.util;

import java.util.Currency;


public final class HistoryPoint implements TimeStampedPrice {
    long m_openTime;

    TickPrice m_open;

    TickPrice m_low;

    TickPrice m_high;

    TickPrice m_close;

    Interval m_granularity;

    Currency m_ccy;

    // these values are settable
    int m_numTicks = -1;

    double m_maxSpread = -1.0;

    double m_avgSpread = -1.0;
    
    public HistoryPoint(long openTime, Currency ccy, double openBid, double openAsk, double highBid, double highAsk,
            double lowBid, double lowAsk, double closeBid, double closeAsk, Interval gran) {
	
	this(openTime, 
		new TickPrice(openTime, openBid, openAsk, ccy),
		new TickPrice(openTime, highBid, highAsk, ccy),
		new TickPrice(openTime, lowBid, lowAsk, ccy),
		new TickPrice(openTime, closeBid, closeAsk, ccy),
	     gran);
    }

    public HistoryPoint(long openTime, TickPrice open, TickPrice high,
            TickPrice low, TickPrice close, Interval gran) {
        m_openTime = openTime;
        m_open = open;
        m_high = high;
        m_low = low;
        m_close = close;
        m_granularity = gran;
        
        //these assertions interfere with the ability to use TickPrice.Zero
        //(currently used by HPBuilder)

//        assert m_open.getTimeStamp() == m_high.getTimeStamp()
//                && m_high.getTimeStamp() == m_low.getTimeStamp()
//                && m_low.getTimeStamp() == m_close.getTimeStamp()
//                && m_close.getTimeStamp() == m_open.getTimeStamp()
//                && m_open.getTimeStamp() == openTime;

//        assert m_open.getCcy().equals(m_high.getCcy())
//                && m_high.getCcy().equals(m_low.getCcy())
//                && m_low.getCcy().equals(m_close.getCcy())
//                && m_close.getCcy().equals(m_open.getCcy());

        m_ccy = close.getCcy();
    }

    public CandlePoint getCandle() {
        return new CandlePoint(m_openTime, m_open.getMean(), m_high.getMean(),
                m_low.getMean(), m_close.getMean(), m_granularity, m_ccy);
    }

    public String toString() {
        return String.format("%1$TF %1$TT %2$6.4f %3$6.4f %4$6.4f %5$6.4f",
                m_openTime, m_open.getMean(), m_high.getMean(),
                m_low.getMean(), m_close.getMean());
    }

    public Interval getGranularity() {
        return m_granularity;
    }

    public TickPrice getOpen() {
        return m_open;
    }

    public TickPrice getLow() {
        return m_low;
    }

    public TickPrice getHigh() {
        return m_high;
    }

    public TickPrice getClose() {
        return m_close;
    }

    public int getNumTicks() {
        return m_numTicks;
    }

    public void setNumTicks(int value) {
        m_numTicks = value;
    }

    public double getMaxSpread() {
        return m_maxSpread;
    }

    public void setMaxSpread(double value) {
        m_maxSpread = value;
    }

    public double getAverageSpread() {
        return m_avgSpread;
    }

    public void setAverageSpread(double value) {
        m_avgSpread = value;
    }

    public long getTimeStamp() {
        return m_openTime;
    }

    public Currency getCcy() {
        return m_ccy;
    }

    public double getAmount() {
        return m_close.getMean();
    }
}