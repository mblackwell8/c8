package c8.trading;

import c8.util.*;

public interface MarketOrder extends Order, Cloneable {

    
    //OANDA has a TransactionLink, which might for example link this market order
    //back to the LimitOrder... no current use and is confusing
    //String getTransactionNumber();

    /**
     * Realised PL, in the home currency
     */
    Balance realisedPL();

    /**
     * Unrealised PL, in the home currency
     */
    Balance unrealisedPL();

    /**
     * Funds not protected by SL, in the home currency (ie. exec price - SL * units)
     * Returns an Amount because this doesn't vary in time
     */
    Amount fundsAtRisk();

    MarketOrder clone();
}