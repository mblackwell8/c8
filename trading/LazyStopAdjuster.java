package c8.trading;

import c8.util.Price;
import c8.util.TickPrice;

public class LazyStopAdjuster implements StopAdjuster {

    public StopAdjuster clone() {
        return new LazyStopAdjuster();
    }

    public Price calculateSL(TickPrice currentPrice, boolean isLong) {
        if (isLong)
            return new Price(currentPrice.getBid() * 0.5, currentPrice.getCcy());

        return new Price(currentPrice.getBid() * 2.0, currentPrice.getCcy());
    }

    public Price calculateTP(TickPrice currentPrice, boolean isLong) {
        if (isLong)
            return new Price(currentPrice.getBid() * 2.0, currentPrice.getCcy());

        return new Price(currentPrice.getBid() * 0.5, currentPrice.getCcy());
    }

    public void moveStops(MarketOrder mo, TickPrice currentPrice) {
        // do nothing
    }

    public boolean shouldMoveStops(MarketOrder mo, TickPrice currentPrice) {
        return false;
    }

    public String getDescription() {
        return "Does nothing";
    }

    public String getName() {
        return "Lazy Stop Adjuster";
    }
}
