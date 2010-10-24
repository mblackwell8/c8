package c8.gateway;

import java.util.Vector;

import com.oanda.fxtrade.api.Account;
import com.oanda.fxtrade.api.FXGame;
import com.oanda.fxtrade.api.FXClient;

import com.oanda.fxtrade.api.FXPair;
import com.oanda.fxtrade.api.MarketOrder;
import com.oanda.fxtrade.api.OAException;
import com.oanda.fxtrade.api.StopLossOrder;
import com.oanda.fxtrade.api.User;
public final class Example1 extends Thread {
private Example1() {
	super();
}
public static void main(String[] args) throws Exception {
	FXClient fxclient  = new FXGame();
	
	String username = args[0];
	String password = args[1];
	
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
		sleep(5000);
	}
	User me = fxclient.getUser();
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

	//MONITOR MY ACCOUNT AND DISPLAY ACCOUNT INFO EVERY 5 SECONDS
	while (true) {
		Vector trades = new Vector();
		try {
			trades = myaccount.getTrades();
		}
		catch (OAException oe) {
			System.out.println("Example: caught: " + oe);
			sleep(1000);
			continue;
		}
		
		System.out.println("CURRENT TRADES:");
		for (int i = 0; i < trades.size(); i++) {
			System.out.println(trades.elementAt(i));
		}
		System.out.println("CURRENT ACCOUNT INFO:");
		System.out.println("Balance: " + myaccount.getBalance());
		System.out.println("Realized PL: " + myaccount.getRealizedPL());
		System.out.println("Unrealized PL: " + myaccount.getUnrealizedPL());
		System.out.println("MarginUsed: " + myaccount.getMarginUsed());
		System.out.println("MarginAvailable: " + myaccount.getMarginAvailable());
		sleep(5000);
	}
	
}
}
