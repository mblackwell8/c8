package c8;

import java.util.Currency;
import c8.trading.*;
import c8.gateway.*;


public interface EnvironmentServer extends MessageSink {
    void start();
    
    String getName();

    long getTime();

    void setTime(long time);

    boolean isTradingBlackout(long time);
    
    boolean isConnected();

    PriceTable getPriceTable();
    
//    Message sendReplyPaid(Message msg, int maxWaitSecs);
//    
//    Message sendReplyPaid(Message msg);
//    
    Transaction execute(MarketOrder order, String accId);

    Transaction modify(MarketOrder order, String accId);

    Transaction close(MarketOrder order, String accId);
    
    MessageSource getMsgSource();
    
    MessageSink getMsgSink();
    
    void setMsgSink(MessageSink sink);

    void setMsgSource(MessageSource source);

    void sleep(long ms);

    Currency getAccountingCcy();

}
