package c8.trading;

import c8.util.Amount;
import c8.util.Price;
import c8.util.TickPrice;

public class PercentBandStopAdjuster implements StopAdjuster {
    double m_takeProfitPercent = DFLT_TP_PERCENT;

    double m_stopLossPercent = DFLT_SL_PERCENT;

    double m_tolerance = DFLT_TOLERANCE;

    public static final double DFLT_TP_PERCENT = 1.25;

    public static final double DFLT_SL_PERCENT = 0.90;

    public static final double DFLT_TOLERANCE = 0.05;

    public PercentBandStopAdjuster() {
    }

    public PercentBandStopAdjuster(double tpPercent, double slPercent,
            double tolerance) {
        m_takeProfitPercent = tpPercent;
        m_stopLossPercent = slPercent;
        m_tolerance = tolerance;
    }

    public double getTakeProfitPercent() {
        return m_takeProfitPercent;
    }

    public void setTakeProfitPercent(double value) {
        m_takeProfitPercent = value;
    }

    public double getStopLossPercent() {
        return m_stopLossPercent;
    }

    public void setStopLossPercent(double value) {
        m_stopLossPercent = value;
    }

    public StopAdjuster clone() {
        return new PercentBandStopAdjuster(m_takeProfitPercent,
                m_stopLossPercent, m_tolerance);
    }

    public Price calculateSL(TickPrice currentPrice, boolean isLong) {
        if (isLong)
            return currentPrice.getBidPrice().multiply(m_stopLossPercent);

        return currentPrice.getAskPrice().multiply(2.0 - m_stopLossPercent);
    }

    public Price calculateTP(TickPrice currentPrice, boolean isLong) {
        if (isLong)
            return currentPrice.getBidPrice().multiply(m_takeProfitPercent);

        return currentPrice.getAskPrice().multiply(2.0 - m_takeProfitPercent);
    }

    public void moveStops(MarketOrder mo, TickPrice currentPrice) {
        assert (shouldMoveStops(mo, currentPrice));

        Price newSL = mo.getStopLoss();
        Price newTP = mo.getTakeProfit();
        if (mo.isLong()) {
            newSL = Amount.max(mo.getStopLoss(), calculateSL(
                    currentPrice, mo.isLong()));
            newTP = Amount.min(mo.getTakeProfit(), calculateTP(
                    currentPrice, mo.isLong()));
        } else {
            // note the reversed Min vs Max treatment for short trades
            newSL = Amount.min(mo.getStopLoss(), calculateSL(
                    currentPrice, mo.isLong()));
            newTP = Amount.max(mo.getTakeProfit(), calculateTP(
                    currentPrice, mo.isLong()));
        }

        mo.setStopLoss(newSL);
        mo.setTakeProfit(newTP);
    }

    public boolean shouldMoveStops(MarketOrder mo, TickPrice currentPrice) {
        // never reduce the stop into a loss making trade
        if ((mo.isLong() && currentPrice.getBidPrice().lessThan(mo.getPrice()))
                || (mo.isShort() && currentPrice.getAskPrice().greaterThan(
                        mo.getPrice())))
            return false;

        double execPrice = mo.isLong() ? currentPrice.getBid() : currentPrice.getAsk();
        if (Math.abs(execPrice * m_stopLossPercent
                / mo.getStopLoss().getAmount() - 1.0) > m_tolerance)
            return true;
        if (Math.abs(execPrice * m_takeProfitPercent
                / mo.getTakeProfit().getAmount() - 1.0) > m_tolerance)
            return true;

        return false;
    }

    public String getDescription() {
        return String
        .format(
                "Moves stops to remain %1$% above, %2$% below, with %3$% tolerance",
                m_takeProfitPercent - 1, Math
                .abs(m_stopLossPercent - 1), m_tolerance);
    }

    public String getName() {
        return "Percent Band Stop Adjuster";
    }
}
