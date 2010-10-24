package c8.trading;

import c8.util.*;

import java.util.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class MACDSignal implements SignalProvider {
    private int m_numFastPeriods = 12;

    private int m_numSlowPeriods = 26;

    //private boolean m_isExponential = true;

    private boolean m_smoothSignalLine = false;

    private int m_numSignalLinePeriods = 9;

    private boolean m_isInitialised = false;

    private double m_lastNonZeroSignal = 0.0;

    private HistoryPointTimeSeries m_prices;
    
    private PriceTimeSeries m_slowMovAvgs;

    private PriceTimeSeries m_fastMovAvgs;

    private PriceTimeSeries m_signalLine;

    private PriceTimeSeries m_signalMovAvgs;

    private static final Logger LOG = LogManager.getLogger(MACDSignal.class);
    
    //defines the number of data points provided to the exponential smoothing process
    //(if it is used)
    public static final int n_EXP_SMOOTHING_DATAPTS = 100;

    public MACDSignal() {
    }

    public MACDSignal(int fastPeriods, int slowPeriods) {
        this(fastPeriods, slowPeriods, 0);
    }

//    public MACDSignal(int fastPeriods, int slowPeriods, boolean isExponential) {
//        m_numFastPeriods = fastPeriods;
//        m_numSlowPeriods = slowPeriods;
//        m_isExponential = isExponential;
//    }
//
//    public MACDSignal(int fastPeriods, int slowPeriods, int signalLinePeriods) {
//        this(fastPeriods, slowPeriods, signalLinePeriods, false);
//    }

    public MACDSignal(int fastPeriods, int slowPeriods, int signalLinePeriods/*, boolean isExponential*/) {
        m_numFastPeriods = fastPeriods;
        m_numSlowPeriods = slowPeriods;
        m_numSignalLinePeriods = signalLinePeriods;
        m_smoothSignalLine = (signalLinePeriods > 0);
//        m_isExponential = isExponential;
    }

    public int getNumFastPeriods() {
        return m_numFastPeriods;
    }

    public void setNumFastPeriods(int value) {
        if (value <= 0)
            throw new IllegalArgumentException("Zero/negatives are invalid MA periods");

        // we could change it to be shorter, but let's not
        if (m_isInitialised)
            throw new RuntimeException("Cannot change MACDSignal settings post initialisation");

        m_numFastPeriods = value;
    }

    public int getNumSlowPeriods() {
        return m_numSlowPeriods;
    }

    public void setNumSlowPeriods(int value) {
        if (value <= 0)
            throw new IllegalArgumentException("Zero/negatives are invalid MA periods");

        // we could change it to be shorter, but let's not
        if (m_isInitialised)
            throw new RuntimeException("Cannot change MACDSignal settings post initialisation");

        m_numSlowPeriods = value;
    }

//    public boolean isExponential() {
//        return m_isExponential;
//    }

//    public void setIsExponential(boolean value) {
//        // we could change this one, but let's not
//        if (m_isInitialised)
//            throw new RuntimeException("Cannot change MACDSignal settings post initialisation");
//
//        m_isExponential = value;
//    }

    // public double SmoothingFactor
    // {
    // get { return m_smoothingFactor; }
    // set { m_smoothingFactor = value; }
    // }

    public int getNumSignalLinePeriods() {
        return m_numSignalLinePeriods;
    }

    public void setNumSignalLinePeriods(int value) {
        if (value <= 0)
            throw new IllegalArgumentException("Zero/negatives are invalid MA periods");

        // we could change it to be shorter, but let's not
        if (m_isInitialised)
            throw new RuntimeException("Cannot change MACDSignal settings post initialisation");

        m_numSignalLinePeriods = value;
    }

    public boolean isSmoothSignalLine() {
        return m_smoothSignalLine;
    }

    public void setIsSmoothSignalLine(boolean value) {
        // we could change this one, but let's not
        if (m_isInitialised)
            throw new RuntimeException("Cannot change MACDSignal settings post initialisation");

        m_smoothSignalLine = value;
    }

    public SignalProvider clone() {
        MACDSignal cln = new MACDSignal();
        cln.m_numFastPeriods = m_numFastPeriods;
        cln.m_numSlowPeriods = m_numSlowPeriods;
//        cln.m_isExponential = m_isExponential;
        cln.m_numSignalLinePeriods = m_numSignalLinePeriods;
        cln.m_smoothSignalLine = m_smoothSignalLine;

        return cln;
    }

    public void initialize(PriceTable tbl, Security sec, Interval ival) {
        int reqdPeriods = m_numSlowPeriods + (m_smoothSignalLine ? m_numSignalLinePeriods : 0);
        m_prices = tbl.getPriceHistory(sec, ival, reqdPeriods);
        doInitialise();
    }

    private void doInitialise() {
        LOG.info("Initialising MACDSignal");
	
	if (m_prices == null)
            return;

        int reqdPeriods = m_numSlowPeriods + (m_smoothSignalLine ? m_numSignalLinePeriods : 0);

        // TODO: deal with short data situations
        assert (reqdPeriods <= m_prices.size());

        // calc the MA's across the entire dataset
        PriceTimeSeries closingPrices = m_prices.getClosingPrices();

        m_slowMovAvgs = closingPrices.movingAverage(m_numSlowPeriods);
        m_fastMovAvgs = closingPrices.movingAverage(m_numFastPeriods);

        // the slow moving average will start later because it used up more
        // of the price time series generating its first value (ie. it's slow!)
        assert (m_slowMovAvgs.size() < m_fastMovAvgs.size());
        assert (m_slowMovAvgs.firstDate() > m_fastMovAvgs.firstDate());
        assert (m_slowMovAvgs.lastDate() == m_fastMovAvgs.lastDate());

        m_signalLine = new PriceTimeSeries();
        Iterator<TimeStampedPrice> slowIter = m_slowMovAvgs.iterator();
        TimeSeries<TimeStampedPrice> fastTailSeries = m_fastMovAvgs.tailSeries(m_slowMovAvgs.firstDate(), true);
        assert (fastTailSeries.size() == m_slowMovAvgs.size());
        Iterator<TimeStampedPrice> fastIter = fastTailSeries.iterator();
        TimeStampedPrice slowMA, fastMA;
        double signal = 0.0;
        while (slowIter.hasNext() && fastIter.hasNext()) {
            slowMA = slowIter.next();
            fastMA = fastIter.next();
            assert (slowMA.getTimeStamp() == fastMA.getTimeStamp());
            assert (slowMA.getCcy().equals(fastMA.getCcy()));

            signal = fastMA.getAmount() - slowMA.getAmount();
            m_signalLine.add(slowMA.getTimeStamp(), signal, slowMA.getCcy());
        }

        assert (m_signalLine.size() == 1);

        if (m_smoothSignalLine) {
            assert (m_numSignalLinePeriods > 0);
            m_signalMovAvgs = m_signalLine.movingAverage(m_numSignalLinePeriods);
            signal = m_signalMovAvgs.lastEntry().getValue().getAmount();
        }

        // we need to establish the last non-zero signal, which is compared with
        // subsequent
        // signals as the bars are processed... on most occasions this is just
        // the signal
        // we've calculated here, but just in case it turns out to be a zero
        // signal,
        // then we don't want to keep it... see below also
        if (signal != 0)
            m_lastNonZeroSignal = signal;

        // HACK: ignores situations where there isn't enough data
        m_isInitialised = true;
    }

    public boolean isInitialized() {
        return m_isInitialised;
    }

    public TradeSignal processBar(HistoryPoint hp) {
        if (hp.getTimeStamp() < m_prices.lastDate()) {
            LOG.debug(String.format("Provided history point %1$s predates MACDSignal %2$TF %2$TT. Ignored.", 
                    hp.toString(), m_prices.lastDate()));
            return TradeSignal.NONE;
        }
        
        if (hp.getTimeStamp() == m_prices.lastDate()) {
            LOG.debug(String.format("Provided history point %1$s same as MACDSignal %2$TF %2$TT. Ignored.", 
                    hp.toString(), m_prices.lastDate()));
            return TradeSignal.NONE;
        }
        
        // not sure if this will be exact?
        if (hp.getTimeStamp() - m_prices.lastDate() != m_prices.getInterval().duration()) {
            LOG.debug(String.format("Provided history point %1$s out of sync with last MACDSignal at %2$TF %2$TT. " +
                    "Restarting MACD generator.", hp.toString(), m_prices.lastDate()));
            m_prices.clear();
        }

        m_prices.add(hp);
        
        //if we don't have enough price history, do nothing until we do
        assert (m_numSlowPeriods > m_numFastPeriods);
        if (m_prices.size() < m_numSlowPeriods)
            return TradeSignal.NONE;
        if (m_prices.size() == m_numSlowPeriods)
            doInitialise();

        double slowMA = 0.0, fastMA = 0.0;

//        if (m_isExponential) {
//            slowMA = Quant.exponentialWeightedAverage(
//                    m_prices.getClosingPrices(n_EXP_SMOOTHING_DATAPTS).values(), m_numSlowPeriods);
//            fastMA = Quant.exponentialWeightedAverage(
//                    m_prices.getClosingPrices(n_EXP_SMOOTHING_DATAPTS).values(), m_numFastPeriods);
//        } else {
            slowMA = Quant.average(m_prices.getClosingPrices(m_numSlowPeriods).values());
            fastMA = Quant.average(m_prices.getClosingPrices(m_numFastPeriods).values());
//        }

        m_slowMovAvgs.add(hp.getTimeStamp(), slowMA, hp.getCcy());
        m_fastMovAvgs.add(hp.getTimeStamp(), fastMA, hp.getCcy());

        double signal = fastMA - slowMA;
        m_signalLine.add(hp.getTimeStamp(), signal, hp.getCcy());

        if (m_smoothSignalLine) {
            assert (m_numSignalLinePeriods > 0);
            assert (m_signalLine.size() >= m_numSignalLinePeriods);

            TimeSeries<TimeStampedPrice> newSignals = m_signalLine.tailSeries(m_signalLine.size()
                    - m_numSignalLinePeriods);
            assert (newSignals.size() == m_numSignalLinePeriods);

            double signalMA = /*m_isExponential ? Quant.exponentialWeightedAverage(newSignals.values()) :*/ 
                Quant.average(newSignals.values());

            m_signalMovAvgs.add(hp.getTimeStamp(), signalMA, hp.getCcy());

            signal = signalMA;
        }

        TradeSignal instruction = TradeSignal.NONE;

        // we require a full cross of the axis by the signal line
        // it must go from strictly positive to strictly negative
        // or the converse

        if (m_lastNonZeroSignal < 0 && signal > 0) {
            LOG.info(String.format("FastMA (%1$6f) has crossed SlowMA (%2$6f) from below yielding a long signal",
                    fastMA, slowMA));
            logMAs();

            instruction = TradeSignal.LONG;
        } else if (m_lastNonZeroSignal > 0 && signal < 0) {
            LOG.info(String.format("FastMA (%1$6f) has crossed SlowMA (%2$6f) from above yielding a short signal",
                    fastMA, slowMA));
            logMAs();
            instruction = TradeSignal.SHORT;
        }

        // in the unusual case where m_lastNonZeroSignal is zero
        // (ie. we didn't find a non-zero signal anywhere in the previous
        // signals)
        // we'll just keep on waiting for a non-zero value (no trading signal)

        // only update the last signal if this one is non-zero.
        // this removes the need to deal with a situation where
        // the signal line stops precisely on zero, and on
        // the next iteration we don't know whether it came from above or below
        if (signal != 0)
            m_lastNonZeroSignal = signal;

        return instruction;
    }
    
    private void logMAs() {
	//log the last 15 data points
	int ndps = 15;
	long timeFirst = m_prices.lastDate() - (ndps * m_prices.getInterval().duration());
	
	Iterator<TimeStampedPrice> fastMAiter = m_fastMovAvgs.tailSeries(timeFirst).iterator();
	Iterator<TimeStampedPrice> slowMAiter = m_slowMovAvgs.tailSeries(timeFirst).iterator();
	Iterator<TimeStampedPrice> signalIter = m_signalLine.tailSeries(timeFirst).iterator();
	while (fastMAiter.hasNext() && slowMAiter.hasNext() && signalIter.hasNext()) {
	    TimeStampedPrice fastMA = fastMAiter.next();
	    LOG.info(String.format("%1$TT: Fast - %2$6f, Slow - %3$6f, Signal - %4$6f", 
		    fastMA.getTimeStamp(),
		    fastMA.getAmount(),
		    slowMAiter.next().getAmount(),
		    signalIter.next().getAmount()));
	}
    }

//    public String getDescription() {
//        return String.format("%1$sMACD (%2$d,%3$d,%4$d)", (m_isExponential ? "Exp " : ""), m_numSlowPeriods,
//                m_numFastPeriods, (m_smoothSignalLine ? m_numSignalLinePeriods : 0));
//    }
    
    public String getDescription() {
        return String.format("MACD (%1$d,%2$d,%3$d)", m_numSlowPeriods,
                m_numFastPeriods, (m_smoothSignalLine ? m_numSignalLinePeriods : 0));
    }

    public String getName() {
        return "Trend following MACD indicator";
    }
}

// private MACDSignal CalcSignal(HistoryPointTimeSeries lastHundredBars)
// {
// Debug.Assert(m_numSlowPeriods > m_numFastPeriods);

// double lastSlowTotal = 0m, slowTotal = 0m, slowDiv = 0m, slowDivTotal = 0m;
// double lastFastTotal = 0m, fastTotal = 0m, fastDiv = 0m, fastDivTotal = 0m;
// double lastSignalTotal = 0m, signalTotal = 0m, signalDiv = 0m, signalDivTotal
// = 0m;

// double slowAlpha = 2.0 / (double)(m_numSlowPeriods + 1);
// double fastAlpha = 2.0 / (double)(m_numFastPeriods + 1);

// double signalAlpha = 0.0;
// if (m_numSignalLinePeriods != 0)
// signalAlpha = 2.0 / (double)(m_numSignalLinePeriods + 1);

// Debug.Assert(lastHundredBars.Count - (m_numSlowPeriods + 1) >= 0);
// IEnumerator<HistoryPoint> enumerator =
// lastHundredBars.PositionAt(Math.Max(lastHundredBars.Count - (m_numSlowPeriods
// + 1), 0));
// int posn = m_numSlowPeriods + 1;
// while (enumerator.MoveNext())
// {
// Debug.Assert(posn >= 0);

// if (m_isExponential)
// {
// slowDiv = (double)Math.Pow(1 - slowAlpha, posn);
// fastDiv = (posn <= m_numFastPeriods ?
// (double)Math.Pow(1 - fastAlpha, posn) : 0m);
// signalDiv = (m_numSignalLinePeriods != 0 && posn <= m_numSignalLinePeriods ?
// (double)Math.Pow(1 - signalAlpha, posn) : 0m);
// }
// else
// {
// slowDiv = 1m;
// fastDiv = (posn <= m_numFastPeriods ? 1m : 0m);
// signalDiv = (m_numSignalLinePeriods != 0 && posn <= m_numSignalLinePeriods ?
// 1m : 0m);
// }

// double closePrice = enumerator.Current.Close.Mean;

// if (posn == m_numSlowPeriods + 1)
// {
// lastSlowTotal += slowDiv * closePrice;
// lastFastTotal += fastDiv * closePrice;
// lastSignalTotal += signalDiv * ((fastDiv * closePrice) - (slowDiv *
// closePrice));
// }
// else if (posn < m_numSlowPeriods + 1 && posn > 0)
// {
// double slowVal = slowDiv * closePrice;
// double fastVal = fastDiv * closePrice;
// double signalVal = signalDiv * ((fastDiv * closePrice) - (slowDiv *
// closePrice));

// lastSlowTotal += slowVal;
// lastFastTotal += fastVal;
// lastSignalTotal += signalVal;

// slowTotal += slowVal;
// fastTotal += fastVal;
// signalTotal += signalVal;

// slowDivTotal += slowDiv;
// fastDivTotal += fastDiv;
// signalDivTotal += signalDiv;
// }
// else if (posn == 0)
// {
// slowTotal += slowDiv * closePrice;
// fastTotal += fastDiv * closePrice;
// signalTotal += signalDiv * ((fastDiv * closePrice) - (slowDiv * closePrice));

// slowDivTotal += slowDiv;
// fastDivTotal += fastDiv;
// signalDivTotal += signalDiv;
// }

// posn--;
// }

// double slowMA = slowTotal / slowDivTotal;
// double lastSlowMA = lastSlowTotal / slowDivTotal;
// double fastMA = fastTotal / fastDivTotal;
// double lastFastMA = lastFastTotal / fastDivTotal;

// double signalMA = 0m;
// double lastSignalMA = 0m;

// if (m_numSignalLinePeriods != 0)
// {
// signalMA = signalTotal / signalDivTotal;
// lastSignalMA = lastSignalTotal / signalDivTotal;
// }
// else
// {
// signalMA = fastMA - slowMA;
// lastSignalMA = lastFastMA - lastSlowMA;
// }

// //wait for a full cross
// if (lastSignalMA <= 0 && signalMA > 0)
// return MACDSignal.CrossedHigher;
// else if (lastSignalMA >= 0 && signalMA < 0)
// return MACDSignal.CrossedLower;

// return MACDSignal.None;
// }
