package c8.trading;

import c8.util.*;
import c8.util.Security;

public interface Order extends TimeStamped {
    //internal order identifier... nothing to do with Oanda, but always valid
    String getId();

    //Oanda order number
    void setOrderNumber(String ordNum);
    String getOrderNumber();

    Security getSecurity();

    void setSecurity(Security s);

    long getUnits();

    void setUnits(long units);

    // True, if the order is considered long (generally Units > 0)
    boolean isLong();

    // True, if the order is considered long (generally Units < 0)
    boolean isShort();

    // / Is this the executed price or the order price... it might come
    // / back (from OANDA) as a market order with the executed price inserted
    void setPrice(Price p);
    Price getPrice();

    Price getStopLoss();

    void setStopLoss(Price sl);

    Price getTakeProfit();

    void setTakeProfit(Price tp);
}