package c8.util;

import java.util.*;
import org.apache.log4j.*;

public class BarMaker {
    //    private BarMaker.Source m_source;

    private BarMaker.Sink m_sink;

    private BarMaker.TimeSeriesSink m_savedSink;

    private boolean m_shouldSave = false;

    private int m_numTicksRead = 0;

    private int m_numCreated = 0;

    private int m_numFailed = 0;

    private boolean m_isRunning;

    private Interval m_bar;

    private boolean m_startOnRoundTime = true;

    private boolean m_carryOverMissingBars = true;

    private WeekSpan m_tradingWeek;

    public static final WeekTime DEFAULT_WEEK_START;

    public static final WeekTime DEFAULT_WEEK_FINISH;

    private static final Logger LOG = LogManager.getLogger(BarMaker.class);

    // / <summary>
    // / Specifies the default number of bars which will be backfilled
    // / if there is a time gap in the tickstream. Use -1 to specify
    // / that all bars should be infilled (ie. no limit)
    // / </summary>
    // / <remarks>
    // / Does not effect the carry over bars process. Targeted at
    // / weekend and public holiday market closures, or where the data
    // / is not discontinuous for some reason
    // / </remarks>
    public static final int DEFAULT_INFILL_BARS = 10;

    private int m_maxInfillBars;

    HashMap<Security, HPBuilder> m_currentBars;
    HashMap<Security, HistoryPoint> m_carryOverBars;
    ArrayList<Security> m_carryOverSecurities;
    long m_barStart = 0;
    boolean m_isDuringTradingWeek = true;

    static {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(0);
        cal.set(Calendar.HOUR_OF_DAY, 17);
        long fivePm = cal.getTimeInMillis();
        // start at 5pm EST on Sunday
        DEFAULT_WEEK_START = new WeekTime(Calendar.SUNDAY, fivePm);

        // finish at 5pm EST on Friday
        DEFAULT_WEEK_FINISH = new WeekTime(Calendar.FRIDAY, fivePm);
    }

    public BarMaker() {
        this(null, Interval.EVERY_FIVE_MINS);
    }

    public BarMaker(Sink sink) {
        this(sink, Interval.EVERY_FIVE_MINS);
    }

    public BarMaker(Sink sink, Interval bar) {
        m_sink = sink;
        m_bar = bar;
        m_tradingWeek = new WeekSpan(DEFAULT_WEEK_START, DEFAULT_WEEK_FINISH);
        m_maxInfillBars = DEFAULT_INFILL_BARS;
    }

    private void init() {
        m_isRunning = true;
        m_numTicksRead = 0;
        m_numCreated = 0;
        m_numFailed = 0;

        if (m_shouldSave)
            m_savedSink = new TimeSeriesSink();

        if (m_sink == null) {
            if (m_savedSink != null)
                m_sink = m_savedSink;
            else
                m_sink = new TimeSeriesSink();
        }

        assert (m_sink != null);
        assert (!m_shouldSave || m_savedSink != null);

        m_currentBars = new HashMap<Security, HPBuilder>();
        m_carryOverBars = new HashMap<Security, HistoryPoint>();
        m_carryOverSecurities = new ArrayList<Security>();
    }

    public void tick(SecurityPrice sp) {

        if (!m_isRunning)
            init();

        assert (sp != null);
        if (sp == null)
            return;

        // use the first security price timestamp, which means that
        // subsequent
        // ticks (for diff securities) will still work from this base
        if (m_numTicksRead == 0) {
            if (m_startOnRoundTime) {
                if (m_bar.isRoundTime(sp.getTimeStamp()))
                    m_barStart = sp.getTimeStamp();
                else
                    m_barStart = m_bar.nextRoundTime(sp.getTimeStamp());
            } else {
                m_barStart = sp.getTimeStamp();
            }
        }

        m_numTicksRead++;

        // if the tick is outside the trading week, then reset
        // the barStart to the beginning of the next trading week
        if (m_isDuringTradingWeek && !m_tradingWeek.spans(sp.getTimeStamp())) {
            // need to be careful here... if the timestamp has skipped a
            // couple of days
            // (perhaps because the data source corresponds quite closely to
            // the trading week,
            // as is likely), then we might skip a week
            // the bar start is reliably in the current week, so use that as
            // our
            // starting point, rather than the timestamp of this data point
            m_barStart = m_tradingWeek.absoluteStart(m_barStart + (WeekTime.MILLIS_PER_DAY * WeekTime.DAYS_PER_WEEK));

            if (m_startOnRoundTime && !m_bar.isRoundTime(m_barStart))
                m_barStart = m_bar.nextRoundTime(m_barStart);

            // and remove any carry overs... they no longer apply
            m_carryOverSecurities.clear();
            m_carryOverBars.clear();
            m_isDuringTradingWeek = false;
        }

        if (sp.getTimeStamp() < m_barStart)
            return;

        m_isDuringTradingWeek = true;
        assert (m_tradingWeek.spans(sp.getTimeStamp()));

        // if the timestamp for ANY security passes the bar, save them all
        if (sp.getTimeStamp() - m_barStart > m_bar.duration()) {
            // save the bar
            HashMap<Security, HistoryPoint> newCarryOverBars = new HashMap<Security, HistoryPoint>();
            for (Map.Entry<Security, HPBuilder> entry : m_currentBars.entrySet()) {
                if (entry.getValue() == null)
                    continue;

                HistoryPoint hp = entry.getValue().getCurrent();
                Security s = entry.getKey();
                writeBar(s, hp);

                newCarryOverBars.put(s, hp);
                if (!m_carryOverSecurities.contains(s))
                    m_carryOverSecurities.add(s);
            }

            if (m_carryOverMissingBars) {
                for (Security s : m_carryOverSecurities) {
                    if (!m_currentBars.containsKey(s)) {
                        // then it hasn't been written above
                        // (and neither will it appear in newCarryOverBars)
                        assert (!newCarryOverBars.containsKey(s));
                        HistoryPoint hp;
                        if ((hp = m_carryOverBars.get(s)) != null) {
                            // ok, it's in the carry over bars
                            // so update it's timestamp and include the aspn
                            // that if there was no tick, then the market
                            // mustn't have moved
                            hp = new HistoryPoint(m_barStart, hp.getClose(), hp.getClose(), hp.getClose(), hp
                                    .getClose(), m_bar);
                            writeBar(s, hp);

                            // then add it to the new carry over bars
                            newCarryOverBars.put(s, hp);
                        }

                        // if the pair wasn't in this bar, or in the last
                        // bar, or carried over
                        // from some previous bar (newCarryOverBars.Add(s,
                        // hp)), then there's
                        // nothing we can do. if this approach works as
                        // intended, then if
                        // a pair has EVER had a full bar, it will be
                        // written
                    }
                }
            }

            m_currentBars.clear();
            m_carryOverBars = newCarryOverBars;

            assert (m_carryOverBars.size() == m_carryOverSecurities.size());

            // barStart is not this timestamp... want to be precise
            m_barStart = m_barStart + m_bar.duration();

            // accommodate any jumps in the tick stream (ie. the tick
            // streams
            // skips some bars)
            long dataPtBarStart = m_bar.nextRoundTime(sp.getTimeStamp()) - m_bar.duration();
            if (dataPtBarStart > m_barStart) {
                // fill in the missing bars. could be just a few or quite a
                // lot
                // eg. what if there is a public holiday? what if just a 10
                // min gap?
                // if we are outside the trading week then this code should
                // never run
                assert (m_tradingWeek.spans(sp.getTimeStamp()));

                int reqdInfillBars = (int) ((sp.getTimeStamp() - m_barStart) / m_bar.duration());

                // negative value for maxInfillBars indicates to infill as
                // much as reqd
                if (m_maxInfillBars < 0 || reqdInfillBars < m_maxInfillBars) {
                    // if we don't have any carry over bars, then we can't
                    // write anything
                    for (Map.Entry<Security, HistoryPoint> entry : m_carryOverBars.entrySet()) {
                        LOG.info(String.format(
                                "Infilling %1$s bars between %2$tA, %2$tT and %3$tA, %3$tT for %4$s",
                                reqdInfillBars, m_barStart, dataPtBarStart, entry.getKey().getTicker()));

                        long currBarStart = m_barStart;
                        for (int nInfillBars = 0; nInfillBars < reqdInfillBars; nInfillBars++) {
                            assert (currBarStart < dataPtBarStart);
                            HistoryPoint hp = new HistoryPoint(currBarStart, entry.getValue().getClose(), entry
                                    .getValue().getClose(), entry.getValue().getClose(), entry.getValue()
                                    .getClose(), m_bar);

                            writeBar(entry.getKey(), hp);

                            currBarStart += m_bar.duration();
                        }
                    }
                }

                m_barStart = dataPtBarStart;
            }
        }

        if (!m_currentBars.containsKey(sp.getSecurity())) {
            // then the open has not been captured for this security
            HPBuilder hpb = new HPBuilder(m_barStart, m_bar);
            hpb.tick(sp.getTickPrice());
            m_currentBars.put(sp.getSecurity(), hpb);
        } else {
            m_currentBars.get(sp.getSecurity()).tick(sp.getTickPrice());
        }

    }

    private void writeBar(Security s, HistoryPoint hp) {
        try {
            m_sink.save(s, hp);
            m_numCreated++;
        } catch (Exception e) {
            LOG.error("Exception: " + e.toString());
            LOG.error(String.format("Missed value: %1$s %2$s", s.getTicker(), hp.toString()));
            m_numFailed++;
        }

        if (m_shouldSave && m_sink != m_savedSink)
            m_savedSink.save(s, hp);
    }

    //    public BarMaker.Source getSource() {
    //        return m_source;
    //    }
    //
    //    public void setSource(BarMaker.Source value) {
    //        // fail silently
    //        if (!m_isRunning) {
    //            m_source = value;
    //        }
    //    }

    public BarMaker.Sink getSink() {
        return m_sink;
    }

    public void setSink(BarMaker.Sink value) {
        // fail silently
        if (!m_isRunning) {
            m_sink = value;
        }
    }

    public BarMaker.TimeSeriesSink getSavedSink() {
        return m_savedSink;
    }

    public boolean getShouldSave() {
        return m_shouldSave;
    }

    public void setShouldSave(boolean value) {
        // fail silently
        if (!m_isRunning) {
            m_shouldSave = value;
        }
    }

    public Interval getBar() {
        return m_bar;
    }

    public void setBar(Interval value) {
        // fail silently
        if (!m_isRunning) {
            m_bar = value;
        }
    }

    public boolean getStartOnRoundTime() {
        return m_startOnRoundTime;
    }

    public void setStartOnRoundTime(boolean value) {
        // fail silently
        if (!m_isRunning) {
            m_startOnRoundTime = value;
        }
    }

    public boolean getCarryOverMissingBars() {
        return m_carryOverMissingBars;
    }

    public void setCarryOverMissingBars(boolean value) {
        // fail silently
        if (!m_isRunning) {
            m_carryOverMissingBars = value;
        }
    }

    public WeekTime getTradingWeekStart() {
        return m_tradingWeek.getStart();
    }

    public void setTradingWeekStart(WeekTime value) {
        // fail silently
        if (!m_isRunning) {
            m_tradingWeek = new WeekSpan(value, m_tradingWeek.getFinish());
        }
    }

    public WeekTime getTradingWeekFinish() {
        return m_tradingWeek.getFinish();
    }

    public void setTradingWeekFinish(WeekTime value) {
        // fail silently
        if (!m_isRunning) {
            m_tradingWeek = new WeekSpan(m_tradingWeek.getStart(), value);
        }
    }

    public WeekSpan getTradingWeek() {
        return m_tradingWeek;
    }

    public void setTradingWeek(WeekSpan value) {
        // fail silently
        if (!m_isRunning) {
            m_tradingWeek = value;
        }
    }

    public int getMaximumInfillBars() {
        return m_maxInfillBars;
    }

    public void setMaximumInfillBars(int value) {
        // fail silently
        if (!m_isRunning) {
            m_maxInfillBars = value;
        }
    }

    public int getNumberCreated() {
        return m_numCreated;
    }

    public int getNumberFailed() {
        return m_numFailed;
    }

    public int getNumberTicksRead() {
        return m_numTicksRead;
    }

    // public interface Sink
    // {
    // void save(Security s, HistoryPoint hp);
    // void close();
    // }

    //    public interface Source extends Iterable<SecurityPrice> {
    //	void close();
    //    }

    public interface Sink {
        void save(Security s, HistoryPoint hp);

        void close();
    }

    public class TimeSeriesSink implements Sink {
        HashMap<Security, HistoryPointTimeSeries> m_data = new HashMap<Security, HistoryPointTimeSeries>();

        public void save(Security s, HistoryPoint hp) {
            HistoryPointTimeSeries hpt;
            if ((hpt = m_data.get(s)) != null) {
                hpt = new HistoryPointTimeSeries(s);
                m_data.put(s, hpt);
            }

            hpt.add(hp);
        }

        public HashMap<Security, HistoryPointTimeSeries> getData() {
            return m_data;
        }

        public void close() {
            // nothing to do
        }

    }

    /**
     * @param args
     */
    //    public static void main(String[] args) {
    //        if (args.length == 0) {
    //            System.out.println("Usage: OdbcBarMaker <ConfigFile>");
    //            return;
    //        }
    //
    //        // note: must specify none or both of sourcedbtype and sinkdbtype... if
    //        // one is specified and not the other, the program will simply make them
    //        // both the same
    //
    //        // use a config file to set
    //        // 1. select db connect string - "SourceConnectString=[text]"
    //        // 2. select db type (optional), default=ODBC
    //        // "SourceType=[Sql|MySql|Odbc|OleDb|File]"
    //        // 3. insert db connect string (optional) - "SinkConnectString=[text]"
    //        // 4. insert db type (optional), default=ODBC
    //        // "SinkType=[Sql|MySql|Odbc|OleDb|File]"
    //        // 5. select sql (optional) (Ticker, Timestamp, Bid, Ask) -
    //        // "SelectSQL=[text]"
    //        // 6. insert table name (for db sinks only) - "InsertTableName=[text]"
    //        // 7. database id of the market "MarketID=[parseable to Integer]"
    //        // 8. interval (optional) "Interval=[parseable to Interval]"
    //        // 9. database id of the interval "IntervalID=[parseable to Integer]"
    //        // 10. isBidOfferData (optional) "BidOfferData=[parseable to
    //        // booleanean]"
    //        // 11. round times (optional) "StartOnRoundTime=[parseable to
    //        // booleanean]"
    //        // 12. carry over prev bars (optional) "CarryOverMissingBars=[parseable
    //        // to booleanean]"
    //        // 13. trading week start (optional) "TradingWeekStartTime=[parseable to
    //        // WeekTime (dddd,HH:mm(:ss)]"
    //        // 14. trading week finish (optional) "TradingWeekFinishTime=[parseable
    //        // to WeekTime (dddd,HH:mm(:ss)]"
    //        // 15. max infill bars (optional) "MaxInfillBars=[parseable to int]"
    //
    //        String sourceConnectStr = null, sinkConnectStr = null;
    //        String selectSql = null;
    //        String insertTableName = null;
    //        String sourceType = null, sinkType = null;
    //        Interval ival = Interval.EVERY_FIVE_MINS;
    //        int marketId = -1, intervalId = -1;
    //        boolean isBidOffer = false;
    //        boolean startOnRound = false;
    //        boolean carryOverMissing = false;
    //        WeekTime weekStartTime = BarMaker.DEFAULT_WEEK_START;
    //        WeekTime weekFinishTime = BarMaker.DEFAULT_WEEK_FINISH;
    //        int maxInfillBars = BarMaker.DEFAULT_INFILL_BARS;
    //        try {
    //            BufferedReader reader = new BufferedReader(new FileReader(args[0]));
    //            String line;
    //            while ((line = reader.readLine()) != null) {
    //                if (line.startsWith("#") || line.isEmpty())
    //                    continue;
    //
    //                int index = line.indexOf('=');
    //                if (index < 0 || index > line.length()) {
    //                    System.err.println(String.format("Ignored line '%1$s' because it was incorrectly formatted", line));
    //                    continue;
    //                }
    //
    //                String[] parts = new String[] { line.substring(0, index), line.substring(index + 1) };
    //
    //                if (parts[0].equals("SourceConnectString"))
    //                    sourceConnectStr = parts[1];
    //                else if (parts[0].equals("SourceType"))
    //                    sourceType = parts[1];
    //                else if (parts[0].equals("SinkConnectString"))
    //                    sinkConnectStr = parts[1];
    //                else if (parts[0].equals("SinkType"))
    //                    sinkType = parts[1];
    //                else if (parts[0].equals("SelectSQL"))
    //                    selectSql = parts[1];
    //                else if (parts[0].equals("InsertTableName"))
    //                    insertTableName = parts[1];
    //                else if (parts[0].equals("MarketID"))
    //                    marketId = Integer.getInteger(parts[1]);
    //                else if (parts[0].equals("Interval"))
    //                    ival = Interval.valueOf(parts[1]);
    //                else if (parts[0].equals("IntervalID"))
    //                    intervalId = Integer.getInteger(parts[1]);
    //                else if (parts[0].equals("BidOfferData"))
    //                    isBidOffer = Boolean.parseBoolean(parts[1]);
    //                else if (parts[0].equals("StartOnRoundTime"))
    //                    startOnRound = Boolean.parseBoolean(parts[1]);
    //                else if (parts[0].equals("CarryOverMissingBars"))
    //                    carryOverMissing = Boolean.parseBoolean(parts[1]);
    //                else if (parts[0].equals("TradingWeekStartTime"))
    //                    weekStartTime = WeekTime.parse(parts[1]);
    //                else if (parts[0].equals("TradingWeekFinishTime"))
    //                    weekFinishTime = WeekTime.parse(parts[1]);
    //                else if (parts[0].equals("MaxInfillBars"))
    //                    maxInfillBars = Integer.getInteger(parts[1]);
    //                else
    //                    System.err.println(String.format("Ignored line '%1$s' because '%2$s' is not a valid param", line,
    //                            parts[0]));
    //            }
    //        } catch (Exception e) {
    //            // Let the user know what went wrong.
    //            e.printStackTrace();
    //        }
    //
    //        // ***** Check minimum conditions *****
    //
    //        boolean canRun = true;
    //        if (sourceConnectStr == null) {
    //            System.err.println("No connection String supplied. Format: SourceConnectString=[text]");
    //            canRun = false;
    //        }
    //
    //        if (sinkConnectStr == null)
    //            sinkConnectStr = sourceConnectStr;
    //
    //        if (selectSql == null && sourceType != "File") {
    //            System.err.println("No select SQL supplied. Format: SelectSQL=[text]");
    //            canRun = false;
    //        }
    //
    //        if (insertTableName == null && sinkType != "File") {
    //            System.err.println("No insert tablename supplied. Format: InsertTableName=[text]");
    //            canRun = false;
    //        }
    //
    //        if (marketId == -1 && sinkType != "File") {
    //            System.err.println("No DB MarketID supplied (or -1, invalid). Format: MarketID=[parseable to Integer]");
    //            canRun = false;
    //        }
    //
    //        if (intervalId == -1 && sinkType != "File") {
    //            System.err.println("No DB IntervalID supplied (or -1, invalid). Format: IntervalID=[parseable to Integer]");
    //            canRun = false;
    //        }
    //
    //        if (!canRun)
    //            return;
    //
    //        // ***** region Setup source and sink *****
    //
    ////        BarMaker.Source source = null;
    //        BarMaker.Sink sink = null;
    //
    //        // if one is null, make the source and sink both the same
    //        if (sinkType == null && sourceType != null)
    //            sinkType = sourceType;
    //        else if (sourceType == null && sinkType != null)
    //            sourceType = sinkType;
    //
    //        assert ((sourceType == null && sinkType == null) || (sourceType != null && sinkType != null));
    //
    ////        if (sourceType.equals("File")) {
    ////            try {
    ////                source = new FileSource(sourceConnectStr);
    ////            } catch (IOException e) {
    ////                e.printStackTrace();
    ////                source = null;
    ////            }
    ////        } else {
    ////            try {
    ////                Connection sourceConn = getDbConnection(sourceType, sourceConnectStr);
    ////                source = new DbSource(sourceConn, selectSql, isBidOffer);
    ////            } catch (SQLException e) {
    ////                e.printStackTrace();
    ////                source = null;
    ////            }
    ////        }
    ////
    ////        if (sinkType.equals("File")) {
    ////            sink = new FileSink(sinkConnectStr);
    ////        } else {
    ////            try {
    ////                Connection sinkConn = getDbConnection(sinkType, sinkConnectStr);
    ////                sink = new DbBarSink(sinkConn, insertTableName, marketId, intervalId);
    ////            } catch (SQLException e) {
    ////                e.printStackTrace();
    ////                sink = null;
    ////            }
    ////        }
    //
    //        if (source == null) {
    //            System.err.println("The source connection failed. Unable to proceed.");
    //            return;
    //        }
    //
    //        if (sink == null) {
    //            System.err.println("The sink connection failed. Unable to proceed.");
    //            return;
    //        }
    //
    //        BarMaker barMaker = new BarMaker(source, sink, ival);
    //        barMaker.setStartOnRoundTime(startOnRound);
    //        barMaker.setCarryOverMissingBars(carryOverMissing);
    //        barMaker.setTradingWeekStart(weekStartTime);
    //        barMaker.setTradingWeekFinish(weekFinishTime);
    //        barMaker.setMaximumInfillBars(maxInfillBars);
    //
    //        Thread worker = new Thread(barMaker);
    //        try {
    //            // at this point we don't want any possibility of a basic error
    //            worker.start();
    //            while (worker.isAlive()) {
    //                System.out.println(String.format("Processing: %1$s ticks read %2$s created, %3$s failed\r", barMaker
    //                        .getNumberTicksRead(), barMaker.getNumberCreated(), barMaker.getNumberFailed()));
    //                Thread.sleep(1000);
    //            }
    //        } catch (Exception e) {
    //            System.err.println("Fatal error: " + e.toString());
    //            System.err.println("Closing...");
    //        } finally {
    //            try {
    //                worker.join();
    //            } catch (InterruptedException e) {
    //                System.err.println("Unable to join the worker thread.");
    //                e.printStackTrace();
    //            }
    //            source.close();
    //            sink.close();
    //        }
    //    }
    //
    //    private static Connection getDbConnection(String type, String url) throws SQLException {
    //        String forName;
    //        String odbcForName = "sun.jdbc.odbc.JdbcOdbcDriver";
    //        if (type.equals("Sql"))
    //            // TODO:
    //            forName = odbcForName;
    //        else if (type.equals("MySql"))
    //            forName = "com.mysql.jdbc.Driver";
    //        else if (type.equals("OleDb"))
    //            // TODO:
    //            forName = odbcForName;
    //        else if (type.equals("Odbc"))
    //            forName = odbcForName;
    //        else
    //            forName = odbcForName;
    //
    //        while (true) {
    //            try {
    //                Class.forName(forName);
    //                break;
    //            } catch (ClassNotFoundException e) {
    //                System.err.printf("Exception raised at GetDbConnection(%1$s, %2$s): %3$s\n", type, url, e);
    //                if (forName.equals(odbcForName)) {
    //                    System.err.println("ODBC is not supposed to fail!!");
    //                    return null;
    //                } else {
    //                    System.out.println("Attempting ODBC connection instead...");
    //                    forName = odbcForName;
    //                }
    //            }
    //        }
    //
    //        return DriverManager.getConnection(url);
    //    }

}
