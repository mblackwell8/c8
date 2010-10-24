package c8.util;

import java.util.Currency;

public interface TimeStampedPrice extends TimeStamped {
    public double getAmount();

    public Currency getCcy();
}
