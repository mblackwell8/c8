package c8;

import c8.init.*;
import c8.trading.Agent;
import c8.gateway.*;

import java.io.*;
import java.util.*;

import javax.xml.parsers.FactoryConfigurationError;

import org.tanukisoftware.wrapper.WrapperManager;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.xml.DOMConfigurator;

public final class Trader implements Runnable {


    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java c8.Trader [options] xmlConfigFormatFile");
            return;
        }

        PrintStream stdout = null;
        PrintStream stderr = null;
        boolean runAsService = false;
        String xmlSettingsFileName = args[args.length - 1];
        int cycleMs = 0;

        try {
            Options opt = new Options();

            opt.addOption("o", true, "Set STDOUT file ref");
            opt.addOption("e", true, "Set STDERR file ref");
            opt.addOption("s", false, "Run as a service");
            opt.addOption("l", true, "Set Log4J XML config file ref");
            opt.addOption("c", true, "Cycle millis");

            BasicParser parser = new BasicParser();
            CommandLine cl = parser.parse(opt, args);

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

            if (cl.hasOption('c')) {
                cycleMs = Integer.parseInt(cl.getOptionValue('c'));
            } else {
                //default to 5 mins
                cycleMs = 300000;
            }

            runAsService = cl.hasOption('s');
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }

        if (stdout != null)
            System.setOut(stdout);
        if (stderr != null)
            System.setErr(stderr);

        C8Settings settings;
        try {
            System.out.print("Configuring JC8...\n");
            settings = C8SettingsParser.parse(xmlSettingsFileName);
            if (settings == null) {
                System.err.println("Error while parsing settings file");
                return;
            }
            System.out.print("Done!\n");
            //might want to ping the ExecEnviron at this point??

        } catch (RuntimeException e) {
            System.err.println("Error while setting up C8: " + e.toString());
            return;
        }

        System.out.println("Starting ExecEnviron...");
        ExecEnviron.start();

        System.out.println("Starting OANDA client...");
        OandaClient client = new OandaClient(true);

        MessagePasser msgPasserToOANDA = new MessagePasser("Trader-->OANDA");
        MessagePasser msgPasserFromOANDA = new MessagePasser("OANDA-->Trader");

        System.out.println("Setting client message source as MessagePasser type");
        client.setMessageSource(msgPasserToOANDA);
        System.out.println("Setting client message sink as MessagePasser type");
        client.setMessageSink(msgPasserFromOANDA);

        System.out.println("Setting ExecEnviron message source as MessagePasser type");
        ExecEnviron.current.setMsgSource(msgPasserFromOANDA);
        System.out.println("Setting ExecEnviron message sink as MessagePasser type");
        ExecEnviron.current.setMsgSink(msgPasserToOANDA);

//        System.out.println("Setting ExecEnviron message source as OandaClient");
//        ExecEnviron.current.setMsgSource(client);
//        System.out.println("Setting ExecEnviron message sink as MessagePasser type");
//        ExecEnviron.current.setMsgSink(client);

        Thread clientThread = new Thread(client, "OandaClient");
        System.out.println("OANDA client will run on thread " + clientThread.getName());
        clientThread.start();

        //eventually replace this with an OK message from client
        try {
            System.out.println("Sleeping for twenty seconds while OANDA starts up");
            Thread.sleep(20000);
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        Trader t = new Trader(cycleMs, settings.getAgents().values());


        try {
            if (runAsService) {
                //                // Start the application. If the JVM was launched from the
                //                // native
                //                // Wrapper then the application will wait for the native Wrapper
                //                // to
                //                // call the application's start method. Otherwise the start
                //                // method
                //                // will be called immediately.
                //
                //                // HACK: not using any args... could send these, maybe?
                //                // HACK: where will STDOUT and STDERR go?
                //                WrapperManager.start(new C8WrapperListener(settings.getBot()),
                //                        null);
            } else {
                t.run();
            }
        } finally {
            // flush stdout and stderr, given they might be going to files
            System.out.flush();
            System.err.flush();
        }
    }

    private Collection<Agent> m_agents = new LinkedList<Agent>();

    //  default to 5 mins
    private int m_cycleMillis = 300000;

    public Trader() { }

    public Trader(int cycleMillis) {
        m_cycleMillis = cycleMillis;
    }

    public Trader(int cycleMillis, Collection<Agent> agents) {
        m_cycleMillis = cycleMillis;
        m_agents.addAll(agents);
    }

    public Collection<Agent> getAgents() {
        return m_agents;
    }

    public void run() {
        System.out.println("Registering price feeds...");
        for (Agent a : m_agents) {
            System.out.println("Requesting feed for: " + a.getSecurity());
            Message m = new Message(Message.Action.PriceSubscriptionRequest, a.getSecurity().toString(), Message.Priority.Immediate);
            if (!ExecEnviron.current.send(m)) {
                System.err.println("Feed request failed for agent: " + a.getName());
            }
        }
        
        System.out.println("Closing all existing positions...");
        for (Agent a : m_agents) {
            System.out.println("Closing positions on account: " + a.getOpeningAccountId());
            Message m = new Message(Message.Action.CloseAllPositions, a.getOpeningAccountId(), Message.Priority.Immediate);
            if (!ExecEnviron.current.send(m)) {
                System.err.println("Close all positions failed for: " + a.getName());
            }
        }

        System.out.println("Starting Trader...");

        Timer sendCycle = new Timer("Trader");
        sendCycle.scheduleAtFixedRate(new TimerTask() { 
            public void run() {
                for (Agent a : m_agents) {
                    try {
                        a.trade();
                    }
                    catch (RuntimeException e) {
                        System.err.println("Exception: " + e.toString());
                        e.printStackTrace();
                    }
                }
            }
        }, 0, m_cycleMillis);

        System.out.println("Trader finished.");
    }

}
