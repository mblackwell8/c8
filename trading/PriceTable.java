package c8.trading;

import c8.util.*;

public interface PriceTable {
    TickPrice getPrice(Security sec);

    boolean hasPrice(Security sec);
    
    HistoryPointTimeSeries getPriceHistory(Security sec, Interval ival,
            int nTicks);

    HistoryPoint getLastHistoryPoint(Security sec, Interval ival);
}