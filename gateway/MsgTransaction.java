package c8.gateway;

import c8.ExecEnviron;
import c8.trading.Transaction;
import c8.util.*;

public class MsgTransaction implements Transaction {

    //private static int NEXTID = 0;

    private long m_timestamp;

    private String m_internalOrderId = "na";

    private String m_transactionNumber;

    private Security m_security;

    private long m_units;

    private Price m_price;

    private Price m_stopLossPrice;

    private Price m_takeProfitPrice;

    private String m_linkedTransNum;

    private Balance m_postBalance;

    private int m_completionCode;

    public MsgTransaction(String internalOrderId, String transNumber, String linkedTransNumber,
            Security sec, long units, Price price, Price sl, Price tp, Balance balAfter, int completionCode) {
        m_internalOrderId = internalOrderId;
        //	m_id = Integer.toString(NEXTID++);
        m_timestamp = ExecEnviron.time();
        m_transactionNumber = transNumber;
        m_linkedTransNum = linkedTransNumber;
        m_security = sec;
        m_units = units;
        m_price = price;
        m_stopLossPrice = sl;
        m_takeProfitPrice = tp;
        m_postBalance = balAfter;
        m_completionCode = completionCode;
    }

    public long getTimeStamp() {
        return m_timestamp;
    }

    public Balance getBalanceAfter() {
        return m_postBalance;
    }

    public String getLinkedTransactionNumber() {
        return m_linkedTransNum;
    }

    public Price getPrice() {
        return m_price;
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

    //    public String getId() {
    //        return m_id;
    //    }

    public String getInternalOrderId() {
        return m_internalOrderId;
    }

    public String getTransactionNumber() {
        return m_transactionNumber;
    }

    public long getUnits() {
        return m_units;
    }

    public boolean isBuy() {
        return m_units > 0;
    }

    public boolean isSell() {
        return m_units < 0;
    }

    /*************************************
     **                                  **
     ** TRANSACTIONS COMPLETION CODES:   **
     ** 100 Normal open or close         **
     **     long or short                **
     ** 102 Stop Loss                    **
     ** 103 Take Profit                  **
     ** 104 Margin Call                  **
     ** 107 Interests                    **
     ** 109 Manual Change to account     **
     **     e.g. funds, P&L reset        **
     **                                  **
     **************************************/

    public Transaction.Code getSubtype() {
        switch (m_completionCode) {
        case 100:
            return Transaction.Code.Order;
        case 102:
            return Transaction.Code.StopLoss;
        case 103:
            return Transaction.Code.TakeProfit;
        case 104:
            return Transaction.Code.MarginCall;
        case 107:
            return Transaction.Code.Interest;
        case 109:
            return Transaction.Code.ManualChange;
        }

        return Transaction.Code.Unknown;
    }

    public String toString() {
        return String.format("MsgTransaction: %1$s %2$d %3$s at %4$s - Transacted as %5$s", 
                (this.isBuy() ? "Long" : "Short"), this.getUnits(), this.getSecurity().getTicker(), this.getPrice().toString(), m_transactionNumber);
    }

}
