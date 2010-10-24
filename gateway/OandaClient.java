package c8.gateway;

import c8.util.*;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

import javax.xml.parsers.FactoryConfigurationError;

import org.apache.commons.cli.*;
import org.apache.log4j.*;
import org.apache.log4j.xml.DOMConfigurator;

import com.oanda.fxtrade.api.*;



public class OandaClient implements Runnable, MessageSource, MessageSink {

    private static void testOrder() {
        FXClient fxclient  = new FXGame();

        String username = "meskdale";
        String password = "Alliecat8";

        while (true) {
            try {
                fxclient.setTimeout(10);
                fxclient.setWithRateThread(true);
                fxclient.login(username, password);
                break;
            }
            catch (OAException oe) {
                System.out.println("Example: caught: " + oe);
            }
            return;
        }
        User me = null;
        try {
            me = fxclient.getUser();
        } catch (SessionException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }
        System.out.println("name=" + me.getName());
        System.out.println("email=" + me.getEmail());


        Vector accounts = me.getAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            System.out.println("accountid=" + accounts.elementAt(i));
        }

        Account myaccount = (Account)accounts.firstElement();
        System.out.println("Account ID: " + myaccount.getAccountId());
        System.out.println("MarginRate: " + myaccount.getMarginRate());

        //PLACE A MARKET ORDER
        MarketOrder neworder = new MarketOrder();
        neworder.setUnits(100000);
        neworder.setPair(new FXPair("GBP/CHF"));
        neworder.setStopLoss(new StopLossOrder(0.1029));
        try {
            myaccount.execute(neworder);
        }
        catch (OAException oae) {
            System.out.println("Example: caught: " + oae);
        }
    }


    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("Usage: java c8.Trader [options] xmlConfigFormatFile");
            return;
        }

        PrintStream stdout = null;
        PrintStream stderr = null;
        boolean isGameOnly = true;
        boolean isDaemon = false;
        String fileSourceName = null;
        String fileSinkName = null;

        Options opt = new Options();

        opt.addOption("o", true, "Set STDOUT file ref");
        opt.addOption("e", true, "Set STDERR file ref");
        opt.addOption("d", false, "Run as a daemon");
        opt.addOption("x", true, "Set ExecEnviron");
        opt.addOption("l", true, "Set Log4J XML config file ref");
        opt.addOption("s", true, "Set message file source");
        opt.addOption("k", true, "Set message file sink");

        BasicParser parser = new BasicParser();
        CommandLine cl;
        try {
            cl = parser.parse(opt, args);
        } catch (ParseException e1) {
            System.err.println("Cannot parse command line");
            return;
        }

        if (cl.hasOption('o')) {
            try {
                FileOutputStream stdoutstream = new FileOutputStream(cl.getOptionValue('o'));
                stdout = new PrintStream(stdoutstream);
            } catch (FileNotFoundException e) {
                System.err.println("Cannot open/access specified stdout file: "
                        + cl.getOptionValue('o'));
                return;
            }
        }

        if (cl.hasOption('e')) {
            try {
                FileOutputStream stderrstream = new FileOutputStream(cl.getOptionValue('e'));
                stdout = new PrintStream(stderrstream);
            } catch (FileNotFoundException e) {
                System.err.println("Cannot open/access specified stderr file: "
                        + cl.getOptionValue('e'));
                return;
            }
        }

        if (cl.hasOption('l')) {
            try {
                DOMConfigurator.configure(cl.getOptionValue('l'));
            } catch (FactoryConfigurationError e) {
                System.err.println("Error while configuring Log4J: "
                        + e.toString());
                return;
            }
        } else {
            BasicConfigurator.configure();
        }

        if (cl.hasOption('x')) {
            if (cl.getOptionValue('x').equalsIgnoreCase("FXGame"))
                isGameOnly = true;
            else if (cl.getOptionValue('x').equalsIgnoreCase("FXTrade"))
                isGameOnly = false;
            else {                   
                System.err.println("Specified ExecEnviron is not recognised: "
                        + cl.getOptionValue('x'));
                return;
            }
        } else {
            System.err.println("No ExecEnviron was provided");
            return;
        }

        if (cl.hasOption('s'))
            fileSourceName = cl.getOptionValue('s');

        if (fileSourceName == null) {
            System.err.println("Message source file not specified. Stopping");
            return;
        }

        if (cl.hasOption('k')) 
            fileSinkName = cl.getOptionValue('k');

        if (fileSinkName == null) {
            System.err.println("Message sink file not specified. Stopping");
            return;
        }

        MessageSource source = null;
        MessageSink sink = null;
        try {
            if (fileSourceName.equals("db"))
                source = new DbMessageSource();
            else
                source = new FileSource(fileSourceName);

            if (fileSinkName.equals("db"))
                sink = new DbMessageSink();
            else
                sink = new FileSink(fileSinkName);

        } catch (IOException e1) {
            System.err.println("Invalid source or sink file... stopping");
            return;
        }

        isDaemon = cl.hasOption('d');
        if (isDaemon) {
            System.err.println("Daemon option not yet supported. Sorry.");
            return;
        }

        if (stdout != null)
            System.setOut(stdout);
        if (stderr != null)
            System.setErr(stderr);

        OandaClient client = new OandaClient(isGameOnly);
        client.setMessageSource(source);
        client.setMessageSink(sink);

        client.run();
    }

    private PriorityBlockingQueue<Message> m_unhandledMsgsFromServer;
    private PriorityBlockingQueue<Message> m_unhandledMsgsFromClient;

    private MessageSource m_msgSource;
    private MessageSink m_msgSink;

    private ConcurrentHashMap<Integer, OrderMessagePair> m_openOrders;
    private ConcurrentHashMap<Integer, OrderMessagePair> m_execMsgs;
    private ConcurrentHashMap<Integer, OrderMessagePair> m_modifyMsgs;
    private ConcurrentHashMap<Integer, OrderMessagePair> m_closeMsgs;

    //keep one list keyed by MessageID
    private ConcurrentHashMap<Integer, OrderMessagePair> m_allMsgsByID;

    private DbPriceStore m_latestPrices;
    private SortedSet<String> m_priceSubs;

    private class OrderMessagePair {
        Message m_msg = null;
        MarketOrder m_oandaOrder = null;
        c8.trading.MarketOrder m_internalOrder = null;
        Transaction m_oandaTrans = null;

        public OrderMessagePair(Message m, MarketOrder oandaOrder, c8.trading.MarketOrder interalOrder) {
            m_msg = m;
            m_oandaOrder = oandaOrder;
            m_internalOrder = interalOrder;
        }
    }

    //  m_isLogged on is this class internal idea of whether it wants to be logged on,
    // mostly used to facilitate the update method (ie. this class needs to know
    // when it has been deliberately logged off, and hence shouldn't attempt
    // reconnection when it is advised of the log off)
    private boolean m_isLoggedOn;
    
    private boolean m_isGame;

    static final Logger LOG = LogManager.getLogger(OandaClient.class);

    private FXClient m_client;

    private boolean m_isDoingMessage;
    private long m_lastDoMessageStartTime = -1;

    private long m_lastForceRestartEndTime = -1;

    private long m_maxWaitSecondsBeforeDecideDisconnected = 60;

    private long m_messengerCycleSeconds = 5;

    private int m_maxMessageTries = 3;
    private int m_maxConnectionRetries = 1000;
    private int m_delayBetweenRetriesSeconds = 5;
    private int m_periodicPollSeconds = 30;

    private String m_username = "meskdale";
    private String m_pwd = "Alliecat8";
    private Timer m_messenger;
    private Thread m_worker;

    public OandaClient() {
        this(true);
    }

    public OandaClient(boolean isGameOnly) {
        m_isGame = isGameOnly;

        //all of these should be carried through reconnections...
        m_unhandledMsgsFromServer = new PriorityBlockingQueue<Message>();
        m_unhandledMsgsFromClient = new PriorityBlockingQueue<Message>();
        m_openOrders = new ConcurrentHashMap<Integer, OrderMessagePair>();
        m_latestPrices = new DbPriceStore();
        m_execMsgs = new ConcurrentHashMap<Integer, OrderMessagePair>();
        m_modifyMsgs = new ConcurrentHashMap<Integer, OrderMessagePair>();
        m_closeMsgs = new ConcurrentHashMap<Integer, OrderMessagePair>();
        m_allMsgsByID = new ConcurrentHashMap<Integer, OrderMessagePair>();

        m_priceSubs = new TreeSet<String>();
    }

    public void setMessageSource(MessageSource m) {
        m_msgSource = m;
    }

    public void setMessageSink(MessageSink m) {
        m_msgSink = m;
    }

    public boolean send(Message m) {
        LOG.debug(String.format("Message rec'd: %1$s", m));

        if (m_unhandledMsgsFromClient == null) {
            LOG.debug("Attempt to add message to closed queue" + m.toString());
            return false;
        }

        boolean added = m_unhandledMsgsFromClient.add(m);

        if (!added)
            LOG.error(String.format("Incoming message not added to queue: %1$s", m));

        return added;
    }

    public Message getNext() {
        Message next = m_unhandledMsgsFromServer.poll();

        if (next != null)
            LOG.debug(String.format("Message posted: %1$s", next));

        return next;
    }

    public void close() throws IOException {
        // do nothing?

    }

    public long getMessengerCycleSecs() {
        return m_messengerCycleSeconds;
    }

    public void setMessengerCycleSecs(long secs) {
        if (m_isLoggedOn || m_messenger != null) {
            //could probably change this, but would be ignored until next restart
            LOG.warn("Cannot set messenger cycle seconds after logon or when m_messenger already set");
            return;
        }

        m_messengerCycleSeconds = secs;
    }

    private void login() {
        login(m_username, m_pwd);
    }

    private void login(String username, String pwd) {

        m_username = username;
        m_pwd = pwd;

        try {
            initClient();
            m_client.login(username, pwd);

            //m_isLoggedOn is meant to represent whether the client wants to be logged on
            //m_isLoggedOn = isLoggedIn();
            //assert m_isLoggedOn;
            m_isLoggedOn = true;
            assert isLoggedIn();

            setAccountEvents();
            setRateEvents();

        } catch (com.oanda.fxtrade.api.InvalidUserException e) {
            LOG.error("Failed to login - invalid username", e);
        } catch (com.oanda.fxtrade.api.InvalidPasswordException e) {
            LOG.error("Failed to login - invalid password", e);
        } catch (com.oanda.fxtrade.api.SessionException e) {
            //System.err.println(e.toString());
            LOG.error("Failed to login - SessionException", e);
        }
    }

    private void initClient() {
        LOG.info("Initialising OandaGatewayClient as " + (m_isGame ? "FXGame" : "FXTrade"));
        if (m_isGame)
            m_client = new com.oanda.fxtrade.api.FXGame();
        else
            m_client = new com.oanda.fxtrade.api.FXTrade();

        LOG.info("Setting keep alive thread to true");
        m_client.setWithKeepAliveThread(true);
        LOG.info("Setting rate thread to true");
        m_client.setWithRateThread(true);
    }

    private void setAccountEvents() {
        LOG.info("Setting account events...");
        try {
            for (Object o : m_client.getUser().getAccounts()) {
                try {
                    Account acc = (Account)o;
                    FXEventManager evMgr = acc.getEventManager();
                    LOG.info("Setting FXAccountEvent for account: " + acc.getAccountName());
                    evMgr.add(new AccountEventHandler());
                } catch (AccountException e) {
                    LOG.error("Could not get FXEventManager for setEvents: " + e.toString());
                }

            }
        } catch (com.oanda.fxtrade.api.SessionException e) {
            LOG.error("Failed to get accounts for setEvents", e);
            //TODO: forcerestart???
        }
    }

    private class AccountEventHandler extends FXAccountEvent {
        public void handle(FXEventInfo EI, FXEventManager EM) {
            if (EI == null) {
                LOG.error("AccountEvent with null FXEventInfo!");
                return;
            }

            if (EI instanceof FXAccountEventInfo) {
                Transaction trans = ((FXAccountEventInfo)EI).getTransaction();
                if (trans == null) {
                    LOG.error("AccountEvent with null transaction!");
                    return;
                }

                try {
                    LOG.info("AccountEvent received: " + trans.toString());

                    OrderMessagePair linkedOrder = null;
                    if (trans.getCompletionCode() == Transaction.FX_USER ||
                            trans.getCompletionCode() == Transaction.FX_SL || 
                            trans.getCompletionCode() == Transaction.FX_TP) {
                        linkedOrder = getLinkedOrderMsg(trans);
                        if (linkedOrder != null) {
                            linkedOrder.m_oandaTrans = trans;

                            //it's the same OMP object so this should already be done...
                            //			OrderMessagePair msgByID = m_allMsgsByID.get(Integer.valueOf(linkedOrder.m_msg.getId()));
                            //			if (msgByID != null)
                            //			    msgByID.m_oandaTrans = trans;
                        }
                    }

                    Message.Action action = Message.Action.Trans_Other;
                    if (trans.getCompletionCode() == Transaction.FX_USER) {
                        action = Message.Action.Trans_Ordered;
                    }
                    else if (trans.getCompletionCode() == Transaction.FX_SL) {
                        action = Message.Action.Trans_SL;
                    }
                    else if (trans.getCompletionCode() == Transaction.FX_TP) {
                        action = Message.Action.Trans_TP;
                    }
                    else if (trans.getCompletionCode() == Transaction.FX_INTEREST) {
                        LOG.warn("Interest paid... not added to balances");
                    }
                    else {
                        LOG.warn(String.format("ERROR? Unexpected completion code: %1$d", trans.getCompletionCode()));
                    }

                    int linkedMsgId = -1;
                    String linkedInternalOrderId = "na";
                    if (linkedOrder != null) {
                        linkedMsgId = linkedOrder.m_msg.getId();
                        linkedInternalOrderId = linkedOrder.m_internalOrder.getId();
                    }

                    //send the Message whether or not we have a linked message...
                    String trStr = Message.transactionToString(trans, linkedInternalOrderId);
                    Message msg = new Message(action, trStr, Message.Priority.Immediate, linkedMsgId);
                    //sendMessage(msg);
                    if (!m_unhandledMsgsFromServer.add(msg))
                        LOG.error("Message not added to unhandledMsgsFromServer: " + msg.toString());	

                    LOG.info("Account event handled");
                } catch (Exception e) {
                    LOG.error("Exception on account event: " + e.toString());
                    e.printStackTrace();
                }
            }
        }

        private OrderMessagePair getLinkedOrderMsg(Transaction trans) {
            //add a msg link back to that order so that the 
            //other side can identify the connection

            LOG.debug(String.format("OrderMessagePair size Exec: %1$d Mod: %2$d Close: %3$d", 
                    m_execMsgs.size(), m_modifyMsgs.size(), m_closeMsgs.size()));

            ConcurrentHashMap<Integer, OrderMessagePair> orderMsgs = null;
            int trNum = -1;
            if (trans.getCompletionCode() == Transaction.FX_USER) {
                if (trans.getType().equalsIgnoreCase("BuyMarket") ||
                        trans.getType().equalsIgnoreCase("SellMarket")) {
                    LOG.info("Buy/Sell (execute) Market transaction conf rec'd");
                    trNum = trans.getTransactionNumber();
                    orderMsgs = m_execMsgs;
                }
                else if (trans.getType().equalsIgnoreCase("ChangeTrade")) {
                    LOG.info("Change Trade (modify) Market transaction conf rec'd");
                    trNum = trans.getTransactionLink();
                    orderMsgs = m_modifyMsgs;
                }
                else if (trans.getType().equalsIgnoreCase("CloseTradeB") ||
                        trans.getType().equalsIgnoreCase("CloseTradeS")) {
                    LOG.info("Close Trade (close) Market transaction conf rec'd");
                    trNum = trans.getTransactionLink();
                    orderMsgs = m_closeMsgs;
                }
                else {
                    LOG.warn("Transaction has FX_USER completion code but has unexpected type: " + trans.getType());
                }
            }
            else if (trans.getCompletionCode() == Transaction.FX_SL || 
                    trans.getCompletionCode() == Transaction.FX_TP) {
                orderMsgs = m_execMsgs;
                trNum = trans.getTransactionLink();
            }
            else {
                LOG.warn("Inappropriate call to getLinkedMsgId. Wrong completion code");
                return null;
            }

            OrderMessagePair omp = null;
            if (orderMsgs != null && trNum != -1) {
                omp = orderMsgs.get(Integer.valueOf(trNum));
                if (omp == null)
                    LOG.error("Cannot find OpenOrder object for transaction: " + trans.toString());
                else
                    LOG.info("Linked message ID is " + Integer.toString(omp.m_msg.getId()));
            }
            else {
                LOG.error(String.format("orderMsgs was null (%1$s) and/or trNum was -1 (%2$d)", 
                        (orderMsgs == null ? "yes" : "no"), trNum));
            }

            return omp;
        }
    }

    private void setRateEvents() {
        LOG.info("Setting rate events...");
        try {
            RateTable rt = m_client.getRateTable();
            FXEventManager evMgr = rt.getEventManager();
            //catch all rate events...
            evMgr.add(new RateEventHandler());
        } catch (SessionDisconnectedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private class RateEventHandler extends FXRateEvent {
        public void handle(FXEventInfo EI, FXEventManager EM) {
            //do NOT want this thread to crash...
            try {
                if (EI == null)
                    return;

                FXRateEventInfo fxr = (FXRateEventInfo)EI;
                if (fxr == null)
                    return;

                m_latestPrices.setPrice(
                        new Security(fxr.getPair().toString()), 
                        new TickPrice(fxr.getTick().getTimestamp() * 1000, 
                                fxr.getTick().getBid(), fxr.getTick().getAsk(), Currency.getInstance(fxr.getPair().getQuote())));

                if (m_priceSubs.contains(fxr.getPair().getPair())) {
                    LOG.info(String.format("Received rate event (%1$s): %2$TF %2$TT %3$6.5f %3$6.5f", 
                            fxr.getPair(), fxr.getTick().getTimestamp() * 1000, fxr.getTick().getBid(), fxr.getTick().getAsk()));

                    String reStr = String.format("%1$TF %1$TT\t%2$s\t%3$6.5f\t%4$6.5f", fxr.getTimestamp() * 1000, 
                            fxr.getPair(), fxr.getTick().getBid(), fxr.getTick().getAsk());
                    Message msg = new Message(Message.Action.PriceUpdate, reStr);

                    m_unhandledMsgsFromServer.add(msg);
                    //sendMessage(msg);
                }
            } catch (Exception e) {
                LOG.error("Exception on rate event: " + e.toString());
                e.printStackTrace();
            }
        }

        public boolean match(FXEventInfo EI) {
            return true;
        }
    }

    private class DbPriceStore implements java.io.Closeable {

        //depending on speed may cache things here... but for now let's just
        //query the db for everything

        Connection m_dbConn;
        PreparedStatement m_getTickStmt;
        PreparedStatement m_insertTickStmt;
        PreparedStatement m_insertHpStmt;

        String m_dbAcc = "jdbc:mysql://localhost:3306/fx_data";
        String m_userName = "mark";
        String m_pwd = "alliecat8";

        BarMaker m_fiveMinBarMaker;

        HashMap<Security, TickPrice> m_latestPrices;

        long m_lastDbAccessTime = -1;

        //i think that this is actually 1800 seconds, but mostly just want to avoid
        //checking the connection every couple of seconds
        long DB_CONNECTION_TIMEOUT_SECS = 1500;

        public DbPriceStore() {
            initLatestPrices();
            initDb();
            initBarMaker();
        }

        public void close() throws IOException {
            try {
                m_dbConn.close();
                m_getTickStmt.close();
                m_insertHpStmt.close();
                m_insertTickStmt.close();
            } catch (SQLException e) {
                LOG.error("DbPriceTable not closed properly.", e);
            }
        }

        private void initLatestPrices() {
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
                m_insertTickStmt = m_dbConn.prepareStatement(
                        "INSERT INTO t_data_Tick (TickTimeStamp, Pair, Bid, Ask) " +
                "VALUES (?, ?, ?, ?)");
            } catch (SQLException e) {
                LOG.error("Could not create INSERT statement", e);
                m_lastDbAccessTime = -1;
            }

            try {
                m_insertHpStmt = m_dbConn.prepareStatement(
                        "INSERT INTO t_data_HistoryPoint (Granularity_ID, HPTimeStamp, Pair, OpenBid, OpenAsk, HighBid, HighAsk, " +
                        "LowBid, LowAsk, CloseBid, CloseAsk, MaxSpread, AverageSpread, NumTicks) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            } catch (SQLException e) {
                LOG.error("Could not create INSERT statement", e);
                m_lastDbAccessTime = -1;
            }
        }

        private void initBarMaker() {
            LOG.info("Initialising BarMaker...");

            m_fiveMinBarMaker = new BarMaker(new BarMaker.Sink() {
                public void close() {
                    try {
                        m_insertHpStmt.close();
                    } catch (SQLException e) {
                        LOG.error("Could not close m_insertHpStmt", e);
                    }
                }

                public void save(Security sec, HistoryPoint hp) {
                    try {
                        if (m_insertHpStmt == null) {
                            LOG.debug("m_insertHpStmt was null!");
                            return;
                        }

                        checkDb();

                        m_insertHpStmt.setInt(1, Interval.EVERY_FIVE_MINS.getDbPrimaryKey());
                        m_insertHpStmt.setTimestamp(2, new Timestamp(hp.getTimeStamp()));
                        m_insertHpStmt.setString(3, sec.getTicker());
                        m_insertHpStmt.setDouble(4, hp.getOpen().getBid());
                        m_insertHpStmt.setDouble(5, hp.getOpen().getAsk());
                        m_insertHpStmt.setDouble(6, hp.getHigh().getBid());
                        m_insertHpStmt.setDouble(7, hp.getHigh().getAsk());
                        m_insertHpStmt.setDouble(8, hp.getLow().getBid());
                        m_insertHpStmt.setDouble(9, hp.getLow().getAsk());
                        m_insertHpStmt.setDouble(10, hp.getClose().getBid());
                        m_insertHpStmt.setDouble(11, hp.getClose().getAsk());
                        m_insertHpStmt.setDouble(12, hp.getMaxSpread());
                        m_insertHpStmt.setDouble(13, hp.getAverageSpread());

                        //disallow negative ticks
                        m_insertHpStmt.setInt(14, (hp.getNumTicks() < 0 ? 0 : hp.getNumTicks()));

                        if (m_insertHpStmt.executeUpdate() != 1)
                            LOG.error(String.format("Insert HP failed: %1$s %2%s", sec.getTicker(), hp));
                        else
                            m_lastDbAccessTime = System.currentTimeMillis();
                    } catch (SQLException e) {
                        LOG.error(String.format("Insert HP failed: %1$s %2%s", sec.getTicker(), hp), e);
                        m_lastDbAccessTime = -1;
                    }
                }
            }, Interval.EVERY_FIVE_MINS);

            LOG.info("->Five min intervals set");

            LOG.info("->Will carry over missing bars");
            m_fiveMinBarMaker.setCarryOverMissingBars(true);

            LOG.info("->Will start on round time");
            m_fiveMinBarMaker.setStartOnRoundTime(true);

            LOG.info("->Will infill missing bars with no limit (weekends etc)");
            m_fiveMinBarMaker.setMaximumInfillBars(-1);
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

        private void setPrice(Security sec, TickPrice p) {
            m_latestPrices.put(sec, p);

            if (m_insertTickStmt == null) {
                LOG.debug("m_insertTickStmt was null!");
                return;
            }

            checkDb();

            try {
                //this needs to be in milliseconds... is it?
                m_insertTickStmt.setTimestamp(1, new Timestamp(p.getTimeStamp()));
                m_insertTickStmt.setString(2, sec.getTicker());
                m_insertTickStmt.setDouble(3, p.getBid());
                m_insertTickStmt.setDouble(4, p.getAsk());

                if (m_insertTickStmt.executeUpdate() != 1)
                    LOG.error(String.format("Insert tick failed: %1$s, %2$s", sec, p));
                else
                    m_lastDbAccessTime = System.currentTimeMillis();
            } catch (SQLException e) {
                LOG.error(String.format("Insert tick failed: %1$s, %2$s", sec, p), e);
                m_lastDbAccessTime = -1;
            }

            m_fiveMinBarMaker.tick(new SecurityPrice(sec, p));

        }

        public boolean hasPrice(Security sec) {
            return m_latestPrices.containsKey(sec);
        }

    }

    private boolean isLoggedIn() {
        LOG.debug("isLoggedIn() method called...");

        long now = System.currentTimeMillis();
        if (isDoMessageTimeout()) {
            LOG.debug("isDoMessageTimeout() returns true");
            LOG.info(String.format("Last message was sent to Oanda %1$d seconds ago... may not be logged in", 
                    (now - m_lastDoMessageStartTime) / 1000));

            //first check if there was a restart... restarting can sometimes take several attempts, during
            //which time messages are not being processed and false negatives can be produced
            if (!isRecentForcedRestart()) {
                LOG.debug("isRecentForcedRestart returns false");
                return false;
            }
        }

        //NB. this returns whether the client is ACTUALLY logged in, not whether it
        //wants to be
        if (m_client == null) {
            LOG.debug("m_client is null. Returning false");
            return false;
        }

        boolean isLI = m_client.isLoggedIn();

        LOG.debug("m_client.isLoggedIn() returns: " + (isLI ? "True" : "False"));

        return isLI;
    }

    private boolean isDoMessageTimeout() {
        return (m_lastDoMessageStartTime != -1 && 
                m_isDoingMessage &&
                System.currentTimeMillis() - m_lastDoMessageStartTime > m_maxWaitSecondsBeforeDecideDisconnected * 1000);
    }

    private boolean isRecentForcedRestart() {
        //returns true if there has been a forced restart in the last 10 seconds

        return (m_lastForceRestartEndTime != -1 && 
                System.currentTimeMillis() - m_lastForceRestartEndTime < 10000);
    }

    public void logout() {
        //set loggedOn to false before join-ing
        LOG.info("Logging out...");

        m_isLoggedOn = false;
        LOG.info("Stopping messenger...");
        m_messenger.cancel();

        try {
            LOG.info("Closing message sink..");
            m_msgSink.close();
        } catch (IOException e) {
            LOG.error("Could not close message sink", e);
        }

        try {
            LOG.info("Closing message source...");
            m_msgSource.close();
        } catch (IOException e) {
            LOG.error("Could not close message source", e);
        }

        LOG.info("Logging out of OANDA client...");
        m_client.logout();

        if (m_client.isLoggedIn())
            LOG.debug("logout called, but m_client.isLoggedIn() returns true");

        LOG.info("Logged out.");
    }

    private void forceRestart() {
        //probably should only be run from the same thread that is executing messages
        //(ie. the doMessage loop thread)

        LOG.info("Force restart method called");

        if (m_messenger != null) {
            LOG.info("Cancelling messenger thread");
            m_messenger.cancel();
            LOG.info("Messenger thread cancelled");
        }
        
        if (m_worker != null) {
            LOG.info("Setting worker thread to null (which will stop it, either soon or when it is reassigned)");
            m_worker = null;
        }

        //	this prevents isDoMessageTimeout motivated restarts from looping endlessly
        m_lastDoMessageStartTime = -1;
        LOG.info("lastDoMessageStartTime reset to -1");

        //reconnect
        int nRetries = 0;
        while (nRetries++ < m_maxConnectionRetries) {
            try {
                LOG.info(String.format("Attempted reconnection to OANDA number %1$d ", nRetries));
                login();
                if (this.isLoggedIn()) {
                    LOG.info("Successfully reconnected to OANDA.");
                    break;
                }
            }
            catch (Exception e) {
                LOG.error("Login failed with unrecognised exception. Retrying.", e);
            }

            LOG.info(String.format("Attempt %1$d complete, but unsuccessful. Sleeping for %2$d secs...", 
                    nRetries, m_delayBetweenRetriesSeconds));

            try {
                Thread.sleep(m_delayBetweenRetriesSeconds * 1000);
            } catch (InterruptedException e) {
                LOG.error("Sleep between forceRestart retries interrupted", e);
            }
        }

        m_lastForceRestartEndTime = System.currentTimeMillis();

        LOG.info("Force restart done. isLoggedIn() returns: " + this.isLoggedIn());
    }

    public void run() {
        while (true) {
            try {
                LOG.info("Commencing execution...");
                //this might come after forcing a restart, so check
                //if logged in before doing it...
                while (!isLoggedIn()) {
                    LOG.info("isLoggedIn() returns false. Logging in...");
                    this.login();
                }

                startMessenger();
                startWorker();
                pollPeriodically();
                
            } catch (RuntimeException e) {
                LOG.error("client.run() threw an exception", e);
            } finally {
                //		flush stdout and stderr, given they might be going to files
                System.out.flush();
                System.err.flush();
            }

            if (m_isLoggedOn) {
                LOG.info("Restarting after system error...");
                forceRestart();
            }
            else {
                LOG.info("Client has logged off");
                break;
            }
        }

        LOG.info("Finishing OANDA session.");
    }
    
    private void startMessenger() {
        //process messages on a timer
        //received messages are taken from the msg source and put on the unhandled message from client list
        //outgoing messages are sent to the msg sink... if they fail the failure
        //count is incremented, and conditionally they are put back on the unhandled message
        //from server list
        if (m_msgSink == null || m_msgSource == null) {
            LOG.error(String.format("MessageSink (%1$s) or MessageSource (%2$s) is null. Messenger not started", 
                    m_msgSink, m_msgSource));
            return;
        }
        
        m_messenger = new Timer("OC_messenger");
        m_messenger.scheduleAtFixedRate(new TimerTask() {

            public void run() {
                Message next;
                try {
                    LOG.debug("Checking for messages from trading client");
                    while ((next = m_msgSource.getNext()) != null) {
                        LOG.debug("Received client message: " + next.toString());
                        m_unhandledMsgsFromClient.put(next);
                    }

                    LOG.debug(String.format("There are %1$d message(s) from OANDA", m_unhandledMsgsFromServer.size()));
                    while ((next = m_unhandledMsgsFromServer.poll()) != null) {
                        next.incrementTries();
                        if (!m_msgSink.send(next)) {
                            LOG.error("Message sink refused to handle message: " + next.toString());
                            if (next.getTries() < m_maxMessageTries) {
                                LOG.debug("getTries is less than max... retrying");
                                LOG.info("Putting last msg back on unhandled msg list: " + next.toString());
                                m_unhandledMsgsFromServer.put(next);
                            }
                            else {
                                LOG.error("Given up on message because reached max tries: " + next.toString());
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    LOG.error("Exception on messenger timer", e);
                }

            }
        }, 0, m_messengerCycleSeconds * 1000);


    }
  
    private void startWorker() {
        
        m_worker = new Thread(new Runnable() {
            public void run() {
                Thread thisThread = Thread.currentThread();
                Message lastMsg = null;
                try {
                    //check if the thread has been cancelled
                    while (m_worker != null && m_worker == thisThread) {
                        try {
                            LOG.debug(String.format("Waiting for next message. There are %1$d in the queue", 
                                    m_unhandledMsgsFromClient.size()));

                            //processes msgs from client, blocking until the next message is rec'd
                            lastMsg = m_unhandledMsgsFromClient.take();
                            if (lastMsg != null) {
                                LOG.info(String.format("Message received: %1$d %2$s - %3$s", 
                                        lastMsg.getId(), lastMsg.getAction(), 
                                        (lastMsg.getData() == null ? "null" : lastMsg.getData())));

                                if (!isLoggedIn()) {
                                    LOG.error("OandaClient disconnected! isLoggedIn() returns false");
                                    throw new RuntimeException();
                                }

                                //trust that won't become disconnected in the time since checked above...
                                if (!doMessage(lastMsg)) {
                                    //if it couldn't be handled, put it back on the queue
                                    //until the number of attempts exceeds the maximum
                                    LOG.error("doMessage failed: " + lastMsg.toString());
                                    if (lastMsg.getTries() < m_maxMessageTries) {
                                        LOG.debug("getTries is less than max... retrying");
                                        LOG.info("Putting last msg back on unhandled msg list: " + lastMsg.toString());
                                        m_unhandledMsgsFromClient.put(lastMsg);
                                    }
                                    else {
                                        LOG.error("Cannot do message: " + lastMsg.toString());

                                        //send a failure notification
                                        Message msg = new Message(Message.Action.FailureNotification, lastMsg.toString(), 
                                                Message.Priority.Immediate, lastMsg.getId());
                                        if (!m_unhandledMsgsFromServer.add(msg))
                                            LOG.error("Message not added to unhandledMsgsFromServer: " + msg.toString());
                                    }
                                }
                            }
                            else {
                                LOG.debug("Null message received... trying again");
                            }
                        } catch (InterruptedException e) {
                            LOG.error("OandaClient interrupted.", e);
                            if (lastMsg != null && !m_unhandledMsgsFromClient.contains(lastMsg)) {
                                LOG.info("Message added back to list for processing later: " + lastMsg.toString(), e);
                                m_unhandledMsgsFromClient.put(lastMsg);
                            }
                            else if (lastMsg != null) {
                                LOG.info("lastMsg already on the list: " + lastMsg.toString());
                            }
                            else {
                                LOG.info("lastMsg null... presumably InterruptedException occurred before removing something from list");
                            }
                        }
                        //put any other recoverable exceptions in here...
                        
                    }
                } catch (RuntimeException e) {
                    LOG.error("Exception on worker: ", e);

                    //      must rethrow to motivate relogin etc
                    throw e;
                }
                finally {
                    LOG.info("Oanda client finishing. m_isLoggedOn is: " + (m_isLoggedOn ? "True" : "False"));

                    if (lastMsg != null && !m_unhandledMsgsFromClient.contains(lastMsg)) {
                        LOG.info("Putting last msg back on unhandled msg list: " + lastMsg.toString());
                        m_unhandledMsgsFromClient.put(lastMsg);
                    }

                    for (Message unhandled : m_unhandledMsgsFromClient)
                        LOG.info("Unhandled message (for now): " + unhandled.toString());
                }
            }
        }, "OC_worker");
        
        m_worker.start();
        
    }

    private void pollPeriodically() {
        //just check the worker thread, because the messenger thread is fairly harmless
        //TODO: check on the FXEventThread?  will probably respond to doMessageTimeout too though
        while (!isDoMessageTimeout()) {
            try {
                LOG.debug("isDoMessageTimeout returns false.  Sleeping...");
                Thread.sleep(m_periodicPollSeconds * 1000);
            } catch (InterruptedException e) {
                LOG.error("Periodic poll interrupted", e);
            }
        }
            
        LOG.error("isDoMessageTimeout returned true.  Throwing RuntimeException from pollPeriodically");
                throw new RuntimeException();
    }
    
    //returns true if the message is handled
    private boolean doMessage(Message m) {
 
        if (m == null) {
            LOG.debug("Null message sent to doMessage()");
            //the msg is handled... don't want it back
            return true;
        }

        m_isDoingMessage = true;
        m_lastDoMessageStartTime = System.currentTimeMillis();

        OrderMessagePair prevDone = m_allMsgsByID.get(Integer.valueOf(m.getId()));
        if (prevDone != null) {
            LOG.info("Message appears to be have sent again: " + m);
            if (prevDone.m_oandaTrans != null) {
                LOG.info("...And has an existing transaction: " + prevDone.m_oandaTrans);
                int linkedMsgId = -1; 
                if (prevDone.m_msg != null)
                    linkedMsgId = prevDone.m_msg.getId();
                else
                    LOG.error("...but no linked message!");
                String linkedInternalOrderId = "na";
                if (prevDone.m_internalOrder != null)
                    linkedInternalOrderId = prevDone.m_internalOrder.getId();
                else
                    LOG.error("...but no internal order!");

                String trStr = Message.transactionToString(prevDone.m_oandaTrans, linkedInternalOrderId);
                Message msg = new Message(Message.Action.Trans_Ordered, trStr, Message.Priority.Immediate, linkedMsgId);
                m_unhandledMsgsFromServer.add(msg);	

                m_isDoingMessage = false;
                return true;
            }
            else {
                //BUG: could end up here if the FXEventThread has died... and the transaction
                //has probably been done
                LOG.info("...But no transaction so will send again");
            }
        }

        m.incrementTries();

        LOG.debug(String.format("Attempt %1$d to execute message %2$s", m.getTries(), m));

        switch (m.getAction()) {
        case OpenOrder:
            String[] parts = m.getData().split("\t");
            int accId = Integer.parseInt(parts[0]);
            c8.trading.MarketOrder internalOrder = Message.parseMktOrder(m.getData());

            MarketOrder oandaOrder = translateOrder(internalOrder);

            if (!execute(accId, oandaOrder)) {
                m_isDoingMessage = false;
                return false;
            }

            //found one case where the order seemed to return a 0 trans number, and the system
            //collapsed... protect against this and provide some debugging info
            if (oandaOrder.getTransactionNumber() == 0) {
                LOG.error(String.format("Order execution returned true, but no trans number! Order: %1$s Msg: %2$s", 
                        oandaOrder, m));

                //todo: problem with this is that the order has been executed... what to do?
                //based on one data point, if there isn't a transaction number, the order hasn't
                //been executed... so probably don't need to worry
                m_isDoingMessage = false;
                return false;
            }

            LOG.info("Successfully ordered: " + oandaOrder.toString());

            OrderMessagePair omp = new OrderMessagePair(m, oandaOrder, internalOrder);
            m_openOrders.put(Integer.valueOf(oandaOrder.getTransactionNumber()), omp);

            //this is removed when the transaction confirmation is sent back... NOT ANYMORE
            LOG.debug(String.format("Adding trans number %1$d to m_execMsgs", oandaOrder.getTransactionNumber()));
            m_execMsgs.put(Integer.valueOf(oandaOrder.getTransactionNumber()), omp);

            m_allMsgsByID.put(Integer.valueOf(m.getId()), omp);

            break;
        case ModifyOrder:
            //required format is accId\torderNum
            parts = m.getData().split("\t");
            accId = Integer.parseInt(parts[0]);
            internalOrder = Message.parseMktOrder(m.getData());

            oandaOrder = retrieveOrder(Integer.parseInt(internalOrder.getOrderNumber()));
            if (oandaOrder == null) {
                LOG.error(String.format("Order retrieval for order %1$s (id %2$s) returned null. Not modified", 
                        internalOrder.getOrderNumber(), internalOrder.getId()));
                m_isDoingMessage = false;
                return false;
            }

            MarketOrder modified = translateOrder(internalOrder);

            oandaOrder.setStopLoss(modified.getStopLoss());
            oandaOrder.setTakeProfit(modified.getTakeProfit());

            if (!modify(accId, oandaOrder)) {
                m_isDoingMessage = false;
                return false;
            }

            LOG.info("Successfully modified: " + oandaOrder.toString());

            omp = new OrderMessagePair(m, oandaOrder, internalOrder);

            //	  this is removed when the transaction confirmation is sent back... NOT ANYMORE
            LOG.debug(String.format("Adding trans number %1$d to m_modifyMsgs", oandaOrder.getTransactionNumber()));
            m_modifyMsgs.put(Integer.valueOf(oandaOrder.getTransactionNumber()), omp);

            m_allMsgsByID.put(Integer.valueOf(m.getId()), omp);

            break;
        case CloseOrder:
            //required format is accId\torderNum
            parts = m.getData().split("\t");
            accId = Integer.parseInt(parts[0]);

            internalOrder = Message.parseMktOrder(m.getData());
            oandaOrder = retrieveOrder(Integer.parseInt(internalOrder.getOrderNumber()));

            if (oandaOrder == null) {
                LOG.error(String.format("Order retrieval for order %1$s (id %2$s) returned null. Not modified", 
                        internalOrder.getOrderNumber(), internalOrder.getId()));
                m_isDoingMessage = false;
                return false;
            }

            if (!close(accId, oandaOrder)) {
                m_isDoingMessage = false;
                return false;
            }

            LOG.info("Successfully closed: " + oandaOrder.toString());

            if (m_openOrders.remove(Integer.valueOf(oandaOrder.getTransactionNumber())) == null)
                LOG.error(String.format("Order number %1$d not removed from open orders list (%2$s", 
                        oandaOrder.getTransactionNumber(), oandaOrder));

            omp = new OrderMessagePair(m, oandaOrder, internalOrder);

            //	  this is removed when the transaction confirmation is sent back... NOT ANYMORE
            LOG.debug(String.format("Adding trans number %1$d to m_closeMsgs", oandaOrder.getTransactionNumber()));
            m_closeMsgs.put(Integer.valueOf(oandaOrder.getTransactionNumber()), omp);

            m_allMsgsByID.put(Integer.valueOf(m.getId()), omp);

            break;
        case CloseAllPositions:
            closeAllPositions(Integer.parseInt(m.getData()));
            break;
        case Logoff:
            logout();
            break;
        case PriceSubscriptionRequest:
            addPriceSub(m);
            break;
        case SendLastTransaction:
            //required data is accId
            Vector transactions = getTransactions(Integer.parseInt(m.getData()));

            if (transactions == null) {
                m_isDoingMessage = false;
                return false;
            }

            Transaction last = (Transaction)transactions.lastElement();
            if (last == null) {
                m_isDoingMessage = false;
                return false;
            }
                
            //make a message and send back
            String trStr = Message.transactionToString(last, "na");
            Message msg = new Message(Message.Action.SendLastTransaction_Reply, trStr, Message.Priority.Immediate, m.getId());
            m_unhandledMsgsFromServer.add(msg); 

            break;
        case PollIsLoggedOn:
            boolean isLI = this.isLoggedIn();
            m_unhandledMsgsFromServer.add(new Message(Message.Action.PollIsLoggedOn_Reply, Boolean.toString(isLI), Message.Priority.Immediate, m.getId()));
            break;
        case Trans_Ordered:
            LOG.error("Message.Action.Trans_Ordered should not be sent to client");
            break;
        case Trans_SL:
            LOG.error("Message.Action.Trans_SL should not be sent to client");
            break;
        case Trans_TP:
            LOG.error("Message.Action.Trans_TP should not be sent to client");
            break;
        case PriceUpdate:
            LOG.error("Message.Action.PriceUpdate should not be sent to client");
            break;
        
        default:
            m_isDoingMessage = false;
            throw new RuntimeException("Unknown Action:" + m.getAction().toString());

        }

        m_isDoingMessage = false;
        return true;
    }

    private void addPriceSub(Message m) {
        m_priceSubs.add(m.getData());
    }

    private MarketOrder translateOrder(c8.trading.MarketOrder internalOrder) {
        ////	pattern is AccountId\tOrderId\tSecurity\tUnits\t[StopLossPrice]\t[TakeProfitPrice]
        //	assert (parts.length >= 4);
        //
        //	FXPair pair = new FXPair(parts[2]);
        //	long units = Long.parseLong(parts[3]);
        //
        //	double sl = -1.0;
        //	if (parts.length > 4)
        //	    sl = Double.parseDouble(parts[4]);
        //	double tp = -1.0;
        //	if (parts.length > 5)
        //	    tp = Double.parseDouble(parts[5]);

        MarketOrder order = new MarketOrder();

        FXPair pair = new FXPair(internalOrder.getSecurity().getTicker());
        order.setPair(pair);
        order.setUnits(internalOrder.getUnits());

        //if there's no stop or take, then no need to check whether they are reasonable
        if (internalOrder.getStopLoss().equals(Price.Zero) && internalOrder.getTakeProfit().equals(Price.Zero))
            return order;

        if (!internalOrder.getStopLoss().getCcy().toString().equalsIgnoreCase(pair.getQuote())) {
            LOG.error(String.format("SL denominated in diff ccy (%1$s) to the pair quote (%2$s). SL ignored.", 
                    internalOrder.getStopLoss().getCcy(), pair.getQuote()));
            return order;
        }

        if (!internalOrder.getTakeProfit().getCcy().toString().equalsIgnoreCase(pair.getQuote())) {
            LOG.error(String.format("TP denominated in diff ccy (%1$s) to the pair quote (%2$s). TP ignored.", 
                    internalOrder.getTakeProfit().getCcy(), pair.getQuote()));
            return order;
        }

        TickPrice t = m_latestPrices.getPrice(new Security(order.getPair().toString()));
        FXTick lastQuote = (t == null ? null : new FXTick(t.getTimeStamp(), t.getBid(), t.getAsk()));
        if (lastQuote == null) {
            try {
                RateTable rt = m_client.getRateTable();
                lastQuote = rt.getRate(order.getPair());
            } catch (SessionDisconnectedException e) {
                LOG.error("Failed to get rate from OANDA... can't check SL/TP", e);
            } catch (RateTableException e) {
                LOG.error("Failed to get rate from OANDA... can't check SL/TP", e);
            }
        }

        //if it's a short, the SL should be higher than the current price
        //(OANDA chucks a wobbly if these conditions are not met)
        boolean badStop = false;
        double sl = internalOrder.getStopLoss().getAmount();
        double tp = internalOrder.getTakeProfit().getAmount();
        if (lastQuote != null) {
            //	    selling at the bid, reversal implies buying at the ask, so SL > ask
            if (order.getUnits() < 0 && sl <= lastQuote.getAsk()) {

                LOG.error(String.format("Cannot set SL to %1$6.5f for SHORT because ask for %2$s is %3$6.5f", 
                        sl, order.getPair(), lastQuote.getAsk()));
                badStop = true;
            }
            //	    buying at the ask, reversal implies selling at the bid, so SL < bid
            else if (order.getUnits() > 0 && sl >= lastQuote.getBid()) {
                LOG.error(String.format("Cannot set SL to %1$6.5f for LONG because bid for %2$s is %3$6.5f", 
                        sl, order.getPair(), lastQuote.getAsk()));
                badStop = true;
            }
        }
        else {
            //API chucks a silly exception if the SL/TP is incorrectly set (NoClassDefFoundError)
            //may want to cancel the stop altogether...
            LOG.info(String.format("No current quote avail for %1$s... assume that SL is fine", order.getPair()));
        }

        if (!badStop)
            order.setStopLoss(new StopLossOrder(sl));
        //if it's a short, the SL should be higher than the current price
        //(OANDA chucks a wobbly if these conditions are not met)
        boolean badTP = false;
        if (lastQuote != null) {
            //	    selling at the bid, profit implies buying at the ask, so TP < ask
            if (order.getUnits() < 0 && tp >= lastQuote.getAsk()) {

                LOG.error(String.format("Cannot set TP to %1$6.5f for SHORT because ask for %2$s is %3$6.5f", 
                        sl, order.getPair(), lastQuote.getAsk()));
                badTP = true;
            }
            //	    buying at the ask, profit implies selling at the bid, so TP > bid
            else if (order.getUnits() > 0 && tp <= lastQuote.getBid()) {
                LOG.error(String.format("Cannot set TP to %1$6.5f for LONG because bid for %2$s is %3$6.5f", 
                        sl, order.getPair(), lastQuote.getAsk()));
                badTP = true;
            }
        }
        else {
            LOG.info(String.format("No current quote avail for %1$s... assume that TP is fine", order.getPair()));
        }

        if (!badTP)
            order.setTakeProfit(new TakeProfitOrder(tp));

        return order;
    }

    private MarketOrder retrieveOrder(int ordNum) {
        OrderMessagePair order = m_openOrders.get(Integer.valueOf(ordNum));
        if (order == null) {
            LOG.error(String.format("Order number %1$d not found!", ordNum));
            return null;
        }

        return order.m_oandaOrder;
    }

    private boolean execute(int accId, com.oanda.fxtrade.api.MarketOrder order) {
        try {
            //	    Vector accounts = m_client.getUser().getAccounts();
            //	    for (int i = 0; i < accounts.size(); i++) {
            //		System.out.println("accountid=" + accounts.elementAt(i));
            //	    }
            //
            //	    Account myaccount = (Account)accounts.firstElement();
            //	    System.out.println("Account ID: " + myaccount.getAccountId());
            //	    System.out.println("MarginRate: " + myaccount.getMarginRate());

            //PLACE A MARKET ORDER
            //order.setUnits(-1000);
            //order.setPair(new FXPair("GBP/CHF"));
            //order.setStopLoss(new StopLossOrder(0.1029));



            //order.setStopLoss(null);
            //order.setTakeProfit(null);
            //	    try {
            //		myaccount.execute(neworder);
            //	    }
            //	    catch (OAException oae) {
            //		System.out.println("Example: caught: " + oae);
            //	    }

            LOG.debug(String.format("Executing MO %1$s on account %2$d", order, accId));
            com.oanda.fxtrade.api.Account acc = m_client.getUser().getAccountWithId(accId);
            acc.execute(order);
        } catch (SessionException e) {
            //thrown by getUser
            LOG.error(String.format("Failed to execute MO '%1$s'", order.toString()), e);
            return false;
        } catch (AccountException e) {
            //thrown by getAccountWithId
            LOG.error(String.format("Failed to execute MO '%1$s'", order.toString()), e);
            return false;
        } catch (OAException e) {
            //thrown by the execute method
            LOG.error(String.format("Failed to execute MO '%1$s'", order.toString()), e);
            return false;
        }

        return true;
    }

    private boolean close(int accId, com.oanda.fxtrade.api.MarketOrder order) {
        try {
            LOG.debug(String.format("Closing MO %1$s on account %2$d", order, accId));
            com.oanda.fxtrade.api.Account acc = m_client.getUser().getAccountWithId(accId);
            acc.close(order);
        } catch (SessionException e) {
            //thrown by getUser
            LOG.error(String.format("Failed to close MO '%1$s'", order.toString()), e);
            return false;
        } catch (AccountException e) {
            //thrown by getAccountWithId
            LOG.error(String.format("Failed to close MO '%1$s'", order.toString()), e);
            return false;
        } catch (OAException e) {
            //thrown by the execute method
            LOG.error(String.format("Failed to close MO '%1$s'", order.toString()), e);
            return false;
        }

        return true;
    }

    private boolean modify(int accId, com.oanda.fxtrade.api.MarketOrder order) {
        try {
            LOG.debug(String.format("Modifying MO %1$s on account %2$d", order, accId));
            com.oanda.fxtrade.api.Account acc = m_client.getUser().getAccountWithId(accId);
            acc.modify(order);
        } catch (SessionException e) {
            //thrown by getUser
            LOG.error(String.format("Failed to modify MO '%1$s'", order.toString()), e);
            return false;
        } catch (AccountException e) {
            //thrown by getAccountWithId
            LOG.error(String.format("Failed to modify MO '%1$s'", order.toString()), e);
            return false;
        } catch (OAException e) {
            //thrown by the execute method
            LOG.error(String.format("Failed to modify MO '%1$s'", order.toString()), e);
            return false;
        }

        return true;
    }

    private Vector<?> getTransactions(int accId) {
        Vector<?> transactions = null;
        try {
            LOG.debug("Getting last trasaction for account ID: " + Integer.toString(accId));
            com.oanda.fxtrade.api.Account acc = m_client.getUser().getAccountWithId(accId);
            transactions = acc.getTransactions();

        } catch (SessionException e) {
            //thrown by getUser
            LOG.error(String.format("Failed to get transactions: %1$s", e));
        } catch (AccountException e) {
            //thrown by getAccountWithId
            LOG.error(String.format("Failed to get transactions: %1$s", e));
        } 

        return transactions;
    }


    private boolean closeAllPositions(int accId) {
        LOG.info("Closing all positions on account: " + Integer.toString(accId));
        try {
            com.oanda.fxtrade.api.Account acc = m_client.getUser().getAccountWithId(accId);
            Vector<?> positions = acc.getPositions();
            for(Object obj : positions) {
                LOG.info("Closing position: " + obj.toString());
                Position posn = (Position)obj;
                MarketOrder closer = new MarketOrder();
                closer.setPair(posn.getPair());
                closer.setUnits(posn.getUnits());
                try {
                    acc.close(closer);
                } catch (OAException e) {
                    LOG.error(String.format("Failed to close MO '%1$s'... will keep trying to close all on acc %2$d", closer.toString(), accId), e);
                }
            }
        } catch (SessionException e) {
            //thrown by getUser
            LOG.error(String.format("Failed to close all positions on '%1$d'", accId), e);
            return false;
        } catch (AccountException e) {
            //thrown by getAccountWithId
            LOG.error(String.format("Failed to close all positions on '%1$d'", accId), e);
            return false;
        }
        
        //returns true even if some of the closes failed
        return true;
    }
}


//private void doRun() {    
//LOG.info("Oanda client starting");
//
//startMessenger();
//
//Message lastMsg = null;
//try {
//  while (m_isLoggedOn) {
//      try {
//          LOG.debug(String.format("Waiting for next message. There are %1$d in the queue", 
//                  m_unhandledMsgsFromClient.size()));
//
//          //processes msgs from client, blocking until the next message is rec'd
//          lastMsg = m_unhandledMsgsFromClient.take();
//          if (lastMsg != null) {
//              LOG.info(String.format("Message received: %1$d %2$s - %3$s", 
//                      lastMsg.getId(), lastMsg.getAction(), 
//                      (lastMsg.getData() == null ? "null" : lastMsg.getData())));
//
//              if (!isLoggedIn()) {
//                  LOG.error("OandaClient disconnected! isLoggedIn() returns false");
//                  throw new RuntimeException();
//              }
//
//              //trust that won't become disconnected in the time since checked above...
//              if (!doMessage(lastMsg)) {
//                  //if it couldn't be handled, put it back on the queue
//                  //until the number of attempts exceeds the maximum
//                  LOG.error("doMessage failed: " + lastMsg.toString());
//                  if (lastMsg.getTries() < m_maxMessageTries) {
//                      LOG.debug("getTries is less than max... retrying");
//                      LOG.info("Putting last msg back on unhandled msg list: " + lastMsg.toString());
//                      m_unhandledMsgsFromClient.put(lastMsg);
//                  }
//                  else {
//                      LOG.error("Cannot do message: " + lastMsg.toString());
//
//                      //send a failure notification
//                      Message msg = new Message(Message.Action.FailureNotification, lastMsg.toString(), 
//                              Message.Priority.Immediate, lastMsg.getId());
//                      if (!m_unhandledMsgsFromServer.add(msg))
//                          LOG.error("Message not added to unhandledMsgsFromServer: " + msg.toString());
//                  }
//              }
//          }
//          else {
//              LOG.debug("Null message received... trying again");
//          }
//      } catch (InterruptedException e) {
//          LOG.error("OandaClient interrupted.", e);
//          if (lastMsg != null && !m_unhandledMsgsFromClient.contains(lastMsg)) {
//              LOG.info("Message added back to list for processing later: " + lastMsg.toString(), e);
//              m_unhandledMsgsFromClient.put(lastMsg);
//          }
//          else if (lastMsg != null) {
//              LOG.info("lastMsg already on the list: " + lastMsg.toString());
//          }
//          else {
//              LOG.info("lastMsg null... presumably InterruptedException occurred before removing something from list");
//          }
//      }
//      //put any other recoverable exceptions in here...
//  }
//} catch (RuntimeException e) {
//  LOG.error("Exception on worker: ", e);
//
//  //        must rethrow to motivate relogin etc
//  throw e;
//}
//finally {
//  LOG.info("Oanda client finishing. m_isLoggedOn is: " + (m_isLoggedOn ? "True" : "False"));
//
//  if (lastMsg != null && !m_unhandledMsgsFromClient.contains(lastMsg)) {
//      LOG.info("Putting last msg back on unhandled msg list: " + lastMsg.toString());
//      m_unhandledMsgsFromClient.put(lastMsg);
//  }
//
//  for (Message unhandled : m_unhandledMsgsFromClient)
//      LOG.info("Unhandled message (for now): " + unhandled.toString());
//}
//}
