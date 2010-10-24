package c8.trading;

import c8.ExecEnviron;
import c8.util.HistoryPoint;
import c8.util.Interval;
import c8.util.Security;

public class ConstantSignal implements SignalProvider {

    private SignalProvider.TradeSignal m_lastSignal = SignalProvider.TradeSignal.LONG;
    
    public void initialize(PriceTable tbl, Security sec, Interval ival) { }

    public boolean isInitialized() {
	return true;
    }

    public TradeSignal processBar(HistoryPoint hp) {
	if (m_lastSignal.equals(SignalProvider.TradeSignal.LONG))
	    m_lastSignal = SignalProvider.TradeSignal.SHORT;
	else
	    m_lastSignal = SignalProvider.TradeSignal.LONG;
	
	return m_lastSignal;
    }

    public String getDescription() {
	return "Always returns the opposite signal to the last one";
    }

    public String getName() {
	return "ConstantSignal";
    }
    
    public SignalProvider clone() {
        ConstantSignal cs = new ConstantSignal();
        cs.m_lastSignal = m_lastSignal;

        return cs;
    }
    
    public static void main(String[] args) {
	System.out.format("Now: %1$TT = %1$d", ExecEnviron.time());
}

}
