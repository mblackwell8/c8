package c8.util;

import java.util.*;

public final class Quant {

    private Quant() {
    }

    public static <T extends TimeStampedPrice> double average(Collection<T> series) {
        assert (series.size() > 0);
        if (series.size() == 0)
            return 0;

        return sum(series) / (double) series.size();
    }

    public static <T extends TimeStampedPrice> double sum(Collection<T> series) {
        assert (series.size() > 0);
        if (series.size() == 0)
            return 0;

        double sum = 0;
        for (T price : series)
            sum += price.getAmount();

        return sum;
    }

    public static <T extends TimeStampedPrice> double exponentialWeightedAverage(Collection<T> orderedSeries) {
        return exponentialWeightedAverage(orderedSeries, orderedSeries.size());
    }

    public static <T extends TimeStampedPrice> double exponentialWeightedAverage(Collection<T> orderedSeries,
            int nAlphaPers) {
        // see: http://en.wikipedia.org/wiki/Moving_average
        double alpha = 2.0 / (double) (nAlphaPers + 1);

        return exponentialWeightedAverage(orderedSeries, alpha);
    }

    public static <T extends TimeStampedPrice> double exponentialWeightedAverage(Collection<T> orderedSeries,
            double alpha) {
        // Values of α close to unity have less of a smoothing effect and give
        // greater weight to recent changes in the data, while values of α
        // closer to zero have a greater smoothing effect and are less
        // responsive to recent changes. There is no formally correct procedure
        // for choosing α. Sometimes the statistician's judgment is used to
        // choose an appropriate factor. Alternatively, a statistical technique
        // may be used to optimize the value of α. For example, the method of
        // least squares might be used to determine the value of α for which the
        // sum of the quantities (sn-1 − xn)2 is minimized.

        assert (orderedSeries.size() > 0);
        if (orderedSeries.size() == 0)
            return 0;

        double expMA = 0.0;
        for (T price : orderedSeries) {
            expMA = price.getAmount() * alpha + (1 - alpha) * expMA;
        }

        return expMA;

        // int exponent = orderedSeries.size();
        // double wtdTotal = 0, wtdDiv = 0;
        // double weight = 0;
        // long lastDate = Long.MIN_VALUE;
        // for (T price : orderedSeries) {
        // // iterates from oldest to newest
        // assert (price.getTimeStamp() > lastDate);
        // lastDate = price.getTimeStamp();
        //
        // weight = Math.pow(1.0 - alpha, --exponent);
        //
        // wtdTotal += price.getAmount() * weight;
        // wtdDiv += weight;
        // }
        //
        // assert (exponent == 0);
        //
        // return wtdTotal / wtdDiv;
    }
}