package c8.trading;

import c8.util.*;

public interface TradingDataStore {    
    //void write(LimitOrder lo, String orderer, String comment);

    void write(MarketOrder mo, String orderer, String comment);

    void write(Transaction trans, String transactor, String comment);

    void writeInvestedFunds(String acct, Balance investedFunds, String comment);

    void writeBalance(String acct, Balance balance, String comment);

    void writeEquityValue(String acct, Balance balance, String comment);
}
