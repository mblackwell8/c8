package c8.trading;

import c8.util.*;

import java.util.*;

public class RandomSignal implements SignalProvider {
    Random m_tradeDecider;

    int m_longProbabilityPercent;

    static final long SEED = 88;

    public RandomSignal() {
        m_tradeDecider = new Random(SEED);
        m_longProbabilityPercent = 30;
    }

    public int getLongProbabilityPercent() {
        return m_longProbabilityPercent;
    }

    public void setLongProbabilityPercent(int value) {
        m_longProbabilityPercent = value;
    }

    public SignalProvider clone() {
        RandomSignal rsp = new RandomSignal();
        rsp.m_longProbabilityPercent = m_longProbabilityPercent;

        return rsp;
    }

    public void initialize(PriceTable tbl, Security sec, Interval ival) {
        // nothing to do
    }

    public boolean isInitialized() {
        return true;
    }

    public TradeSignal processBar(HistoryPoint hp) {
        if (m_tradeDecider.nextInt() % (100 / m_longProbabilityPercent) == 0)
            return TradeSignal.LONG;
        else
            return TradeSignal.SHORT;

        // never return a hold
    }

    public String getDescription() {
        return "Flip a coin (metaphorically of course)";
    }

    public String getName() {
        return "Completely random algorithm (for testing)";
    }
}
