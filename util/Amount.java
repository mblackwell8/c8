package c8.util;

import java.text.ParseException;
import java.util.Currency;


public class Amount implements Comparable<Amount> {
    private double m_amt;

    private Currency m_ccy;
    
    public Amount(double amount, Currency ccy) {
        m_amt = amount;
        m_ccy = ccy;
    }

    public double getAmount() {
        return m_amt;
    }

    public Currency getCcy() {
        return m_ccy;
    }

    public Amount convert(Currency targetCcy) {
        return convert(this, targetCcy);
    }

    public static Amount convert(Amount amt, Currency ccy) {
        return CurrencyConverter.convert(amt, ccy);
    }

    public int compareTo(Amount o) {
        return compare(this, o);
    }

    public static int compare(Amount arg1, Amount arg2) {
        if (!arg1.m_ccy.equals(arg2.m_ccy))
            return compare(arg1, convert(arg2, arg1.m_ccy));

        return Double.compare(arg1.m_amt, arg2.m_amt);
    }

    public boolean lessThan(Amount amt) {
        if (!m_ccy.equals(amt.m_ccy))
            return lessThan(convert(amt, m_ccy));

        return m_amt < amt.m_amt;
    }

    public boolean lessThanOrEqualTo(Amount amt) {
        return (this.lessThan(amt) || this.equalsConvertedAmount(amt));
    }

    public boolean greaterThanOrEqualTo(Amount amt) {
        return (this.greaterThan(amt) || this.equalsConvertedAmount(amt));
    }

    public boolean greaterThan(Amount amt) {
        if (!m_ccy.equals(amt.m_ccy))
            return greaterThan(convert(amt, m_ccy));

        return m_amt > amt.m_amt;
    }

    public static <T extends Amount> T max(T arg1, T arg2) {
        if (compare(arg1, arg2) < 0)
            return arg2;

        return arg1;
    }

    public static <T extends Amount> T min(T arg1, T arg2) {
        if (compare(arg1, arg2) < 0)
            return arg1;

        return arg2;
    }

    public boolean equals(Object o) {
        if (o instanceof Amount)
            return equals((Amount) o);

        return false;
    }

    public int hashCode() {
        // multiply by a prime and add...
        return (int) (m_ccy.hashCode() * 31 + m_amt);
    }

    public String toString() {
        return String.format("%1$2.2f %2$s", m_amt, m_ccy.getCurrencyCode());
    }
    
    public static Amount parse(String amt) throws ParseException {
	amt = amt.trim();
	String[] parts = amt.split(" ");
	
	//fussy implementation
	if (parts.length != 2)
	    throw new ParseException(
		    String.format("Amount.parse must be formatted as 'double ccy', which '%1$s is not", amt),
		    0);
	
	double amtDbl = Double.parseDouble(parts[0]);
	Currency ccy = Currency.getInstance(parts[1]);
	
	return new Amount(amtDbl, ccy);
    }

    public boolean equals(Amount amt) {
        // doesn't do any conversion...
        return (m_amt == amt.m_amt && m_ccy.equals(amt.m_ccy));
    }

    public boolean equalsConvertedAmount(Amount amt) {
        return equals(amt.convert(m_ccy));
    }

    public static Amount multiply(Amount amt, double by) {
        return new Amount(amt.m_amt * by, amt.m_ccy);
    }

    public static Amount divide(Amount amt, double by) {
        return new Amount(amt.m_amt / by, amt.m_ccy);
    }

    public static Amount add(Amount amt1, double added) {
        return new Amount(amt1.m_amt + added, amt1.m_ccy);
    }

    public static Amount add(Amount amt1,
            Amount amt2) {
        if (!amt1.m_ccy.equals(amt2.m_ccy))
            return add(amt1, convert(amt2, amt1.getCcy()));

        return add(amt1, amt2.m_amt);
    }

    public static Amount subtract(Amount amt1,
            double subtracted) {
        return new Amount(amt1.m_amt - subtracted, amt1.m_ccy);
    }

    public static Amount subtract(Amount amt1,
            Amount amt2) {
        if (!amt1.m_ccy.equals(amt2.m_ccy))
            return subtract(amt1, convert(amt2, amt1.getCcy()));

        return subtract(amt1, amt2.m_amt);
    }
    
    public static void main(String[] args) {
	Amount tenUSD = new Amount(10, Currency.getInstance("USD"));
	System.out.println("Created ten USD: " + tenUSD.toString());
	
	Amount fiveUSD = new Amount(5, Currency.getInstance("USD"));
	System.out.println("Created five USD: " + fiveUSD.toString());
	
	Amount sum = add(fiveUSD, tenUSD);
	System.out.println("Sum of ten plus five USD is: " + sum.toString());
	
	Amount diff = subtract(tenUSD, fiveUSD);
	System.out.println("Diff of ten minus five USD is: " + diff.toString());
	
	System.out.println("Ten USD is more than Five USD? " + tenUSD.greaterThan(fiveUSD));
	
//	BasicConfigurator.configure();
//	ExecEnviron.start("OANDA_Game");
//	ExecEnviron.market().getLoginner().login(ExecEnviron.market());
//	
//	//test a standard pair
//	Amount tenAUD = new Amount(10, Currency.getInstance("AUD"));
//	
//	sum = add(tenAUD, tenUSD);
//	System.out.println("Sum of ten AUD plus ten USD is: " + sum.toString());
//	
//	diff = subtract(tenUSD, tenAUD);
//	System.out.println("Diff of ten USD minus ten AUD is: " + diff.toString());
//	
//	System.out.println("Ten USD is more than Ten AUD? " + tenUSD.greaterThan(tenAUD));
//	
//	ExecEnviron.market().logout();
    }
}
