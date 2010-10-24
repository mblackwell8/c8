package c8.util;

import java.util.Currency;


//make this class final because of the operator return types
public final class Price extends Amount {
    public static Price Zero;
    
    static {
        Zero = new Price(0, Currency.getInstance("USD"));
    }
    
    //might be a tad dodgy design... copy constructor based on a super type?
    public Price(Amount amt) {
        super(amt.getAmount(), amt.getCcy());
    }

    public Price(double amount, Currency ccy) {
        super(amount, ccy);
    }

    public Price subtract(double amount) {
        return new Price(Amount.subtract(this, amount));
    }

    public Price subtract(Amount amount) {
        return new Price(Amount.subtract(this, amount));
    }

    public Price add(double amount) {
        return new Price(Amount.add(this, amount));
    }

    public Price add(Amount amount) {
        return new Price(Amount.add(this, amount));
    }

    public Price multiply(double by) {
        return new Price(Amount.multiply(this, by));
    }

    public Price divide(double by) {
        return new Price(Amount.divide(this, by));
    }

    public Price convert(Currency targetCcy) {
        return new Price(Amount.convert(this, targetCcy));
    }
    
    public String toString() {
        return String.format("%1$2.5f %2$s", getAmount(), getCcy().getCurrencyCode());
    }
}
