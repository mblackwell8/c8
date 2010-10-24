package c8;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import c8.gateway.*;
import c8.trading.*;
import c8.util.*;

public class ExecEnviron {
    static EnvironmentServer current;
    static Properties appProperties;


    static boolean isStarted;


    private ExecEnviron() {
    }

    static {
        current = new NullEnvironmentServer();

        appProperties = new Properties();
        try {
            FileInputStream in = new FileInputStream("c8.ini");
            appProperties.load(in);
        } catch (Exception e) {
            // TODO: what to do?
            e.printStackTrace();
        }
    }

    public static EnvironmentServer start() {
        if (isStarted) {
            throw new RuntimeException("Cannot start ExecEnviron twice");
        }

        String environName = appProperties.getProperty("Market");

        if (environName.equalsIgnoreCase("Test")) {
            throw new RuntimeException();
        } else if (environName.equalsIgnoreCase("OANDA")) {
            current = new OandaEnvironmentServer();
            current.start();
            isStarted = true;
        } else {
            System.err.println("Specified ExecEnviron is not recognised: " + environName);
        }

        return current;
    }


    public static Properties c8props() {
        return appProperties;
    }

    public static boolean isTradingBlackout() {
        return isTradingBlackout(time());
    }

    public static boolean isTradingBlackout(long time) {
        return current.isTradingBlackout(time);
    }
    
    public static boolean isConnected() {
        return current.isConnected();
    }

    public static void sleepUntilNextRoundTime(Interval iv) {
        long nextRoundTime = iv.nextRoundTime();
        assert nextRoundTime > time();

        sleep(nextRoundTime - time());
    }

    public static void sleep(long ms) {
        current.sleep(ms);
    }

    public static boolean shouldStop() {
        File stopFile = new File("c8stop");
        return stopFile.exists();
    }

    public static PriceTable getPriceTable() {
        return current.getPriceTable();
    }

    public static void send(Message msg) {
        current.send(msg);
    }

//    public static Message sendReplyPaid(Message msg) {
//        //contract reply info by msg type:
//        //OpenOrder - transaction
//        //ModifyOrder - transaction
//        //CloseOrder - transaction
//        //OpenOrderConfirmation - should not be sent by client side... null
//        //TransactionUpdate - transaction
//        //PriceUpdate - price
//        //CloseAllPositions - boolean
//        //Logoff - boolean
//
//        return current.sendReplyPaid(msg);
//    }

    public static Transaction execute(MarketOrder order, String accId) {
        return current.execute(order, accId);
    }

    public static Transaction modify(MarketOrder order, String accId) {
        return current.modify(order, accId);
    }

    public static Transaction close(MarketOrder order, String accId) {
        return current.close(order, accId);
    }

    public static long time() {
        return current.getTime();
    }

    public static void setTime(long time) {
        current.setTime(time);
    }

    public static Currency getAccountingCcy() {
        return current.getAccountingCcy();
    }

    public static String getName() {
        return current.getName();
    }
}
