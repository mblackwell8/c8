package c8.trading;

import org.apache.log4j.Logger;

import c8.util.Price;
import c8.util.TickPrice;

import com.oanda.fxtrade.api.*;

public class ChannelStopAdjuster implements StopAdjuster {

    private double m_slBankDistance;
    private double m_tpBankDistance;
    
    private static Logger LOG = Logger.getLogger(ChannelStopAdjuster.class);
    
    public ChannelStopAdjuster() {}
    
    public ChannelStopAdjuster(double slPercent, double tpPercent) {
	m_slBankDistance = slPercent;
	m_tpBankDistance = tpPercent;
    }
    
    public double getSLPercent() {
        return m_slBankDistance;
    }

    public void setSLPercent(double bankDistance) {
        m_slBankDistance = bankDistance;
    }

    public double getTPPercent() {
        return m_tpBankDistance;
    }

    public void setTPPercent(double bankDistance) {
        m_tpBankDistance = bankDistance;
    }
    
    
    
    public Price calculateSL(TickPrice currentPrice, boolean isLong) {
	Price sl = null;
	if (isLong) {
	    //we're buying at the ask price, so we need protection if the
	    //price goes DOWN
	    sl = currentPrice.getAskPrice().multiply(1 - m_slBankDistance);
	}
	else {
	    //we're selling at the bid price, so we need protection if the
	    //price goes UP
	    sl = currentPrice.getBidPrice().multiply(1 + m_slBankDistance);
	}
	
	return sl;
    }

    public Price calculateTP(TickPrice currentPrice, boolean isLong) {
	Price tp = null;
	if (isLong) {
	    //we're buying at the ask price, so we want to take profit
	    //if the price goes up!
	    tp = currentPrice.getAskPrice().multiply(1 + m_tpBankDistance);
	}
	else {
	    //we're selling at the bid price, so we want to take profit
	    //if the price goes down!
	    tp = currentPrice.getBidPrice().multiply(1 - m_slBankDistance);
	}
	
	return tp;
    }

    public void moveStops(MarketOrder mo, TickPrice currentPrice) {
	LOG.debug("Moving stops on MO: " + mo.getId());
	
	Price oldSL = mo.getStopLoss();
	Price oldTP = mo.getTakeProfit();
	
	mo.setStopLoss(calculateSL(currentPrice, mo.isLong()));
	mo.setTakeProfit(calculateTP(currentPrice, mo.isLong()));
	
	LOG.info(String.format("SL/TP adjusted on MO %1$s. SL from %2$s to %3$s; TP from %4$s to %5$s",
		mo.getId(), oldSL, mo.getStopLoss(), oldTP, mo.getTakeProfit()));
    }

    public boolean shouldMoveStops(MarketOrder mo, TickPrice currentPrice) {
	if (mo.getStopLoss().equals(calculateSL(currentPrice, mo.isLong()))) {
	    if (mo.getTakeProfit().equals(calculateTP(currentPrice, mo.isLong())))
		return false;
	}
	
	return true;
    }

    public String getDescription() {
	return String.format("Sets stops (SL/TP) a fixed percentage channel width SL=%1$g TP=%1$g",
		m_slBankDistance, m_tpBankDistance);
    }

    public String getName() {
	return "Channel Stop Adjuster";
    }
    
    public StopAdjuster clone() {
	return new ChannelStopAdjuster(m_slBankDistance, m_tpBankDistance);
    }



}
