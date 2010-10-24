package c8.util;

import java.util.Currency;

public class CurrencyConverterException extends RuntimeException {
    Currency m_fromCcy;

    Currency m_toCcy;

    public CurrencyConverterException(Currency fromCcy, Currency toCcy) {
        this(fromCcy, toCcy, null);
    }

    public CurrencyConverterException(Currency fromCcy, Currency toCcy,
            Throwable cause) {
        super(String.format("Cannot convert from %1$s to %2$s", fromCcy
                .getCurrencyCode(), toCcy.getCurrencyCode()), cause);
        m_fromCcy = fromCcy;
        m_toCcy = toCcy;
    }

}
