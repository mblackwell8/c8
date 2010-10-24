package c8.trading;

import c8.util.*;

public interface Transaction extends TimeStamped {
    String getTransactionNumber();
    Security getSecurity();

    long getUnits();
    boolean isBuy();
    boolean isSell();

    Price getPrice();
    Price getStopLoss();
    Price getTakeProfit();

    //transaction number of the other side of this transaction,
    //if there is one. Empty if not
    String getLinkedTransactionNumber();
    Balance getBalanceAfter();
    String getInternalOrderId();

    Code getSubtype();

    public enum Code {
        Order,
        StopLoss,
        TakeProfit,
        MarginCall,
        Interest,
        ManualChange,
        Other,
        Unknown
    }
}