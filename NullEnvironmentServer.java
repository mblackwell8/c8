package c8;

import java.io.IOException;
import java.util.Currency;
import c8.gateway.Message;
import c8.gateway.MessageSink;
import c8.gateway.MessageSource;
import c8.trading.PriceTable;

public class NullEnvironmentServer implements EnvironmentServer {
    public void start() {}
    
    public Message sendReplyPaid(Message msg, int maxWaitSecs) {
	// TODO Auto-generated method stub
	return null;
    }

    public void setMsgSink(MessageSink sink) {
	// TODO Auto-generated method stub
	
    }

    public void setMsgSource(MessageSource source) {
	// TODO Auto-generated method stub
	
    }

    public MessageSink getMsgSink() {
	return null;
    }

    public MessageSource getMsgSource() {
	return null;
    }

    public PriceTable getPriceTable() {
	return null;
    }

    public boolean send(Message msg) {
	return false;
    }
    
    public void close() throws IOException {
    }

    public Message sendReplyPaid(Message msg) {
	return null;
    }

    public Currency getAccountingCcy() {
        return Currency.getInstance("AUD");
    }

    public String getName() {
        return "NullEnvironmentServer";
    }

    public long getTime() {
        return System.currentTimeMillis();
    }

    public boolean isTradingBlackout(long time) {
        return false;
    }

    public String nextUniqueId() {
        return "";
    }

    public void setTime(long time) {

    }

    public void sleep(long ms) {
    }

}
