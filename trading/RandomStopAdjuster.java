package c8.trading;

import java.util.Random;

import c8.util.Price;
import c8.util.TickPrice;

public class RandomStopAdjuster implements StopAdjuster {

    private Random m_plusMinusPercent;
    private Random m_shouldUpdateStops;

    static final long SEED = 88;

    public RandomStopAdjuster() {
        m_plusMinusPercent = new Random(SEED);
        m_shouldUpdateStops = new Random(SEED);
    }

    public Price calculateSL(TickPrice currentPrice, boolean isLong) {
        double plusMinusPercent = m_plusMinusPercent.nextDouble();

        if (isLong)
            return new Price(currentPrice.getBid() * (1.0 - plusMinusPercent), currentPrice.getCcy());

        return new Price(currentPrice.getAsk() * (1.0 + plusMinusPercent), currentPrice.getCcy());
    }

    public Price calculateTP(TickPrice currentPrice, boolean isLong) {
        double plusMinusPercent = m_plusMinusPercent.nextDouble();

        if (isLong)
            return new Price(currentPrice.getBid() * (1.0 + plusMinusPercent), currentPrice.getCcy());

        return new Price(currentPrice.getAsk() * (1.0 - plusMinusPercent), currentPrice.getCcy());
    }

    public void moveStops(MarketOrder mo, TickPrice currentPrice) {
        mo.setStopLoss(calculateSL(currentPrice, mo.isLong()));
        mo.setTakeProfit(calculateTP(currentPrice, mo.isLong()));
    }

    public boolean shouldMoveStops(MarketOrder mo, TickPrice currentPrice) {
        return m_shouldUpdateStops.nextBoolean();
    }

    public String getDescription() {
        return "Sets a randomly generated percentage plus/minus band around currentPrice";
    }

    public String getName() {
        return "Random Stop Adjuster";
    }

    public StopAdjuster clone() {
        return new RandomStopAdjuster();
    }

}
