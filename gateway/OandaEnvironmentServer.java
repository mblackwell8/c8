package c8.gateway;

import java.io.IOException;
import java.sql.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


import c8.EnvironmentServer;
import c8.trading.*;
import c8.util.*;

public class OandaEnvironmentServer implements EnvironmentServer {

    private long m_localTimeLag = 0;

    private MessageSource m_source;
    private MessageSink m_sink;
    private Timer m_recvCycle;
    private DbPriceTable m_pt;

    //keyed by the linked message number
    private ConcurrentHashMap<String, Transaction> m_sltpTransactions;

    private PriorityBlockingQueue<Message> m_incomingMessageQueue;

    //wait two mins before returning a failure
    private static int DFLT_REPLY_WAIT_SECS = 120;
    private int m_initDelaySecs = 0;
    private int m_cycleSecs = 5;
    
    //start with true  because want to test connection on first try
    private boolean m_wasNullReplyLast = true;

    static final Logger LOG = LogManager.getLogger(OandaEnvironmentServer.class);

    public OandaEnvironmentServer() {
    }

    public void start() {

        //m_source = new DbMessageSource();
        //m_sink = new DbMessageSink();
        m_pt = new DbPriceTable();
        m_incomingMessageQueue = new PriorityBlockingQueue<Message>();
        m_sltpTransactions = new ConcurrentHashMap<String, Transaction>();

        m_recvCycle = new Timer("OES_worker");
        m_recvCycle.scheduleAtFixedRate(new TimerTask() { 
            public void run() {
                try {
                    if (m_source == null) {
                        LOG.info("Cannot receive messages yet");
                        return;
                    }

                    Message recd = null;
                    while ((recd = m_source.getNext()) != null) {
                        switch (recd.getAction()) {
                        case PriceUpdate:
                            updatePrice(recd);
                            break;
                        case Trans_Ordered:
                        case FailureNotification:
                        case SendLastTransaction_Reply:
                        case PollIsLoggedOn_Reply:
                            if (!m_incomingMessageQueue.add(recd))
                                LOG.error("Incoming transaction could not be added to queue.");
                            break;
                        case Trans_SL:
                        case Trans_TP:
                            logSLTP(recd);
                            break;
                        case Trans_Other:
                            LOG.debug("Trans_Other type received, UNHANDLED: " + recd.toString());
                            break;
                        

                        default:
                            LOG.debug("Message recd (ignored): " + recd.toString());
                        }
                    }
                }
                catch (RuntimeException e) {
                    LOG.error("Exception: ", e);
                }
            }
        }, m_initDelaySecs * 1000, m_cycleSecs * 1000);
    }

    public void destroy() {
        LOG.info("Destroying OandaEnvServer_Receiver...");
        m_recvCycle.cancel();
    }

    public String getName() {
        return "OANDA Environment";
    }

    public long getTime() {
        //this should and will always return UTC
        return System.currentTimeMillis() + m_localTimeLag;
    }

    public void setTime(long time) {
        m_localTimeLag = time - System.currentTimeMillis();
        LOG.info(String.format(
                "New time set to %1$tF %1$tT. Local lag = %2$6.2f secs\n",
                time, (double) m_localTimeLag / 1000.0));
    }

    public boolean isTradingBlackout(long time) {
        return false;
    }

    public boolean isConnected() {
        if (!m_wasNullReplyLast)
            return true;
        
        LOG.info("Null reply to last message... checking connection");
        Message reply = sendReplyPaid(new Message(Message.Action.PollIsLoggedOn));
        if (reply != null && reply.getAction().equals(Message.Action.PollIsLoggedOn_Reply)) {
            LOG.info("Connection ok? " + reply.getData());
            if (Boolean.parseBoolean(reply.getData())) {
                return true;
            }
        }
        
        LOG.warn("null reply (or not Message.Action.PollIsLoggedOn_Reply)... not connected");
    
        return false;
        
    }
    public void sleep(long ms) {
        try {
            //            LOG.info("Clearing reply queue on client");
            //            long start = this.getTime();
            //            
            //            //be conservative and give the handler only 75% of the available time
            //            m_gateway.handleReceivedMessages(ms * 3 / 4);
            //            long finish = this.getTime();
            //            
            //            if (finish - start > ms) {
            //        	LOG.info(String.format("Ooops... we overran the available %1$g ms by %2$g ms", ms, finish - start));
            //            }
            //            else {
            //        	Thread.sleep(ms - (finish - start));
            //            }
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // TODO: log this?
        }
    }

    public Currency getAccountingCcy() {
        return Currency.getInstance("AUD");
    }

    public PriceTable getPriceTable() {
        return m_pt;
    }

    public boolean send(Message msg) {
        if (m_sink == null) {
            LOG.error("Cannot send messages yet. Sink not yet specified. Message lost: " + msg.toString());
            return false;
        }

        LOG.debug("Sending message: " + msg.toString());
        if (!m_sink.send(msg)) {
            LOG.error("Could not send message: " + msg.toString());
            return false;
        }

        return true;
    }

    public void close() throws IOException {
        m_sink.close();
        m_source.close();
        m_pt.close();
    }

    public Transaction execute(MarketOrder order, String accId) {   
        if (order == null) {
            LOG.error("Order is null!");
            return null;
        }
        
        Transaction reply = sendOrder(Message.Action.OpenOrder, order, accId);
        
        if (reply != null)
            LOG.debug("Order executed: " + reply.toString());

        return reply;
    }

    public Transaction modify(MarketOrder order, String accId) {
        if (order == null || order.getOrderNumber() == null) {
            LOG.error("Cannot modify: " + (order == null ? "null order" : order.toString()));
            return null;
        }

        Transaction reply = null;
        //first check for SLTP
        if (m_sltpTransactions.containsKey(order.getOrderNumber())) {
            reply = m_sltpTransactions.get(order.getOrderNumber());
            LOG.info("Modified order was stopped out: " + reply.toString());
        }
        else {
            reply = sendOrder(Message.Action.ModifyOrder, order, accId);

            if (reply != null)
                LOG.debug("Order modified: " + reply.toString());
        }

        return reply;
    }

    public Transaction close(MarketOrder order, String accId) {
        if (order == null || order.getOrderNumber() == null) {
            LOG.error("Cannot close: " + (order == null ? "null order" : order.toString()));
            return null;
        }
        
        Transaction reply = null;
        //first check for SLTP
        if (m_sltpTransactions.containsKey(order.getOrderNumber())) {
            reply = m_sltpTransactions.get(order.getOrderNumber());
            LOG.info("Closed order was stopped out: " + reply.toString());
        }
        else {
            reply = sendOrder(Message.Action.CloseOrder, order, accId);

            if (reply != null)
                LOG.debug("Order closed: " + reply.toString());
        }

        return reply;
    }
    
    private Transaction sendOrder(Message.Action orderType, MarketOrder order, String accId) {
        String orderData = Message.orderToString(accId, order);
        Message msg = new Message(orderType, orderData, Message.Priority.Immediate);

        Message reply = sendReplyPaid(msg);

        Transaction t = null;
        if (reply == null) {
            LOG.warn("Null transaction returned on message: " + msg.toString());
            LOG.info("Checking Oanda transaction record");
            
            //send a msg saying "resend last transaction"
            Message resendReq = new Message(Message.Action.SendLastTransaction, accId, Message.Priority.Immediate);
            Message resendResponse = sendReplyPaid(resendReq);

            //if this transaction is linked to the current order, then it has occurred
            if (resendResponse != null && resendResponse.getAction().equals(Message.Action.SendLastTransaction_Reply)) {
                Transaction last = Message.parseTransaction(resendResponse.getData());
                //an OpenOrder Transaction number will match the MarketOrder
                //but we don't have the MarketOrder number yet...
                if (orderType.equals(Message.Action.OpenOrder)) {
                    LOG.warn("Matching OpenOrder to resendResponse is not deterministic");
                    if (last.getSecurity().equals(order.getSecurity()) &&
                        last.getUnits() == order.getUnits()) {
                        LOG.warn(String.format("Order %1$s assumed to match the last Transaction %2$s", order, last));
                        LOG.info("Last transaction is linked to this order.  Previously null reply ignored");
                        t = last;
                    }
                    else {
                        LOG.warn(String.format("Order %1$s assumed NOT to match the last Transaction %2$s", order, last));
                        t = null;
                    }
                }
                //a ModifyOrder or CloseOrder has a TransactionLink number the same
                //as the MarketOrder number
                else if ((orderType.equals(Message.Action.ModifyOrder) || orderType.equals(Message.Action.CloseOrder)) &&
                        last.getLinkedTransactionNumber().equals(order.getOrderNumber())) {
                    LOG.info("Last transaction is linked to this order.  Previously null reply ignored");
                    t = last;
                }
                else {
                    LOG.error("Illegal orderType sent to sendOrder()");
                    t = null;
                }
            }
            //if not, assume that the transaction was ignored or not sent, and return null
            else {
                LOG.info("No response to SendLastTransaction.  Order assumed not be sent.");
                t = null;
            }
            
        }              
        else if (reply.getAction().equals(Message.Action.FailureNotification)) {
            LOG.error("Failure notification received: " + reply.toString());
            t = null;
        }
        else {
            t = Message.parseTransaction(reply.getData());
        }

        return t;

    }

    //don't allow more than one thread at a time...
    //TODO: allow multiple threads, by storing out of order replies somehow
    private synchronized Message sendReplyPaid(Message msg) {
        send(msg);

        //wait for a matching reply
        //TODO: what happens if the order is stopped out while we're waiting??
        Message reply = null;
        try {
            int waitSecs = DFLT_REPLY_WAIT_SECS;
            while (reply == null && waitSecs > 0) {
                long startIter = System.currentTimeMillis();
                LOG.debug(String.format("Starting wait iter @ %1$TT, %2$d secs left", startIter, waitSecs));
                Message maybeReply = m_incomingMessageQueue.poll(waitSecs, TimeUnit.SECONDS);
                if (maybeReply == null) {
                    LOG.error("No reply to message: " + msg.toString());
                }
                else if (msg.getId() == maybeReply.getLinkedId()) {
                    reply = maybeReply;
                }
                else {
                    //HACK: ignoring out of order replies
                    //what if there are multiple replies to the one msg? unlikely i guess,
                    //and in any case it will simply replace the prev one with that linked id
                    LOG.error("Out of order Transaction reply received: " + maybeReply.toString());
                }

                waitSecs -= ((int)(System.currentTimeMillis() - startIter)) / 1000;
                if (reply == null && waitSecs <= 0)
                    LOG.info(String.format("sendReplyPaid timed out after %1$d secs", DFLT_REPLY_WAIT_SECS));
            }
        } catch (InterruptedException e) {
            LOG.error("sendReplyPaid interrupted for msg: " + msg.toString());
        }
        
        m_wasNullReplyLast = (reply == null);

        return reply;
    }

    public MessageSink getMsgSink() {
        return m_sink;
    }

    public MessageSource getMsgSource() {
        return m_source;
    }

    public void setMsgSink(MessageSink sink) {
        m_sink = sink;
    }

    public void setMsgSource(MessageSource source) {
        m_source = source;
    }

    private void updatePrice(Message priceUpdate) {
        if (priceUpdate.getAction().equals(Message.Action.PriceUpdate)) {
            LOG.debug("Price update received: " + priceUpdate.getData());
            try {
                SecurityPrice sp = Message.parseSecPrice(priceUpdate.getData());

                m_pt.setPrice(sp);
            } catch (ParseException e) {
                LOG.error("Missed price update: " + priceUpdate.getData(), e);
            } catch (RuntimeException e) {
                LOG.error("Missed price update: " + priceUpdate.getData(), e);
            }
        }
    }

    private void logSLTP(Message transSLTP) {
        if (transSLTP.getAction().equals(Message.Action.Trans_SL) ||
                transSLTP.getAction().equals(Message.Action.Trans_TP)) {
            LOG.debug("Transaction update received: " + transSLTP.toString());
            Transaction t = Message.parseTransaction(transSLTP.getData());
            Transaction prevMap = m_sltpTransactions.put(t.getLinkedTransactionNumber(), t);
            if (prevMap != null) {
                LOG.error("Current SL/TP transaction has replaced a previous transaction with the same linked ID. " +
                        "Lost message was: " + prevMap.toString());
            }
        }
        else {
            LOG.error("Illegal call to logTransaction... should only be called with Trans_SL or Trans_TP, not " + 
                    transSLTP.getAction().toString());
        }
    }


    public class DbPriceTable implements PriceTable, java.io.Closeable {

        //depending on speed may cache things here... but for now let's just
        //query the db for everything

        Connection m_dbConn;
        PreparedStatement m_getHpStmt;
        PreparedStatement m_getTickStmt;

        String m_dbAcc = "jdbc:mysql://localhost:3306/fx_data";
        String m_userName = "mark";
        String m_pwd = "alliecat8";

        HashMap<Security, TickPrice> m_latestPrices;

        long m_lastDbAccessTime = -1;

        //i think that this is actually 1800 seconds, but mostly just want to avoid
        //checking the connection every couple of seconds
        long DB_CONNECTION_TIMEOUT_SECS = 1500;

        public DbPriceTable() {
            initLatestPrices();
            initDb();
        }

        public void close() throws IOException {
            try {
                m_dbConn.close();
                m_getHpStmt.close();
                m_getTickStmt.close();
            } catch (SQLException e) {
                LOG.error("DbPriceTable not closed properly.", e);
            }
        }

        private void initLatestPrices() {
            //ConcurrentHashMap doesn't allow storage of null
            m_latestPrices = new HashMap<Security, TickPrice>();

            //majors...
            m_latestPrices.put(new Security("EUR/USD"), null);
            m_latestPrices.put(new Security("AUD/USD"), null);
            m_latestPrices.put(new Security("EUR/CHF"), null);
            m_latestPrices.put(new Security("EUR/JPY"), null);
            m_latestPrices.put(new Security("GBP/USD"), null);
            m_latestPrices.put(new Security("GBP/JPY"), null);
            m_latestPrices.put(new Security("NZD/USD"), null);
            m_latestPrices.put(new Security("USD/CAD"), null);
            m_latestPrices.put(new Security("USD/CHF"), null);
            m_latestPrices.put(new Security("USD/JPY"), null);

            //exotics
            m_latestPrices.put(new Security("AUD/JPY"), null);
            m_latestPrices.put(new Security("AUD/NZD"), null);
            m_latestPrices.put(new Security("CAD/JPY"), null);
            m_latestPrices.put(new Security("CHF/JPY"), null);
            m_latestPrices.put(new Security("EUR/AUD"), null);
            m_latestPrices.put(new Security("EUR/CAD"), null);
            m_latestPrices.put(new Security("EUR/CZK"), null);
            m_latestPrices.put(new Security("EUR/DKK"), null);
            m_latestPrices.put(new Security("EUR/GBP"), null);
            m_latestPrices.put(new Security("EUR/HUF"), null);
            m_latestPrices.put(new Security("EUR/NOK"), null);
            m_latestPrices.put(new Security("EUR/PLN"), null);
            m_latestPrices.put(new Security("EUR/SEK"), null);
            m_latestPrices.put(new Security("EUR/TRY"), null);
            m_latestPrices.put(new Security("GBP/CHF"), null);
            m_latestPrices.put(new Security("USD/CNY"), null);
            m_latestPrices.put(new Security("USD/DKK"), null);
            m_latestPrices.put(new Security("USD/HKD"), null);
            m_latestPrices.put(new Security("USD/INR"), null);
            m_latestPrices.put(new Security("USD/MXN"), null);
            m_latestPrices.put(new Security("USD/NOK"), null);
            m_latestPrices.put(new Security("USD/PLN"), null);
            m_latestPrices.put(new Security("USD/SAR"), null);
            m_latestPrices.put(new Security("USD/SGD"), null);
            m_latestPrices.put(new Security("USD/THB"), null);
            m_latestPrices.put(new Security("USD/TRY"), null);
            m_latestPrices.put(new Security("USD/TWD"), null);
            m_latestPrices.put(new Security("USD/ZAR"), null);

            //commodities
            m_latestPrices.put(new Security("XAG/USD"), null);
            m_latestPrices.put(new Security("XAU/USD"), null);

        }

        private void initDb() {
            LOG.info("Initialising DB...");
            try {
                Class.forName("com.mysql.jdbc.Driver");
                m_dbConn = DriverManager.getConnection(m_dbAcc, m_userName, m_pwd);
            } catch (ClassNotFoundException e) {
                LOG.error("Cannot find com.mysql.jdbc.Driver", e);
            }
            catch (SQLException e) {
                LOG.error("SQLException on connect", e);
            }

            if (m_dbConn == null) {
                LOG.error("Could not create DB connection. Therefore cannot create statements");
                return;
            }

            m_lastDbAccessTime = System.currentTimeMillis();

            try {
                m_getTickStmt = m_dbConn.prepareStatement(
                        "SELECT TickTimeStamp, Bid, Ask " +
                        "FROM t_data_Tick " +
                        "WHERE Pair = ? " +
                        "ORDER BY TickTimeStamp desc " +
                "LIMIT 1");
            } catch (SQLException e) {
                LOG.error("Could not create INSERT statement", e);
                m_lastDbAccessTime = -1;
            }

            try {
                //will need to reorder these... or maybe nest the SELECT?
                m_getHpStmt = m_dbConn.prepareStatement(
                        "SELECT HPTimeStamp, OpenBid, OpenAsk, HighBid, HighAsk, " +
                        "LowBid, LowAsk, CloseBid, CloseAsk, MaxSpread, AverageSpread, NumTicks " +
                        "FROM t_data_HistoryPoint " +
                        "WHERE Pair = ? and Granularity_ID = ? " +
                        "ORDER BY HPTimeStamp desc " +
                "LIMIT ?");
            } catch (SQLException e) {
                LOG.error("Could not create INSERT statement", e);
                m_lastDbAccessTime = -1;

            }

        }

        //synchronize this method to make sure not trying to get two connections
        private synchronized boolean checkDb() {
            if (m_dbConn == null) {
                LOG.debug("Call to checkDb prior to initDb!");
                return false;
            }

            //this may be wasteful, constantly pinging the database, so avoid thus:
            long timeSinceLastValidAccess = System.currentTimeMillis() - m_lastDbAccessTime;
            if (m_lastDbAccessTime > 0 && timeSinceLastValidAccess < (DB_CONNECTION_TIMEOUT_SECS * 1000))
                return true;

            LOG.info(String.format("Has been %1$d millisecs since last access... checking connection formally", 
                    timeSinceLastValidAccess));
            boolean closed = false;
            try {
                closed = m_dbConn.isClosed();
            } catch (SQLException e) {
                LOG.info("Exception created on m_dbConn.isClosed(): " + e.toString());
                LOG.info("Will assume the connection is closed");
                closed = true;
            }

            if (closed) {
                LOG.info("Db connection is closed. Calling initDb to correct");
                initDb();
                closed = false;
            }

            LOG.info("Assuming connection is fine, but not confirmed");

            //would like to reinterrogate isClosed(), but that requires more exception handling
            return !closed;
        }

        public synchronized HistoryPoint getLastHistoryPoint(Security sec, Interval ival) {
            HistoryPointTimeSeries ts = getPriceHistory(sec, ival, 1);

            if (ts.size() < 1) {
                LOG.error("Call to getPriceHistory returned < 1 values... fails");
                return null;
            }
            else if (ts.size() > 1) {
                LOG.debug("Call to getPriceHistory returned > 1 values... last returned");
            }

            HistoryPoint retVal = ts.lastEntry().getValue();
            LOG.debug(String.format("History Point for %1$s is %2$s", sec, retVal));

            return retVal;
        }

        public synchronized HistoryPointTimeSeries getPriceHistory(Security sec, Interval ival, int nTicks) {
            if (m_getHpStmt == null) {
                LOG.debug("m_getHpStmt was null!");
                return null;
            }

            HistoryPointTimeSeries retVal = new HistoryPointTimeSeries(sec, ival);

            if (nTicks <= 0)
                return retVal;

            Currency quoteCcy = sec.parseFxCcy();
            int granId = ival.getDbPrimaryKey();

            checkDb();

            try {
                m_getHpStmt.setString(1, sec.getTicker());
                m_getHpStmt.setInt(2, granId);
                m_getHpStmt.setInt(3, nTicks);
                ResultSet rs = m_getHpStmt.executeQuery();
                m_lastDbAccessTime = System.currentTimeMillis();

                while (rs.next()) {
                    HistoryPoint hp = new HistoryPoint(
                            rs.getTimestamp(1).getTime(),
                            quoteCcy,
                            rs.getDouble(2),
                            rs.getDouble(3),
                            rs.getDouble(4),
                            rs.getDouble(5),
                            rs.getDouble(6),
                            rs.getDouble(7),
                            rs.getDouble(8),
                            rs.getDouble(9),
                            ival);

                    hp.setMaxSpread(rs.getDouble(10));
                    hp.setAverageSpread(rs.getDouble(11));
                    hp.setNumTicks(rs.getInt(12));

                    retVal.add(hp);
                }
            } catch (SQLException e) {
                LOG.error("Could not execute DB query", e);
                m_lastDbAccessTime = -1;
            }

            return retVal;
        }

        public synchronized TickPrice getPrice(Security sec) {
            //if it's in the local cache, then return that
            TickPrice tp = null;

            //this will be true if 1) the security isn't there or
            //2) we don't yet have a price for it
            if ((tp = m_latestPrices.get(sec)) == null) {
                tp = getPriceFromDb(sec);

                //don't add the requested security unless we
                //have some record of it in the db (otherwise
                //we could be adding some erroneous pair and
                //will thereafter return true to hasPrice(sec)
                if (tp != null)
                    m_latestPrices.put(sec, tp);
            }

            LOG.debug(String.format("Price for %1$s is %2$s", sec, tp));

            return tp;
        }

        private TickPrice getPriceFromDb(Security sec) {
            if (m_getTickStmt == null) {
                LOG.debug("m_getTickStmt was null!");
                return null;
            }

            Currency quote = sec.parseFxCcy();

            checkDb();

            TickPrice tp = null;
            try {
                m_getTickStmt.setString(1, sec.getTicker());
                ResultSet rs = m_getTickStmt.executeQuery();
                m_lastDbAccessTime = System.currentTimeMillis();

                while (rs.next()) {
                    long dt = rs.getTimestamp(1).getTime();
                    double bid = rs.getDouble(2);
                    double ask = rs.getDouble(3);

                    tp = new TickPrice(dt, bid, ask, quote);
                }
            } catch (SQLException e) {
                LOG.error("Could not execute DB query", e);
                m_lastDbAccessTime = -1;
            }

            return tp;
        }

        private void setPrice(SecurityPrice sp) {
            m_latestPrices.put(sp.getSecurity(), sp.getTickPrice());	    
        }

        public boolean hasPrice(Security sec) {
            return m_latestPrices.containsKey(sec);
        }

    }


}
