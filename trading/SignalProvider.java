package c8.trading;

import c8.util.*;
import c8.util.Security;

import com.oanda.fxtrade.api.*;

public interface SignalProvider extends Algorithm, Cloneable {
    public enum TradeSignal {
        NONE, LONG, SHORT
    }

    // / <summary>
    // / Initializes the algorithm with historical data, replacing
    // / any existing dataset
    // / </summary>
    // / <remarks>
    // / The algorithm might require a little or a lot of data. The security
    // / is really only relevant to collect the right data
    // / </remarks>
    // / <param name="tbl">A price table</param>
    // / <param name="sec">The security traded</param>
    void initialize(PriceTable tbl, Security sec, Interval ival);

    // / <summary>
    // / True, if the signal provider has either been initialized or
    // / has collected enough historical data to begin providing
    // / trading signals
    // / </summary>
    // / <remarks>
    // / For as long as the provider is not initialized, ProcessBar
    // / should always return TradeSignal.None
    // / </remarks>
    boolean isInitialized();

    // / <summary>
    // / Processes the latest bar, returning a trade signal
    // / </summary>
    // / <remarks>
    // / The algorithm will not return a signal until it has enough historical
    // / data to be properly initialised
    // / </remarks>
    // / <param name="hp">The latest history point</param>
    // / <returns>A trade signal</returns>
    TradeSignal processBar(HistoryPoint hp);

    SignalProvider clone();
}
