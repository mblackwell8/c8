package c8.util;

import c8.ExecEnviron;
import c8.gateway.*;
import c8.trading.PriceTable;

import java.util.Currency;

public class CurrencyConverter {
//    PriceTable m_fxPrices;
//
//    public CurrencyConverter(PriceTable fxPrices) {
//        m_fxPrices = fxPrices;
//    }

    /**
     * Converts the provided foreign currency amount into the return currency
     * Returns the converted amount, or zero if no conversion is possible
     */
    public static Amount convert(Amount foreignAmt, Currency returnCcy) {
        return convert(ExecEnviron.getPriceTable(), foreignAmt, returnCcy);
    }

    public static Amount convert(PriceTable fxPrices, Amount foreignAmt, Currency returnCcy) {
        if (foreignAmt.getCcy().equals(returnCcy) || foreignAmt.getAmount() == 0) {
            return new Amount(foreignAmt.getAmount(), returnCcy);
        }

        // egs below are based on returnCcy = AUD and foreignCcy = USD
        // first try direct, where the quote ccy is the return ccy
        Security direct = new Security(String.format("%1$s/%2$s", foreignAmt.getCcy().getCurrencyCode(), returnCcy
                .getCurrencyCode()));
        if (fxPrices.hasPrice(direct)) {
            TickPrice tp = fxPrices.getPrice(direct);

            // eg buy the AUD (base), sell the USD (quote)
            return new Amount(foreignAmt.getAmount() * tp.getBid(), returnCcy);
        }

        // then try inversed
        Security inversed = new Security(String.format("%1$s/%2$s", returnCcy.getCurrencyCode(), foreignAmt.getCcy()
                .getCurrencyCode()));
        if (fxPrices.hasPrice(inversed)) {
            TickPrice tp = fxPrices.getPrice(inversed);

            // eg buy the AUD (quote), sell the USD (base)
            return new Amount(foreignAmt.getAmount() / tp.getAsk(), returnCcy);
        }

        // then try a USD-based or EUR-based cross, via **recursion**...
        // but first prevent that very recursion from recursing again, because
        // if there isn't a direct inverse pair via USD above, then there's no
        // hope
        // of converting the currency
        Currency USD = Currency.getInstance("USD");
        if (foreignAmt.getCcy().equals(USD) || returnCcy.equals(USD))
            return new Amount(0, returnCcy);
        Amount amtInUSD = convert(fxPrices, foreignAmt, USD);
        Amount returnAmtBasedOnUSDcross = convert(fxPrices, amtInUSD, returnCcy);

        // then try a EUR-based cross, which might also go via USD
        Currency EUR = Currency.getInstance("EUR");
        if (foreignAmt.getCcy().equals(EUR) || returnCcy.equals(EUR)) {
            return new Amount(0, returnCcy);
        }

        Amount amtInEUR = convert(fxPrices, foreignAmt, EUR);
        Amount returnAmtBasedOnEURcross = convert(fxPrices, amtInEUR, returnCcy);

        assert (returnAmtBasedOnUSDcross.getAmount() == 0 || returnAmtBasedOnEURcross.getAmount() == 0 || Math
                .abs(returnAmtBasedOnUSDcross.getAmount() / returnAmtBasedOnEURcross.getAmount() - 1) < 0.01);

        if (foreignAmt.getAmount() != 0 && returnAmtBasedOnEURcross.getAmount() == 0
                && returnAmtBasedOnUSDcross.getAmount() == 0)
            throw new CurrencyConverterException(foreignAmt.getCcy(), returnCcy);

        // if we have a negative Amount, return the lowest (maximum)
        // Amount (ie. the most efficient cross)
        if (foreignAmt.getAmount() < 0) {
            if (returnAmtBasedOnUSDcross.getAmount() <= returnAmtBasedOnEURcross.getAmount())
                return returnAmtBasedOnUSDcross;
            else
                return returnAmtBasedOnEURcross;
        }

        // if we have a positive Amount return the highest (maximum)
        // Amount
        if (returnAmtBasedOnUSDcross.getAmount() >= returnAmtBasedOnEURcross.getAmount())
            return returnAmtBasedOnUSDcross;
        else
            return returnAmtBasedOnEURcross;
    }

    public static Currency parseBase(Security sec) {
        return parseBase(sec.getTicker());
    }

    public static Currency parseBase(String baseSlashQuote) {
        // throws an IllegalArgumentException if the currency is not found
        String base = baseSlashQuote.substring(0, 3);

        return Currency.getInstance(base);
    }

    public static Currency parseQuote(Security sec) {
        return parseQuote(sec.getTicker());
    }

    public static Currency parseQuote(String baseSlashQuote) {
        // throws an IllegalArgumentException if the currency is not found
        String quote = baseSlashQuote.substring(4, 7);

        return Currency.getInstance(quote);
    }
}