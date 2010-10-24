package c8.gateway;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import c8.trading.*;
import c8.util.*;

import com.oanda.fxtrade.api.*;
import com.oanda.fxtrade.api.MarketOrder;

public class Message implements Comparable<Message>, TimeStamped {


    private long m_timeStamp;
    private int m_id;
    private int m_linkedId;

    private int m_nTries = 0;

    private static DateFormat DATEFORMAT;

    static {
        DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    public enum Action {
        //max 30 chars for db
        OpenOrder,
        ModifyOrder,
        CloseOrder,
        FailureNotification,
        SendLastTransaction,
        SendLastTransaction_Reply,
        //OpenOrderConfirmation,
        Trans_Ordered,
        Trans_SL,
        Trans_TP,
        Trans_Other,
        PriceSubscriptionRequest, //m_data contains pair name
        PriceUpdate,
        CloseAllPositions,
        PollIsLoggedOn,
        PollIsLoggedOn_Reply,
        Logoff
    }

    private Action m_action;

    private String m_data = null;

    public enum Priority {
        //max 10 chars for db
        //declare in order of priority, so that natural ordering works
        Immediate, Normal, Low;
    }

    private Priority m_priority;

    private static int NEXT_ID = 0;

    public Message(Action c, String data, Priority p, int linkedMsgId) {
        m_id = NEXT_ID++;
        m_timeStamp = System.currentTimeMillis();
        m_action = c;
        m_priority = p;
        m_data = data;
        m_linkedId = linkedMsgId;
    }

    public Message(Action c, String data, Priority p) {
        this(c, data, p, -1);
    }

    public Message(Action c, String data) {
        this(c, data, Priority.Normal);
    }
    
    public Message(Action c) {
        this(c, "", Priority.Normal);
    }

    public Action getAction() {
        return m_action;
    }

    public String getData() {
        return m_data;
    }

    public long getTimeStamp() {
        return m_timeStamp;
    }

    public int getId() {
        return m_id;
    }

    public int getLinkedId() {
        return m_linkedId;
    }

    public Priority getPriority() {
        return m_priority;
    }

    public boolean equals(Object o) {
        if (o instanceof Message)
            return equals((Message) o);

        return false;
    }

    int incrementTries() {
        return ++m_nTries;
    }

    int getTries() {
        return m_nTries;
    }

    public int hashCode() {
        //must correspond to equals (i think)
        //so that if objects are equal, hashcodes must be equal
        return m_id;
    }

    public String toString() {
        String date = DATEFORMAT.format(new Date(m_timeStamp));
        return String.format("%1$d|%2$s|%3$d|%4$s|%5$s|%6$s", m_id, date, m_linkedId, m_priority, m_action, m_data);
    }

    public static Message parseMessage(String line) {
        if (line == null || line.isEmpty())
            return null;

        String[] parts = line.split("\t");

        //must have id, linked_id, timestamp, priority and action
        assert parts.length >= 5;

        int id = Integer.parseInt(parts[0]);

        long ts = -1;
        try {
            ts = DATEFORMAT.parse(parts[1]).getTime();
        } catch (ParseException e) {
            //fail silently! :( ... just let it be the current time
            System.err.println("Failed to Message.parseMessage on date (?): " + parts[1]);
        }
        int linkedId = Integer.parseInt(parts[2]);
        Priority p = Priority.valueOf(parts[3]);
        Action a = Action.valueOf(parts[4]);


        String d = null;
        if (parts.length == 6) {
            d = parts[5];
        }
        if (parts.length > 6) {
            StringBuilder sb = new StringBuilder();
            for (int i = 5; i < parts.length; i++) {
                sb.append(parts[i]);
                if (i < parts.length)
                    sb.append('\t');
            }

            d = sb.toString();
        }

        Message m = new Message(a, d, p, linkedId);
        m.m_id = id;
        if (ts != -1)
            m.m_timeStamp = ts;

        return m;
    }

    public boolean equals(Message msg) {
        return (m_id == msg.m_id);
    }

    public int compareTo(Message o) {
        //return +ve if this object is greater than passed object
        return compare(this, o);
    }

    public static int compare(Message arg1, Message arg2) {
        //return +ve if arg1 > arg2
        
        int cv = arg1.m_priority.compareTo(arg2.m_priority);

        if (cv == 0) {
            //return a positive integer if arg1 is greater than arg2
            //(ie. if it occurred earlier)
            if (arg1.m_timeStamp < arg2.m_timeStamp)
                cv = 1;
            else if (arg1.m_timeStamp > arg2.m_timeStamp)
                cv = -1;
        }

        return cv;
    }


    public static String orderToString(String accId, c8.trading.MarketOrder mo) {
        //OandaClient relies on acc ID being the first element, and an integer
        String orderStr = String.format("%1$s\t%2$s\t%3$s\t%4$d\t%5$8.5f\t%6$8.5f\t%7$8.5f\t%8$s",
                accId, mo.getOrderNumber(), mo.getSecurity(), mo.getUnits(),
                mo.getPrice().getAmount(),
                mo.getStopLoss().getAmount(),
                mo.getTakeProfit().getAmount(), mo.getId());
        return orderStr;
    }

    //    public static String orderToString(String accId, com.oanda.fxtrade.api.MarketOrder mo) {
    //	String orderStr = String.format("%1$s\t%2$d\t%3$s\t%4$d\t%5$8.5f\t%6$8.5f\t%7$8.5f",
    //		    accId, mo.getTransactionNumber(), mo.getPair(), mo.getUnits(),
    //		    mo.getPrice(),
    //		    mo.getStopLoss().getPrice(),
    //		    mo.getTakeProfit().getPrice());
    //	return orderStr;
    //    }

    public static c8.trading.MarketOrder parseMktOrder(String orderData) {
        MsgMarketOrder mo = new MsgMarketOrder();

        //	pattern is AccountId\tOrderNum\tSecurity\tUnits\t[ExecPrice]\t[StopLossPrice]\t[TakeProfitPrice\t[internalId]]
        String[] parts = orderData.split("\t");
        assert (parts.length >= 4);

        String orderNum = parts[1];
        Security pair = new Security(parts[2]);
        long units = Long.parseLong(parts[3]);

        double execP = -1.0;
        if (parts.length > 4)
            execP = Double.parseDouble(parts[4]);
        double sl = -1.0;
        if (parts.length > 5)
            sl = Double.parseDouble(parts[5]);
        double tp = -1.0;
        if (parts.length > 6)
            tp = Double.parseDouble(parts[6]);
        String id = null;
        if (parts.length > 7)
            id = parts[7];


        mo.setOrderNumber(orderNum);
        mo.setSecurity(pair);
        mo.setUnits(units);

        Currency quoteCcy = pair.parseFxCcy();

        if (execP > 0.0)
            mo.setPrice(new Price(execP, quoteCcy));
        if (sl > 0.0)
            mo.setStopLoss(new Price(sl, quoteCcy));
        if (tp > 0.0)
            mo.setTakeProfit(new Price(tp, quoteCcy));
        if (id != null)
            mo.setId(id);

        return mo;

    }

    public static String transactionToString(c8.trading.Transaction trans) {
        return String.format("%1$d,%2$d,%3$s,%4$d,%5$8.5f,%6$8.5f,%7$8.5f,%8$8.5f,%9$d\t%10$s",
                trans.getTransactionNumber(), trans.getLinkedTransactionNumber(),
                trans.getSecurity(), trans.getUnits(),
                trans.getPrice(), trans.getStopLoss(), trans.getTakeProfit(),
                trans.getBalanceAfter(), trans.getSubtype().ordinal(), trans.getInternalOrderId());
    }

    public static String transactionToString(com.oanda.fxtrade.api.Transaction trans, String internalOrderId) {
        return String.format("%1$d\t%2$d\t%3$s\t%4$d\t%5$8.5f\t%6$8.5f\t%7$8.5f\t%8$8.5f\t%9$d\t%10$s",
                trans.getTransactionNumber(), trans.getTransactionLink(),
                trans.getPair(), trans.getUnits(),
                trans.getPrice(), trans.getStopLoss(), trans.getTakeProfit(),
                trans.getBalance(), trans.getCompletionCode(), internalOrderId);
    }

    public static c8.trading.Transaction parseTransaction(String msgData) {
        String[] parts = msgData.split("\t");

        assert (parts.length == 10);

        String transNumber = parts[0];
        String linkedTransNumber = parts[1];
        Security sec = new Security(parts[2]);
        long units = Long.parseLong(parts[3]);

        Currency qCcy = sec.parseFxCcy();
        Price price = new Price(Double.parseDouble(parts[4]), qCcy);
        Price sl = new Price(Double.parseDouble(parts[5]), qCcy);
        Price tp = new Price(Double.parseDouble(parts[6]), qCcy); 
        Balance balAfter = new Balance(Double.parseDouble(parts[7]), qCcy);
        int completionCode = Integer.parseInt(parts[8]);
        String internalOrderId = parts[9];

        MsgTransaction tr = new MsgTransaction(internalOrderId, transNumber, linkedTransNumber,
                sec, units, price, sl, tp, balAfter, completionCode);

        return tr;
    }

    public static SecurityPrice parseSecPrice(String msgData) throws ParseException {
        String[] parts = msgData.split("\t");
        if (parts.length == 4) {
            long dt = DATEFORMAT.parse(parts[0]).getTime();

            Security pair =  new Security(parts[1]);
            double bid = Double.parseDouble(parts[2]);
            double ask = Double.parseDouble(parts[3]);

            return new SecurityPrice(pair, dt, bid, ask, pair.parseFxCcy());
        }
        else
            throw new ParseException("Incorrect number of tab delimited parts in msgData", -1);
    }
}
