package c8.util;

import java.util.*;

//public class PriceTimeSeries<E extends TimeStampedPrice> extends TimeSeries<E>
public class PriceTimeSeries extends TimeSeries<TimeStampedPrice> {
    public PriceTimeSeries() {
    }

    // public PriceTimeSeries<TimeStampedPrice> movingAverage(int periods)
    public PriceTimeSeries movingAverage(int periods) {
        if (this.size() < periods)
            throw new RuntimeException(
                    String.format("Cannot calculate an %1$d period MA with only %2$d data points",
                                    periods, this.size()));

        // PriceTimeSeries<TimeStampedPrice> mavgs = new
        // PriceTimeSeries<TimeStampedPrice>();
        // Queue<E> periodPrices = new ArrayDeque<E>(periods);
        PriceTimeSeries mavgs = new PriceTimeSeries();
        Queue<TimeStampedPrice> periodPrices = new ArrayDeque<TimeStampedPrice>(periods);
        double runningTotal = 0;
        // for(E price : this)
        for (TimeStampedPrice price : this) {
            runningTotal += price.getAmount();
            periodPrices.add(price);
            if (periodPrices.size() >= periods) {
                assert (periodPrices.size() == periods);

                double mavg = runningTotal / periods;
                mavgs.add(new SimplePrice(price.getTimeStamp(), mavg, price.getCcy()));

                // remove the oldest price and subtract it from the total
                runningTotal -= periodPrices.poll().getAmount();
            }
        }
        
        assert (mavgs.size() == this.size() - periods + 1);

        return mavgs;
    }

    public boolean add(long time, double price, Currency ccy) {
        return super.add(new SimplePrice(time, price, ccy));
    }

    public class SimplePrice extends Amount implements
            TimeStampedPrice {
        long m_timestamp;

        public SimplePrice(long time, double price, Currency ccy) {
            super(price, ccy);
            m_timestamp = time;
        }

        public long getTimeStamp() {
            return m_timestamp;
        }

        @Override
        public String toString() {
            return String.format("%1$TT %2$s", m_timestamp, super.toString());
        }
    }

}
