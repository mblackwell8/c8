package c8.util;

import java.util.Currency;

//make this class final because of the operator return types
public final class Balance extends Amount implements TimeStamped {
    long m_timestamp;
    
    

    private Balance(Amount amount) {
        super(amount.getAmount(), amount.getCcy());
        m_timestamp = System.currentTimeMillis();
    }

    public Balance(double amount, Currency ccy) {
        super(amount, ccy);
        m_timestamp = System.currentTimeMillis();
    }

    public long getTimeStamp() {
        return m_timestamp;
    }

    public Balance subtract(double amount) {
        return new Balance(Amount.subtract(this, amount));
    }

    public Balance subtract(Amount amount) {
        return new Balance(Amount.subtract(this, amount));
    }

    public Balance add(double amount) {
        return new Balance(Amount.add(this, amount));
    }

    public Balance add(Amount amount) {
        return new Balance(Amount.add(this, amount));
    }

    public Balance multiply(double by) {
        return new Balance(Amount.multiply(this, by));
    }

    public Balance divide(double by) {
        return new Balance(Amount.divide(this, by));
    }

    public Balance convert(Currency targetCcy) {
        return new Balance(Amount.convert(this, targetCcy));
    }
    
    public String toString() {
        return String.format("%1$TF %1$TT %2$s", m_timestamp, super.toString());
    }

}
