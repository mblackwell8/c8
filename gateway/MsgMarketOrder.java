package c8.gateway;

import c8.ExecEnviron;
import c8.trading.MarketOrder;
import c8.trading.PriceTable;
import c8.util.*;

public class MsgMarketOrder implements MarketOrder {

    private static int NEXTID = 0;

    static String EMPTY_ORDER_NUMBER = "na";

    private long m_timeStamp = 0;

    private String m_id = EMPTY_ORDER_NUMBER;

    private String m_orderNumber = EMPTY_ORDER_NUMBER;

    private Security m_security = Security.Empty;

    private long m_units = 0;

    private Price m_price = Price.Zero;

    private Price m_stopLossPrice = Price.Zero;

    private Price m_takeProfitPrice = Price.Zero;

    private Balance m_profitOnSale;

    //private int m_accountId;

    public MsgMarketOrder() {
        m_id = Integer.toString(NEXTID++);
        m_timeStamp = ExecEnviron.time();
    }

    public String getId() {
        return m_id;
    }

    void setId(String id) {
        m_id = id;
    }

    public String getOrderNumber() {
        return m_orderNumber;
    }

    public void setOrderNumber(String number) {
        m_orderNumber = number;
    }

    public Price getPrice() {
        return m_price;
    }

    public void setPrice(Price p) {
        m_price = p;
    }

    public Security getSecurity() {
        return m_security;
    }

    public Price getStopLoss() {
        return m_stopLossPrice;
    }

    public Price getTakeProfit() {
        return m_takeProfitPrice;
    }

    public long getUnits() {
        return m_units;
    }

    public boolean isLong() {
        return m_units > 0;
    }

    public boolean isShort() {
        return m_units < 0;
    }

    public void setSecurity(Security s) {
        m_security = s;
    }

    public void setStopLoss(Price sl) {
        m_stopLossPrice = sl;
    }

    public void setTakeProfit(Price tp) {
        m_takeProfitPrice = tp;
    }

    public void setUnits(long units) {
        m_units = units;
    }

    public long getTimeStamp() {
        return m_timeStamp;
    }


    public MarketOrder clone() {
        MsgMarketOrder clone = new MsgMarketOrder();

        clone.m_timeStamp = m_timeStamp;
        clone.m_id = m_id;
        clone.setOrderNumber(this.getOrderNumber());
        clone.setSecurity(this.getSecurity());
        clone.setUnits(this.getUnits());
        clone.setPrice(this.getPrice());
        clone.setStopLoss(this.getStopLoss());
        clone.setTakeProfit(this.getTakeProfit());

        clone.m_profitOnSale = m_profitOnSale;

        return clone;
    }



    /**
     * Funds not protected by SL, in the home currency
     */
    public Amount fundsAtRisk() {
        Amount far = new Amount(0.0, ExecEnviron.getAccountingCcy());

        if (m_profitOnSale != null && m_profitOnSale.getAmount() != 0)
            return far;

        if (m_price != null) {
            Amount riskPerUnit = m_price.subtract(this.getStopLoss());
            far = Amount.multiply(riskPerUnit, m_units);
        }

        return far;
    }

    //    public String getTransactionNumber() {
    //        return m_transNumber;
    //    }
    //
    //    void setTransactionNumber(String num) {
    //        m_transNumber = num;
    //    }

    /**
     * Realised PL, in the home currency
     */
    public Balance realisedPL() {
        //not required in current implementation because not retaining market order in agent or BackOffice?
        return m_profitOnSale;
    }

    void setRealisedPL(Balance val) {
        m_profitOnSale = val;
    }

    /**
     * Unrealised PL, in the home currency
     */
    public Balance unrealisedPL() {
        Balance unrPL = new Balance(0.0, ExecEnviron.getAccountingCcy());

        //if price hasn't been set, then return zero
        if (m_price.equals(Price.Zero))
            return unrPL;

        //coded very defensively
        PriceTable pt = ExecEnviron.getPriceTable();
        if (pt != null) {
            TickPrice pr = pt.getPrice(m_security);
            if (pr != null) {
                Price closePr = this.isLong() ? pr.getBidPrice() : pr.getAskPrice();
                Amount unitProfit = closePr.subtract(m_price);

                //units negative for shorts, so should work
                unrPL = unrPL.add(Amount.multiply(unitProfit, m_units));
            }
        }

        return unrPL;
    }

    public String toString() {
        return String.format("MsgMarketOrder: %1$s %2$d %3$s at %4$s - Transacted as %5$s", 
                (this.isLong() ? "Long" : "Short"), this.getUnits(), this.getSecurity().getTicker(), this.getPrice().toString(), m_orderNumber);
    }

}
