package c8.trading;

import java.util.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import c8.ExecEnviron;
import c8.gateway.Message;
import c8.gateway.MessageSink;
import c8.gateway.MessageSource;
import c8.gateway.MsgMarketOrder;
import c8.util.*;

public class Agent {

    static final Logger LOG = LogManager.getLogger(Agent.class);

    private String m_openingAccountId;

    private String m_uniqueId;

    private Security m_security;

    private long m_lastTrialTime;

    private long m_lastOrderTime;

    private Interval m_tradeInterval;

    private double m_float;

    private Currency m_reportingCcy;

    private SignalProvider m_signalProvider;

    private PositionSizer m_posnSizer;

    private StopAdjuster m_stopMover;

    private double m_maxAcceptableSpreadPips = 5.0;

    private BackOffice m_backOffice;

    private boolean m_wasNullReplyLast = false;

    // / <summary>
    // / Stores an existing market order, if there is one. The order is
    // considered
    // / open until either a limit order is executed to reverse the position, or
    // / immediately upon a closing market order, SL or TP
    // / </summary>
    private MarketOrder m_openMarketOrder;

    public Agent() {
        // m_accountant.DataStore = new CloudEight.Data.OdbcTradingDataStore();
        m_reportingCcy = ExecEnviron.getAccountingCcy();
        m_tradeInterval = Interval.EVERY_FIVE_MINS;
        m_backOffice = new BackOffice();
    }

    public String toString() {
        return String.format("'%1$s' (%2$s on %3$s)", m_uniqueId, m_security.getTicker(), 
                ExecEnviron.getName());
    }

    public Currency getAccountingCcy() {
        return m_reportingCcy;
    }

    public void setAccountingCcy(Currency ccy) {
        m_reportingCcy = ccy;
        LOG.info(String.format("%1$s -> Accounting ccy set to %2$s", m_uniqueId, ccy.getCurrencyCode()));
    }

    public String getUniqueName() {
        return m_uniqueId;
    }

    public void setUniqueName(String value) {
        m_uniqueId = value;
        LOG.info(String.format("%1$s -> unique name set", m_uniqueId));
    }

    public String getOpeningAccountId() {
        return m_openingAccountId;
    }

    public void setOpeningAccountId(String value) {
        throwExceptionIfActive();
        m_openingAccountId = value;
        LOG.info(String.format("%1$s -> Opening account ID set to %2$s", m_uniqueId, m_openingAccountId));
    }

    public Security getSecurity() {
        return m_security;
    }

    public void setSecurity(Security value) {
        throwExceptionIfActive();
        m_security = value;
        LOG.info(String.format("%1$s -> Security set to %2$s", m_uniqueId, m_security.getTicker()));
    }

    public Interval getTradeInterval() {
        return m_tradeInterval;
    }

    // recorded when the agent subscribes to a TimedBot
    // public void setTradeInterval(Interval value) {
    // throwExceptionIfActive();
    // m_tradeInterval = value;
    // }

    public BackOffice getBackOffice() {
        return m_backOffice;
    }

    public StopAdjuster getStopAdjuster() {
        return m_stopMover;
    }

    public void setStopAdjuster(StopAdjuster value) {
        // HACK: do we want to be able to change this while active?
        throwExceptionIfActive();
        m_stopMover = value;
        LOG.info(String.format("%1$s -> Stop adjuster set to %2$s", m_uniqueId, m_stopMover.getName()));
    }

    public PositionSizer getPositionSizer() {
        return m_posnSizer;
    }

    public void setPositionSizer(PositionSizer value) {
        // HACK: do we want to be able to change this while active?
        throwExceptionIfActive();
        m_posnSizer = value;
        LOG.info(String.format("%1$s -> Position sizer set to %2$s", m_uniqueId, m_posnSizer.getName()));
    }

    public SignalProvider getSignalProvider() {
        return m_signalProvider;
    }

    public void setSignalProvider(SignalProvider value) {
        // HACK: do we want to be able to change this while active?
        throwExceptionIfActive();
        m_signalProvider = value;
        LOG.info(String.format("%1$s -> Signal provider set to %2$s", m_uniqueId, m_signalProvider.getName()));
    }

    private void throwExceptionIfActive() {
        if (this.isActive())
            throw new RuntimeException("Cannot change parameter values while Agent is active");
    }

    public long getLastTrialTime() {
        return m_lastTrialTime;
    }

    public long getLastOrderTime() {
        return m_lastOrderTime;
    }

    protected MarketOrder getOpenMarketOrder() {
        return m_openMarketOrder;
    }

    public String getName() {
        return m_uniqueId;
    }

    public String getDescription() {
        return String.format("%1$s -> Signal %2$s, Sizer %3$s, Stops %4$s", m_uniqueId, 
                m_signalProvider.getName(), m_posnSizer.getName(), m_stopMover.getName());
    }

    public Balance availableFunds() {
        return m_backOffice.getAvailableFunds().convert(m_reportingCcy);
    }

    public Currency getReportingCcy() {
        return m_reportingCcy;
    }

    public void setReportingCcy(Currency ccy) {
        m_reportingCcy = ccy;
        LOG.info(String.format("%1$s -> Reporting currency set to %2$s", m_uniqueId, ccy.getCurrencyCode()));
    }

    public Balance fundsAtRisk() {
        if (m_openMarketOrder == null)
            return new Balance(0, m_reportingCcy);

        return new Balance(m_openMarketOrder.fundsAtRisk().getAmount(), ExecEnviron.getAccountingCcy());
    }

    public Amount getAllocatedFunds() {
        return m_backOffice.getAllocatedFunds().convert(m_reportingCcy);
    }

    public double getMaxAcceptableSpreadPips() {
        return m_maxAcceptableSpreadPips;
    }

    public void setMaxAcceptableSpreadPips(double pips) {
        m_maxAcceptableSpreadPips = pips;
        LOG.info(String.format("%1$s -> Max acceptable spread pips set to %2$s", m_uniqueId, Double.toString(pips)));
    }

    public Balance investedFundsAtCost() {
        return m_backOffice.getInvestedFundsAtCost().convert(m_reportingCcy);
    }

    public Balance investedFundsAtValue() {
        throw new RuntimeException();

        //        if (m_openMarketOrder == null)
        //            return new Balance(0, m_reportingCcy);
        //
        //        Transaction trans = m_account.getTransactions().get(
        //                m_openMarketOrder.getTransactionNumber());
        //        if (trans == null) {
        //            LOG.error(String.format("%1$s -> Cannot find Trans Num %2$s associated with MO %3$s",
        //                    m_uniqueId, m_openMarketOrder.getTransactionNumber(), m_openMarketOrder.getId()));
        //            return new Balance(0, m_reportingCcy);
        //        }
        //
        //        Amount investment = trans.getPrice().convert(m_reportingCcy).multiply(
        //                trans.getUnits());
        //        return m_openMarketOrder.unrealisedPL().add(investment);
    }

    public boolean isActive() {
        return hasOpenMarketOrder();
    }

    public void setAllocatedFunds(Amount value) {
        m_backOffice.setAllocatedFunds(value);

        LOG.info(String.format("%1$s -> Allocated funds set to %2$s", m_uniqueId, value.toString()));
    }

    public void setFloat(double value) {
        m_float = value;

        LOG.info(String.format("%1$s -> Opening float set to %2$s", m_uniqueId, Double.toString(value)));

        setAllocatedFunds(new Amount(value, m_reportingCcy));
    }

    public boolean hasOpenMarketOrder() {
        return m_openMarketOrder != null;
    }

    // Algorithm and execution

    // public virtual BacktestResult BackTest(HistoryPointTimeSeries hpts)
    // {
    // int numBars = hpts.Count;

    // //HACK: how does this interact with the ExecEnviron??
    // BacktestPriceGateway testGateway = new BacktestPriceGateway(hpts);

    // Agent agent = this.CloneStrategy();
    // agent.Account = testGateway.Account;
    // agent.UniqueName = m_uniqueId + "_Backtest";
    // agent.Security = m_security;
    // agent.TradeInterval = m_tradeInterval;

    // agent.m_accountant = new FundAccountant(this,
    // BacktestResult.DEFAULT_STARTING_FUNDS);

    // //PM? log file?

    // BacktestResult result = new BacktestResult();
    // result.OpeningFunds = agent.AllocatedFunds;
    // Debug.Assert(agent.AllocatedFunds ==
    // BacktestResult.DEFAULT_STARTING_FUNDS);

    // while (testGateway.CurrentStep < numBars)
    // {
    // agent.Trade();

    // testGateway.Step();
    // }

    // //TODO: keep track of backtest result, both intra and after this process

    // m_lastBacktestResult = result;

    // return result;
    // }

    // / <summary>
    // / Tells the Agent to trade its algorithm
    // / </summary>
    // / <returns>An IOrder if the agent trades, otherwise null</returns>
    // / <remarks>Agents may override this method, but this will interfere with
    // / backtesting and result maintenance</remarks>
    public Order trade() {
        LOG.info(String.format("%1$s -> Starting trade method", m_uniqueId));

        if (!m_signalProvider.isInitialized())
            initializeSignalProvider();

        Order order = null;

        m_backOffice.logReconciliationErrors();

        m_lastTrialTime = ExecEnviron.time();
        
        if (!ExecEnviron.isConnected()) {
            LOG.error("Server appears to be disconnected.  Do nothing until it is.");
            return null;
        }

        if (!hasOpenMarketOrder()) {
            LOG.info(String.format("%1$s -> No open Market or Limit order exists", m_uniqueId));
            order = tryEnter();
            if (order != null)
                m_lastOrderTime = ExecEnviron.time();
        } else {
            LOG.info(String.format("%1$s -> Open order exists", m_uniqueId));
            tryExit();
        }

        manageOpenPosition();

        LOG.info(String.format("%1$s -> Ending trade method", m_uniqueId));

        return order;
    }

    private void initializeSignalProvider() {
        if (m_signalProvider.isInitialized())
            return;

        LOG.info(String.format("%1$s -> Initializing Signal Provider", m_uniqueId));

        m_signalProvider.initialize(ExecEnviron.getPriceTable(), m_security, m_tradeInterval);
    }

    private Order tryEnter() {
        assert (!hasOpenMarketOrder());
        if (hasOpenMarketOrder()) {
            LOG.error(String.format("%1$s -> Attempt to enter market twice on %2$s", m_uniqueId, this.toString()));
            return null;
        }

        LOG.info(String.format("%1$s -> tryEnter() called", m_uniqueId));

        PriceTable pt = ExecEnviron.getPriceTable();

        TickPrice currPrice = pt.getPrice(m_security);
        if (currPrice == null) {
            LOG.error("Could not retrieve current price for security: " + m_security.getTicker());
            return null;
        }

        if (currPrice.getSpreadPips() > m_maxAcceptableSpreadPips) {
            LOG.info(String.format("%1$s -> Last tick price (%2$s) exceeds the max acceptable spread (%3$6.4f). Skipping tryEnter()",
                    m_uniqueId, currPrice, m_maxAcceptableSpreadPips));
            return null;
        }

        HistoryPoint lastBar = pt.getLastHistoryPoint(m_security, m_tradeInterval);

        if (lastBar == null) {
            LOG.error(String.format("%1$s -> PriceTable returned null history point. Cannot trade.", m_uniqueId));
            return null;
        }

        LOG.info(String.format("%1$s -> processing last bar for entry signal", m_uniqueId));
        SignalProvider.TradeSignal sig = m_signalProvider.processBar(lastBar);
        if (sig.equals(SignalProvider.TradeSignal.LONG)) {
            LOG.info(String.format("%1$s -> LONG signal received", m_uniqueId));
            double longPosn = m_posnSizer.calculatePosition();
            m_openMarketOrder = executeMarketOrder(longPosn);
        } else if (sig.equals(SignalProvider.TradeSignal.SHORT)) {
            LOG.info(String.format("%1$s -> SHORT signal received", m_uniqueId));
            // note: sell uses a negative position
            double shortPosn = -m_posnSizer.calculatePosition();
            m_openMarketOrder = executeMarketOrder(shortPosn);
        }

        return m_openMarketOrder;
    }

    private void tryExit() {
        // check for an exit signal
        // if exit signal, issue an order against it
        assert (m_openMarketOrder != null);
        if (m_openMarketOrder == null) {
            LOG.debug(String.format("%1$s -> Attempt to exit without open market order on Agent",
                    m_uniqueId));
            return;
        }

        LOG.info(String.format("%1$s -> tryExit() called", m_uniqueId));

        HistoryPoint lastBar = ExecEnviron.getPriceTable().getLastHistoryPoint(m_security,
                m_tradeInterval);

        LOG.info(String.format("%1$s -> processing last bar for exit signal", m_uniqueId));
        SignalProvider.TradeSignal sig = m_signalProvider.processBar(lastBar);

        // only issue a close if the signal is opposite the position
        if ((sig.equals(SignalProvider.TradeSignal.LONG) && m_openMarketOrder.isShort())
                || (sig.equals(SignalProvider.TradeSignal.SHORT) && m_openMarketOrder.isLong())) {
            LOG.info(String.format("%1$s -> %2$s signal received on Agent for MO %3$s", 
                    m_uniqueId, sig.toString(), m_openMarketOrder.getId()));

            m_backOffice.recordClosed(m_openMarketOrder, m_uniqueId);
                 
            Transaction reply = ExecEnviron.close(m_openMarketOrder, m_openingAccountId);

            if (reply != null) {                
                m_backOffice.recordTransacted(reply, m_uniqueId);
                LOG.info(String.format("%1$s -> CLOSED MO %2$s", m_uniqueId, m_openMarketOrder.getId()));
                m_openMarketOrder = null;
            }
            else {
                LOG.error("No reply received.  Order will probably be closed, but set flag and stop trading");
                m_wasNullReplyLast = true;
            }
        }
    }

    // / <summary>
    // / Move stops on any Market Order (not limit order) using IStopAdjuster
    // / </summary>
    private void manageOpenPosition() {
        LOG.info(String.format("%1$s -> manageOpenPosition() called", m_uniqueId));

        TickPrice tp = ExecEnviron.getPriceTable().getPrice(m_security);
        if (m_openMarketOrder != null && m_stopMover.shouldMoveStops(m_openMarketOrder, tp)) {            
            // make sure that the stop adjuster doesn't exceed the position sizer's
            // risk appetite... get it to adjust a cloned order and see what the
            // result is in risk terms
            MarketOrder clnOrder = (MarketOrder) m_openMarketOrder.clone();
            m_stopMover.moveStops(clnOrder, tp);
            double posn = m_posnSizer.calculatePosition();
            Amount currRiskFunds = m_openMarketOrder.fundsAtRisk();
            Amount movedRiskFunds = clnOrder.fundsAtRisk();
            Amount riskAppetite = (Amount)(availableFunds().multiply(posn));
            LOG.info(String.format("%1$s -> Current risk: %2$s Moved risk: %3$s Risk Appetite: %4$s", 
                    m_uniqueId, currRiskFunds, movedRiskFunds, riskAppetite));

            if (currRiskFunds.greaterThan(riskAppetite) && movedRiskFunds.greaterThan(currRiskFunds))
                LOG.warn(String.format("%1$s -> Existing order exceeds risk appetite and stop mover is going harder! Current risk: %2$s Moved risk: %3$s Risk Appetite: %4$s", 
                        m_uniqueId, currRiskFunds, movedRiskFunds, riskAppetite));
            else if (currRiskFunds.greaterThan(riskAppetite) && movedRiskFunds.greaterThan(riskAppetite))
                LOG.warn(String.format("%1$s -> Existing order exceeds risk appetite and stop mover is not helping! Current risk: %2$s Moved risk: %3$s Risk Appetite: %4$s", 
                        m_uniqueId, currRiskFunds, movedRiskFunds, riskAppetite));

            if (movedRiskFunds.equals(riskAppetite)) {
                LOG.info(String.format("%1$s -> Open position has correct risk already, doing nothing", m_uniqueId));
            }
            else if (movedRiskFunds.lessThan(riskAppetite)) {
                LOG.info(String.format("%1$s -> Moving stops", m_uniqueId));
                // only allows the StopAdjuster to move stops, and the SL
                // can only be moved upwards for longs and downwards for shorts
                // if no stop loss has been set, then use the cloned order
                if (m_openMarketOrder.getStopLoss().getAmount() == 0)
                    m_openMarketOrder.setStopLoss(clnOrder.getStopLoss());
                else if (m_openMarketOrder.isLong())
                    m_openMarketOrder.setStopLoss(Amount.max(clnOrder.getStopLoss(),
                            m_openMarketOrder.getStopLoss()));
                else
                    m_openMarketOrder.setStopLoss(Amount.min(clnOrder.getStopLoss(),
                            m_openMarketOrder.getStopLoss()));

                // change the take profit in any case
                m_openMarketOrder.setTakeProfit(clnOrder.getTakeProfit());

                // TODO: should we allow the order to expand in units if the
                // stop narrows?
                // ie. make sure that the risk funds = availfunds * posn
                // this feels like it should be an outsourced policy decision
                // (ask PM?)
                LOG.info(String.format("%1$s -> MODIFYING SL to %2$6.5f and TP to %3$6.5f on MO %4$s",
                        m_uniqueId, m_openMarketOrder.getStopLoss().getAmount(), 
                        m_openMarketOrder.getTakeProfit().getAmount(), m_openMarketOrder.getId()));

                m_backOffice.recordModified(m_openMarketOrder, m_uniqueId);
                Transaction reply = ExecEnviron.modify(m_openMarketOrder, m_openingAccountId);

                if (reply != null) {
                    m_backOffice.recordTransacted(reply, m_uniqueId);
                }
                else {
                    LOG.error("No reply received.  Order will probably be modified, but set flag and stop trading");
                    m_wasNullReplyLast = true;
                }

            } else {
                LOG.info(String.format("%1$s -> Stop mover attempted to exceed required %2$d position. Ignored."
                        + "\tPrice %3$6.5f, Old Stop %4$s, New Stop %5$s, Avail Risk Cap %6$s",
                        m_uniqueId, (int)(posn * 100), tp.getMean(), m_openMarketOrder.getStopLoss(), 
                        clnOrder.getStopLoss(), availableFunds()));
            }

        }
    }

    // / <summary>
    // / Executes a market order using the long security
    // / </summary>
    // / <param name="availFundsPercent">Percent of available funds to commit to
    // the order</param>
    private MarketOrder executeMarketOrder(double availFundsPercent) {
        return executeMarketOrder(convertToUnits(availFundsPercent));
    }

    private int convertToUnits(double percentOfAvailFunds) {
        if (percentOfAvailFunds == 0)
            return 0;

        LOG.debug(String.format("%1$s -> Calculating units for %2$6.2f percent of avail funds", 
                m_uniqueId, percentOfAvailFunds * 100));
        TickPrice tp = ExecEnviron.getPriceTable().getPrice(m_security);
        LOG.debug("\tTick Price is: " + tp.toString());
        Price execPrice = (percentOfAvailFunds > 0 ? tp.getAskPrice() : tp.getBidPrice());
        Price slPrice = m_stopMover.calculateSL(tp, (percentOfAvailFunds > 0));
        LOG.debug("\tSL price is: " + slPrice.toString());
        assert (execPrice.getAmount() != 0);
        if (execPrice.getAmount() == 0) {
            LOG.debug("Price = 0?!");
            return 0;
        }

        // long orders should have a stop loss less than the execprice,
        // short orders the reverse
        // NB. percentOfAvailFunds is not zero given check above
        boolean checkSL = (percentOfAvailFunds > 0 && slPrice.lessThan(execPrice))
        || (percentOfAvailFunds < 0 && slPrice.greaterThan(execPrice));
        assert (checkSL);

        Balance availFunds = availableFunds();
        LOG.debug("\tAvail funds is: " + availFunds.toString());
        Balance riskFunds = availFunds.multiply(percentOfAvailFunds);
        LOG.debug("\tRisk funds is: " + riskFunds.toString());
        Price risk = execPrice.subtract(slPrice).convert(riskFunds.getCcy());
        LOG.debug("\tPer unit risk is: " + risk.toString());
        int units = (int) Math.floor(riskFunds.divide(Math.abs(risk.getAmount())).getAmount());
        LOG.debug(String.format("\tUnits are: %1$d", units));

        boolean checkUnits = ((percentOfAvailFunds < 0 && units < 0) || (percentOfAvailFunds > 0 && units > 0));
        //assert (checkUnits);
        if (!checkSL || !checkUnits) {
            LOG.error(String.format("%1$s -> Cancelling order size due to error: "
                    + "Execution price = %2$6f Stop loss = %3$6f Percent of funds = %4$%}",
                    m_uniqueId, execPrice.getAmount(), slPrice.getAmount(), percentOfAvailFunds * 100));
            return 0;
        }

        return units;
    }

    // / <summary>
    // / Executes a market order using the long security
    // / </summary>
    // / <param name="units">Number of units to order</param>
    private MarketOrder executeMarketOrder(int units) {
        TickPrice price = ExecEnviron.getPriceTable().getPrice(m_security);
        Price sl = m_stopMover.calculateSL(price, (units > 0));
        Price tp = m_stopMover.calculateTP(price, (units > 0));

        return executeMarketOrder(units, sl, tp);
    }

    // / <summary>
    // / Executes a market order using the long security
    // / </summary>
    // / <param name="units">Number of units to order</param>
    // / <param name="slPrice">The StopLoss price, or double.MinusOne for no
    // SL</param>
    // / <param name="tpPrice">The TakeProfit price, or double.MinusOne for no
    // TP</param>
    private MarketOrder executeMarketOrder(int units, Price slPrice, Price tpPrice) {
        LOG.info(String.format("%1$s -> Executing MO: %2$d %3$s, SL = %4$s, TP = %5$s", 
                m_uniqueId, units, m_security, slPrice, tpPrice));

        MarketOrder mo = new MsgMarketOrder();
        mo.setSecurity(m_security);
        if (slPrice.getAmount() > 0)
            mo.setStopLoss(slPrice);
        if (tpPrice.getAmount() > 0)
            mo.setTakeProfit(tpPrice);
        mo.setUnits(units);

        if (idiotProof(mo)) {
            m_backOffice.recordExecuted(mo, m_uniqueId);
            Transaction tr = ExecEnviron.execute(mo, m_openingAccountId);

            if (tr != null) {

                if (tr.getSubtype().equals(Transaction.Code.Order) && 
                        tr.getTransactionNumber() != null && 
                        tr.getTransactionNumber() != "") {
                    mo.setOrderNumber(tr.getTransactionNumber());
                    mo.setPrice(tr.getPrice());

                    m_backOffice.recordTransacted(tr, m_uniqueId);
                    LOG.info(String.format("%1$s -> SENT MO %2$s", m_uniqueId, mo.toString()));
                }
                else {
                    LOG.error("Reply transaction has unidentifiable trans number or was not ordered: " + tr.toString());
                    mo = null;
                }
            }
            else {
                LOG.error("No reply received.  Order will probably be placed, but set flag and stop trading");
                m_wasNullReplyLast = true;
                mo = null;
                //TODO: what to tell the back office?
            }
        } else {
            LOG.error(String.format("%1$s -> attempted send failed (idiot proof): %2$s",
                    m_uniqueId, mo.toString()));
            mo = null;
        }

        return mo;
    }

//    private Message sendReplyPaid(Message msg) {
//        Message reply = ExecEnviron.sendReplyPaid(msg);
//
//        if (reply != null) {
//            LOG.debug("Msg sent: " + msg.toString());
//        }
//        else {
//            LOG.error("No reply to message: " + msg.toString());
//            m_failedMsg = msg;
//        }
//
//        return reply;
//    }

    // / <summary>
    // / Assumes everything is OK, running a check for negatives
    // / </summary>
    // / <param name="order">The order</param>
    // / <returns>True, if ok</returns>
    private static boolean idiotProof(MarketOrder order) {
        boolean isOk = true;

        // can't check order.Price because it may not have been priced yet
        if (order.getUnits() == 0)
            isOk = false;
        else if (order.getUnits() > 0)
            isOk = (order.getStopLoss().lessThanOrEqualTo(order.getTakeProfit()));
        else if (order.getUnits() < 0)
            isOk = (order.getStopLoss().greaterThanOrEqualTo(order.getTakeProfit()));

        return isOk;
    }

    // / <summary>
    // / Use this method to do any heavy duty calcs between bars, rather than
    // / delaying other agents
    // / </summary>
    private void gruntBetweenBars() {
        // TODO: update the backtest result
        // TODO: update the fund manager members
    }

    public void closeAllPositions() {
        LOG.info(String.format("%1$s -> CLOSE ALL POSITIONS called...", m_uniqueId));
        if (m_openMarketOrder != null) {
            m_backOffice.recordClosed(m_openMarketOrder, m_uniqueId);
            
            Transaction tr = ExecEnviron.close(m_openMarketOrder, m_openingAccountId);

            m_backOffice.recordTransacted(tr, m_uniqueId);

            LOG.info(String.format("%1$s -> MO %2$s closed", m_uniqueId, m_openMarketOrder.toString()));
            m_openMarketOrder = null;
        }
        LOG.info(String.format("%1$s -> All positions closed.", m_uniqueId));
    }

    public Agent clone() {
        Agent a = new Agent();

        a.m_signalProvider = m_signalProvider.clone();
        a.m_posnSizer = m_posnSizer.clone();
        a.m_stopMover = m_stopMover.clone();

        return a;
    }


    //    public void update(Observable o, Object arg) {
    //        if (!(o instanceof TimedBot))
    //            return;
    //
    //        TimedBot tb = (TimedBot) o;
    //        if (tb.getCurrentStatus().equals(TimedBot.Status.STARTING)) {
    //            HashMap<String, ? extends Account> accs = MARKET.getAccounts();
    //            Account acct = accs.get(m_openingAccountId);
    //
    //            if (acct != null) {
    //                setAccount(acct);
    //            } else {
    //                LOG.error(String.format("%1$s -> No account to trade. Excluded.", m_uniqueId));
    //                for (Account a : accs.values()) {
    //                    LOG.debug(String.format("\tAccount '%1$s' with ID '%2$s' is avail", 
    //                            a.getAccountName(), a.getAccountId()));
    //                }
    //            }
    //        }
    //        else if (tb.getCurrentStatus().equals(TimedBot.Status.STOPPING)) {
    //            try {
    //                closeAllPositions();
    //            } catch (GatewayException e1) {
    //                MARKET.getErrorHandler().handleError(
    //                        this, e1,
    //                        String.format("%1$s -> Gateway failure while closing positions", m_uniqueId));
    //            } catch (Exception e2) {
    //                LOG.error(String.format("%1$s -> System exception while closing positions", m_uniqueId), e2);
    //            }
    //        }
    //    }
    //
    //    public void subscribe(TimedBot tb) {
    //        m_tradeInterval = tb.getCycleTime();
    //        tb.scheduleWorkRun(new Runnable() {
    //            public void run() {
    //                if (m_account == null) {
    //                    LOG.debug(String.format("%1$s -> No Account set", m_uniqueId));
    //                    return;
    //                }
    //                if (ExecEnviron.isTradingBlackout()) {
    //                    LOG.info(String.format("%1$s -> Trading blackout - not trading", m_uniqueId));
    //                    return;
    //                }
    //
    //                try {
    //                    Order o = trade();
    //                    if (o != null) {
    //                        // TODO: do some back office compliance?
    //                    }
    //                } catch (GatewayException e1) {
    //                    MARKET.getErrorHandler().handleError(
    //                            this, e1,
    //                            String.format("Gateway failure while executing Agent '%1$s' on Sec: %1$s",
    //                                    m_uniqueId, m_security.getTicker()));
    //                } catch (Exception e2) {
    //                    LOG.error(String.format("%1$s -> System exception while executing", m_uniqueId), e2);
    //                }
    //            }
    //            
    //            public String toString() {
    //        	return String.format("Agent '%1$s' work run", m_uniqueId);
    //            }
    //        });
    //
    //        tb.scheduleGruntRun(new Runnable() {
    //            public void run() {
    //                try {
    //                    gruntBetweenBars();
    //                } catch (GatewayException e1) {
    //                    MARKET.getErrorHandler().handleError(
    //                            this, e1, String.format(
    //                                    "Gateway failure while grunting Agent '%1$s' on Sec: %2$s",
    //                                    m_uniqueId, m_security.getTicker()));
    //                } catch (Exception e2) {
    //                    LOG.error(String.format("%1$s -> System exception while grunting", m_uniqueId), e2);
    //                }
    //            }
    //            
    //            public String toString() {
    //        	return String.format("Agent '%1$s' grunt run", m_uniqueId);
    //            }
    //        });
    //        
    //        tb.scheduleEndOfSessionRun(new Runnable() {
    //            public void run() {
    //                try {
    //                    closeAllPositions();
    //                } catch (GatewayException e1) {
    //                    MARKET.getErrorHandler().handleError(
    //                            this, e1, String.format(
    //                                    "Gateway failure while closing positions for Agent '%1$s' on Sec: %2$s",
    //                                    m_uniqueId, m_security.getTicker()));
    //                } catch (Exception e2) {
    //                    LOG.error(String.format("%1$s -> System exception while closing positions", m_uniqueId), e2);
    //                }
    //            }
    //            
    //            public String toString() {
    //        	return String.format("Agent '%1$s' end of session run", m_uniqueId);
    //            }
    //        });
    //    }
    //    
    //    private void doCycle() {
    //    	LOG.info("Starting doCycle()");
    //    	if (m_openOrder == null) {
    //    	    PriceTable pt = ExecEnviron.getPriceTable();
    //    	    HistoryPoint hp = pt.getLastHistoryPoint(m_sec, m_ival);
    //    	    
    //    	    if (m_random.nextBoolean()) {
    //        		LOG.info("Opening an order...");
    //        		//buy
    //        		m_openOrder = new MarketOrder();
    //        		m_openOrder.setPair(new FXPair("AUD/USD"));
    //        		m_openOrder.setUnits(1000);
    //        		m_openOrder.setStopLoss(new StopLossOrder(m_currentAUDUSD * 0.95));
    //        		m_openOrder.setTakeProfit(new TakeProfitOrder(m_currentAUDUSD * 1.05));
    //
    //        		Message msg = new Message(Message.Action.OpenOrder, writeOpenOrder(), Message.Priority.Immediate);
    //        		if (m_sink.send(msg))
    //        		    LOG.debug("Open order msg sent: " + msg.toString());
    //        		else
    //        		    LOG.error("Open order sent msg not accepted: " + msg.toString());
    //    	    } 
    //    	    //else do nothing
    //    	}
    //    	else {
    //    	    if (m_openOrderNum == 0)
    //    		LOG.debug("Open OrderNum has not yet been fed back to Tester!");
    //    	    else if (m_random.nextBoolean()) {
    //    		//modify
    //    		LOG.info("Modifying open order...");
    //    		if (m_currentAUDUSD > m_openOrder.getPrice())
    //    		    m_openOrder.setStopLoss(new StopLossOrder(m_currentAUDUSD * 0.95));
    //    		else
    //    		    m_openOrder.setTakeProfit(new TakeProfitOrder(m_currentAUDUSD * 1.05));
    //
    //    		Message msg = new Message(Message.Action.ModifyOrder, writeOpenOrder(), Message.Priority.Immediate);
    //    		if (m_sink.send(msg))
    //    		    LOG.debug("Modify order msg sent: " + msg.toString());
    //    		else
    //    		    LOG.error("Modify order sent msg not accepted: " + msg.toString());
    //    	    }
    //    	    else {
    //    		//sell
    //    		LOG.info("Closing open order...");
    //    		
    //    		Message msg = new Message(Message.Action.CloseOrder, writeOpenOrder(), Message.Priority.Immediate);
    //    		if (m_sink.send(msg)) {
    //    		    LOG.debug("Close order msg sent: " + msg.toString());
    //    		    m_openOrder = null;
    //    		    m_openOrderNum = 0;
    //    		}
    //    		else {
    //    		    LOG.error("Close order sent msg not accepted: " + msg.toString());
    //    		}
    //    	    }
    //    	}
    //    	LOG.info("Finishing doCycle()");
    //    }



    //    private void updateOrderNum(Message conf) {
    //	if (conf.getAction().equals(Message.Action.OpenOrderConfirmation)) {
    //	    LOG.debug("OrderConf received for order: " + conf.getData());
    //	    m_openOrderNum = Integer.parseInt(conf.getData());
    //	}
    //    }
    //    
    //    private void updatePrice(Message priceUpdate) {
    //    	if (priceUpdate.getAction().equals(Message.Action.PriceUpdate)) {
    //    //	    String reStr = String.format("%1$TF %1$TT,%2$s,%3$6.5f,%4$6.5f", fxr.getTimestamp(), fxr.getPair(),
    //    //		    fxr.getTick().getBid(), fxr.getTick().getAsk());
    //    	    LOG.debug("Price update received: " + priceUpdate.getData());
    //    	    String[] parts = priceUpdate.getData().split("\t");
    //    	    if (parts.length == 4 && parts[1].equals("AUD/USD")) {
    //        		double bid = Double.parseDouble(parts[2]);
    //        		double ask = Double.parseDouble(parts[3]);
    //        		double mid = (bid + ask) / 2.0;
    //        		m_currentAUDUSD = mid;
    //            }
    //            else {
    //        	    LOG.debug("Price update wrong len or not AUD/USD: " + priceUpdate.toString());
    //            }
    //	    }
    //    }
    //    
    //    private static Agent AGENT;


}
