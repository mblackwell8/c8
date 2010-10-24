package c8.trading;

import java.util.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import c8.ExecEnviron;
import c8.util.*;
import c8.gateway.*;

public class BackOffice {

    // NAV = available + invested @ cost + unrealised PL
    // available = allocated + realised PL



    private Amount m_allocatedFunds = new Amount(0.0, DEFAULT_CCY);

    private Balance m_investedFundsAtCost = new Balance(0.0, DEFAULT_CCY);

    private Amount m_realisedPL = new Amount(0.0, DEFAULT_CCY);

    private HashMap<String, Transaction> m_executedTransactions;

    //transactions that have appeared on the server, but which the back office
    //doesn't know about
    private HashMap<String, Transaction> m_unmatchedTransactions;

    private HashMap<String, MarketOrder> m_liveMarketOrders;

    //market orders that have been sent for which we haven't
    //found a matching transaction
    private HashMap<String, MarketOrder> m_unmatchedMOs;

    //modification orders that have been sent for which we haven't
    //found a matching transaction
    private HashMap<String, MarketOrder> m_unmatchedModifications;

    //close orders that have been sent for which we haven't
    //found a matching transaction
    private HashMap<String, MarketOrder> m_unmatchedClosures;

    private HashMap<String, Transaction> m_transactionReports;

    private TimeSeries<Balance> m_historicalInvestedFundsAtCost = new TimeSeries<Balance>();
    private TimeSeries<Balance> m_historicalEquity = new TimeSeries<Balance>();

    private TradingDataStore m_dataStore;

    public static final Currency DEFAULT_CCY = ExecEnviron.getAccountingCcy();
    static final Logger LOG = LogManager.getLogger(BackOffice.class);

    public BackOffice() {
        m_executedTransactions = new HashMap<String, Transaction>();
        m_unmatchedTransactions = new HashMap<String, Transaction>();
        m_liveMarketOrders = new HashMap<String, MarketOrder>();
        m_unmatchedMOs = new HashMap<String, MarketOrder>();
        m_unmatchedModifications = new HashMap<String, MarketOrder>();
        m_unmatchedClosures = new HashMap<String, MarketOrder>();
        m_transactionReports = new HashMap<String, Transaction>();

        m_historicalInvestedFundsAtCost.add(m_investedFundsAtCost);
        m_historicalEquity.add(m_investedFundsAtCost);
    }

    public TradingDataStore getDataStore() {
        return m_dataStore;
    }

    public void setDataStore(TradingDataStore store) {
        m_dataStore = store;
    }

    public Amount getAllocatedFunds() {
        return m_allocatedFunds;
    }

    public void setAllocatedFunds(Amount amt) {
        Amount increase = Amount.subtract(amt, m_allocatedFunds);
        m_allocatedFunds = amt;


        m_historicalEquity.add(new Balance(amt.getAmount(), amt.getCcy()));

        //TODO: adjust invested funds at cost
    }

    public Balance getAvailableFunds() {
        Amount amt = Amount.subtract(m_allocatedFunds, m_investedFundsAtCost);
        Amount avail = Amount.add(amt, m_realisedPL);
        return new Balance(avail.getAmount(), avail.getCcy());
    }

    public Balance getInvestedFundsAtCost() {
        return m_investedFundsAtCost;
    }

    public TimeSeries<Balance> getHistoricalInvestedFundsAtCost() {
        return m_historicalInvestedFundsAtCost;
    }

    public void recordExecuted(MarketOrder mo, String executor) {
        //adjust invested funds etc when the transaction arrives
        if (mo == null) {
            LOG.debug("Null market order sent to recordExecuted(). Ignored.");
            return;
        }

        LOG.info(String.format("MO %1$s recorded as EXECUTED by %2$s", mo.getId(), executor));
        mo = (MarketOrder)mo.clone();

        m_liveMarketOrders.put(mo.getId(), mo);
        m_unmatchedMOs.put(mo.getId(), mo);

        updateDataStore(mo, executor, "Executed");
    }

    public void recordModified(MarketOrder mo, String modifier) {
        //	adjust invested funds etc when the transaction arrives
        if (mo == null) {
            LOG.debug("Null market order sent to recordModified(). Ignored.");
            return;
        }

        LOG.info(String.format("MO %1$s recorded as MODIFIED by %2$s", mo.getId(), modifier));

        MarketOrder localMO = m_liveMarketOrders.get(mo.getId());
        if (localMO == null) {
            LOG.error(String.format("Modified MO '%1$s' is unknown to the Back Office. Adding it now.", mo.getOrderNumber()));
            m_liveMarketOrders.put(mo.getId(), mo);
        } else {
            //only the stop loss or take profit can be altered
            localMO.setStopLoss(mo.getStopLoss());
            localMO.setTakeProfit(mo.getTakeProfit());

            //probably doesn't have the order number set yet
            LOG.debug("Current local order number: " + localMO.getOrderNumber());
            LOG.info("Setting local order number to: " + mo.getOrderNumber());
            localMO.setOrderNumber(mo.getOrderNumber());

            m_unmatchedModifications.put(localMO.getId(), localMO);

            //TODO: check if anything else altered?
        }

        updateDataStore(mo, modifier, "Modified");
    }

    public void recordClosed(MarketOrder mo, String closer) {
        //	adjust invested funds etc when the transaction arrives
        if (mo == null) {
            LOG.debug("Null market order sent to recordClosed(). Ignored.");
            return;
        }

        LOG.info(String.format("MO %1$s recorded as CLOSED by %2$s", mo.getId(), closer));

        MarketOrder localMO = m_liveMarketOrders.get(mo.getId());
        if (localMO == null) {
            LOG.error(String.format("Closed MO '%1$s' is unknown to the Back Office.", mo.getOrderNumber()));
        } else {
            //	    if it wasn't ever modified it probably doesn't have the order number set yet
            LOG.debug("Current local order number: " + localMO.getOrderNumber());
            LOG.info("Setting local order number to: " + mo.getOrderNumber());
            localMO.setOrderNumber(mo.getOrderNumber());

            m_liveMarketOrders.remove(mo.getId());
            m_unmatchedClosures.put(localMO.getId(), localMO);
        }

        updateDataStore(mo, closer, "Closed");
    }

    public void recordTransacted(Transaction t, String transactor) {

        if (t == null) {
            LOG.debug("Null transaction sent to recordTransacted(). Ignored.");
            return;
        }

        LOG.info(String.format("Transaction %1$s recorded by %2$s", t.getTransactionNumber(), transactor));

        m_transactionReports.put(t.getTransactionNumber(), t);

        if (!t.getSubtype().equals(Transaction.Code.Order) &&
                !t.getSubtype().equals(Transaction.Code.TakeProfit) && 
                !t.getSubtype().equals(Transaction.Code.StopLoss)) {

            //it could be interest or a margin call (eek!)... whatever, it shouldn't effect
            //this agent
            LOG.info(String.format("Ignored '%1$s' transaction: %2$s", t.getSubtype(), t.getTransactionNumber()));
            return;
        }


        MarketOrder match = null;
        //transactions from market orders have the same ticket number
        if ((match = m_unmatchedMOs.get(t.getInternalOrderId())) != null) {
            LOG.info(String.format("Transaction %1$s recorded against MO OPEN %2$s", 
                    t.getTransactionNumber(), match.getId()));
            LOG.info(String.format("\tOrder price = %1$s, Fill price = %2$s", match.getPrice(), t.getPrice()));

            Amount investedAmt = t.getPrice().multiply(t.getUnits());
            LOG.info("\tInvested = " + investedAmt.toString());

            LOG.debug("\tInvested funds prior = " + m_investedFundsAtCost.toString());
            m_investedFundsAtCost = m_investedFundsAtCost.add(investedAmt);
            LOG.debug("\tInvested funds post = " + m_investedFundsAtCost.toString());
            m_historicalInvestedFundsAtCost.add(m_investedFundsAtCost);

            if (m_dataStore != null)
                m_dataStore.writeInvestedFunds(transactor, m_investedFundsAtCost, "");

            m_executedTransactions.put(t.getTransactionNumber(), t);

            if (m_dataStore != null)
                m_dataStore.write(t, transactor, "Executed");

            m_unmatchedMOs.remove(match.getId());
        } 
        else if ((match = m_unmatchedModifications.get(t.getInternalOrderId())) != null) {
            LOG.info(String.format("Transaction %1$s recorded against MO MODIFICATION %2$s", 
                    t.getTransactionNumber(), match.getId()));

            m_executedTransactions.put(t.getTransactionNumber(), t);

            if (m_dataStore != null)
                m_dataStore.write(t, transactor, "Modified");

            m_unmatchedModifications.remove(match.getId());
        }
        else if ((match = m_unmatchedClosures.get(t.getInternalOrderId())) != null) {
            LOG.info(String.format("Transaction %1$s recorded against MO CLOSURE %2$s", 
                    t.getTransactionNumber(), match.getId()));

            accountForClosure(match, t);

            if (m_dataStore != null)
                m_dataStore.writeInvestedFunds(transactor, m_investedFundsAtCost, "");

            m_executedTransactions.put(t.getTransactionNumber(), t);

            if (m_dataStore != null)
                m_dataStore.write(t, transactor, "Closed");

            m_unmatchedClosures.remove(match.getId());

        } 
        //check whether the unmatched transactions relate to a SL/TP
        else if ((match = m_liveMarketOrders.get(t.getInternalOrderId())) != null) {
            LOG.info(String.format("Transaction %1$s recorded against MO LIVE ORDER (SL/TP) %2$s", 
                    t.getTransactionNumber(), match.getId()));

            //must've been stopped out
            accountForClosure(match, t);

            if (m_dataStore != null)
                m_dataStore.write(match, transactor, "SL/TP");

            m_liveMarketOrders.remove(match.getId());
        }
        else {
            LOG.info(String.format("Transaction %1$s not matched!", 
                    t.getTransactionNumber()));

            //we don't know where this transaction has come from
            m_unmatchedTransactions.put(t.getTransactionNumber(), t);
        }
    }

    private void accountForClosure(MarketOrder match, Transaction t) {
        //need to find the value of the original transaction and
        //subtract that from the invested funds at cost
        if (!t.getLinkedTransactionNumber().equalsIgnoreCase(match.getOrderNumber())) {
            LOG.debug(String.format("Transaction %1$s has linked trans number %2$s, which does not match order num %3$s",
                    t.getTransactionNumber(), t.getLinkedTransactionNumber(), match.getOrderNumber()));
        }

        //Transaction openingTrans = m_executedTransactions.get(match.getOrderNumber());
        Transaction openingTrans = m_executedTransactions.get(t.getLinkedTransactionNumber());
        if (openingTrans != null) {
            Amount investedAmt = openingTrans.getPrice().multiply(openingTrans.getUnits());
            m_investedFundsAtCost = m_investedFundsAtCost.subtract(investedAmt);
            m_historicalInvestedFundsAtCost.add(m_investedFundsAtCost);

            Amount closeAmt = t.getPrice().multiply(t.getUnits());
            Amount pl = Amount.subtract(closeAmt, investedAmt);
            m_realisedPL = Amount.add(m_realisedPL, pl);

            LOG.info(String.format("MO %1$s closed, PL = %2$s, Current Total PL = %3$s, Avail funds = %4$s",
                    match.getOrderNumber(), pl, m_realisedPL, getAvailableFunds()));
            LOG.debug("\tInvested = " + investedAmt.toString());
            LOG.debug("\tClosed = " + closeAmt.toString());


        } else {
            LOG.error(String.format("Could not find opening transaction associated with closing transaction" +
                    "'%1$s'. Invested funds at cost not properly debited", t.getTransactionNumber()));
        }
    }

    private void updateDataStore(MarketOrder mo, String updater, String comment) {
        if (m_dataStore != null)
            m_dataStore.write(mo, updater, comment);
    }

    public void logReconciliationErrors() {
        //	print out any unmatched orders or transactions
        for (MarketOrder mo : m_unmatchedMOs.values()) {
            LOG.info(String.format("Rec error: Executed MO '%1$s' not matched", mo.getId()));
        }
        for (MarketOrder mo : m_unmatchedModifications.values()) {
            LOG.info(String.format("Rec error: Modified MO '%1$s' not matched", mo.getId()));
        }
        for (MarketOrder mo : m_unmatchedClosures.values()) {
            LOG.info(String.format("Rec error: Close MO '%1$s' not matched", mo.getId()));
        }
        for (Transaction t : m_unmatchedTransactions.values()) {
            //if (!unmatchedTrans_reportedAlready.contains(t.getTransactionNumber()))
            LOG.info(String.format("Rec error: Found TRANS '%1$s' on server not matched", t.getTransactionNumber()));
        }
    }


}
