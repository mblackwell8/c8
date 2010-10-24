package c8.gateway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.oanda.fxtrade.api.*;

public class Tester {
    
    //MarketOrder is just used as a convenient data structure,
    //but don't try to act on it!
    MarketOrder m_openOrder;
    int m_openOrderNum = 0;
    MessageSource m_source;
    MessageSink m_sink;
    double m_currentAUDUSD = 0.95;
    
    int m_accountId = 9650403;
    
    Random m_random = new Random();
    
    static final Logger LOG = LogManager.getLogger(Tester.class);
    
    public void doCycle() {
	LOG.info("Starting doCycle()");
	if (m_openOrder == null) {
	    if (m_random.nextBoolean()) {
		LOG.info("Opening an order...");
		//buy
		m_openOrder = new MarketOrder();
		m_openOrder.setPair(new FXPair("AUD/USD"));
		m_openOrder.setUnits(1000);
		m_openOrder.setStopLoss(new StopLossOrder(m_currentAUDUSD * 0.95));
		m_openOrder.setTakeProfit(new TakeProfitOrder(m_currentAUDUSD * 1.05));
		
		String ord = "blah";// Message.orderToString(Integer.toString(m_accountId), m_openOrder);

		Message msg = new Message(Message.Action.OpenOrder, ord, Message.Priority.Immediate);
		if (m_sink.send(msg))
		    LOG.debug("Open order msg sent: " + msg.toString());
		else
		    LOG.error("Open order sent msg not accepted: " + msg.toString());
	    }
	    //else do nothing 
	}
	else {
	    if (m_openOrderNum == 0)
		LOG.debug("Open OrderNum has not yet been fed back to Tester!");
	    else if (m_random.nextBoolean()) {
		//modify
		LOG.info("Modifying open order...");
		if (m_currentAUDUSD > m_openOrder.getPrice())
		    m_openOrder.setStopLoss(new StopLossOrder(m_currentAUDUSD * 0.95));
		else
		    m_openOrder.setTakeProfit(new TakeProfitOrder(m_currentAUDUSD * 1.05));
		
		String ord = "blah"; //Message.orderToString(Integer.toString(m_accountId), m_openOrder);

		Message msg = new Message(Message.Action.ModifyOrder, ord, Message.Priority.Immediate);
		if (m_sink.send(msg))
		    LOG.debug("Modify order msg sent: " + msg.toString());
		else
		    LOG.error("Modify order sent msg not accepted: " + msg.toString());
	    }
	    else {
		//sell
		LOG.info("Closing open order...");
		
		String ord = "blah";// Message.orderToString(Integer.toString(m_accountId), m_openOrder);
		
		Message msg = new Message(Message.Action.CloseOrder, ord, Message.Priority.Immediate);
		if (m_sink.send(msg)) {
		    LOG.debug("Close order msg sent: " + msg.toString());
		    m_openOrder = null;
		    m_openOrderNum = 0;
		}
		else {
		    LOG.error("Close order sent msg not accepted: " + msg.toString());
		}
	    }
	}
	LOG.info("Finishing doCycle()");
    }
    
//    private String writeOpenOrder() {
//	String orderStr = String.format("%1$d\t%2$d\t%3$s\t%4$d\t%5$2.2f\t%6$2.2f",
//		    m_accountId, m_openOrderNum, m_openOrder.getPair(), m_openOrder.getUnits(),
//		    m_openOrder.getStopLoss().getPrice(),
//		    m_openOrder.getTakeProfit().getPrice());
//	return orderStr;
//    }
    
    private void updateOrderNum(Message conf) {
	if (conf.getAction().equals("blah"/*Message.Action.OpenOrderConfirmation*/)) {
	    LOG.debug("OrderConf received for order: " + conf.getData());
	    m_openOrderNum = Integer.parseInt(conf.getData());
	}
    }
    
    private void updatePrice(Message priceUpdate) {
	if (priceUpdate.getAction().equals(Message.Action.PriceUpdate)) {
//	    String reStr = String.format("%1$TF %1$TT,%2$s,%3$6.5f,%4$6.5f", fxr.getTimestamp(), fxr.getPair(),
//		    fxr.getTick().getBid(), fxr.getTick().getAsk());
	    //LOG.debug("Price update received: " + priceUpdate.getData());
	    String[] parts = priceUpdate.getData().split("\t");
	    if (parts.length == 4 && parts[1].equals("AUD/USD")) {
		double bid = Double.parseDouble(parts[2]);
		double ask = Double.parseDouble(parts[3]);
		double mid = (bid + ask) / 2.0;
		m_currentAUDUSD = mid;
		LOG.debug("Price update received: " + priceUpdate.getData());
	    }
	    else {
		//LOG.debug("Price update wrong len or not AUD/USD: " + priceUpdate.toString());
	    }
	}
    }
    
    private static Tester TESTER;

    /**
     * @param args
     */
    public static void main(String[] args) {
	int cycleMs = 60000;
	int initDelay = 5000;
	
	DOMConfigurator.configure("log4j_test.xml");
	
	TESTER = new Tester();
	
//	try {
//	    TESTER.m_source = new FileSource(recvFilename);
//	    TESTER.m_sink = new FileSink(sendFilename);
//	    
//	} catch (IOException e1) {
//	    System.err.println("IO error on file source/sink settings");
//	    return;
//	}
	
	TESTER.m_source = new DbMessageSource();
	TESTER.m_sink = new DbMessageSink();
	
	Timer sendCycle = new Timer("Sender");
	sendCycle.scheduleAtFixedRate(new TimerTask() { 
	    public void run() {
		try {
		    TESTER.doCycle();
		}
		catch (RuntimeException e) {
		    LOG.error("Exception: ", e);
		}
	    }
	}, initDelay, cycleMs);

	Timer recvCycle = new Timer("Receiver");
	recvCycle.scheduleAtFixedRate(new TimerTask() { 
	    public void run() {
		try {
		    Message recd = null;
		    while ((recd = TESTER.m_source.getNext()) != null) {
			switch (recd.getAction()) {
			case PriceUpdate:
			    TESTER.updatePrice(recd);
			    break;
//			case OpenOrderConfirmation:
//			    TESTER.updateOrderNum(recd);
//			    break;
			default:
			    LOG.debug("Message recd (ignored): " + recd.toString());
			}
		    }
		}
		catch (RuntimeException e) {
		    LOG.error("Exception: ", e);
		}
	    }
	}, initDelay + cycleMs / 10, cycleMs / 10);
	
	BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	while (true) {
	    try {
		System.out.print("\nHit Enter three times to exit...\n\n");
		int enterCount = 0;
		
		while (enterCount++ < 3) {
		    in.readLine();
		}
		
		recvCycle.cancel();
		sendCycle.cancel();
		System.out.println("Exiting. Goodbye.");

		break;
	    } catch (IOException e) {
		System.err.println("Exception: " + e.toString());
	    }
	}
    }
    
    
    

}
