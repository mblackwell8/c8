package c8.init;

import c8.trading.*;
import c8.util.*;
import java.io.*;
import java.util.*;
import java.text.ParseException;
import java.lang.reflect.*;

import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class C8SettingsParser {
    public static C8Settings parse(String xmlConfigFile) {
        File settingsFile = new File(xmlConfigFile);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document settingsInput = null;
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            settingsInput = db.parse(settingsFile);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (settingsInput == null) {
            System.err.println("Unable to parse provided config file: "
                    + xmlConfigFile);
            return null;
        }

        C8Settings settingsOutput = new C8Settings();

        if (!configAgents(settingsInput, settingsOutput))
            return null;
        
        return settingsOutput;
    }

 
    private static boolean configAgents(Document settingsInput,
            C8Settings settingsOutput) {
        NodeList agentNodes = settingsInput.getElementsByTagName("Agent");
        if (agentNodes.getLength() == 0)
            return true;

        // Class<?> agentCheckClass;
        // Class<?> sigCheckClass;
        // Class<?> stopCheckClass;
        // Class<?> sizerCheckClass;
        // try {
        // agentCheckClass = Class.forName("Agent");
        // sigCheckClass = Class.forName("SignalProvider");
        // stopCheckClass = Class.forName("StopAdjuster");
        // sizerCheckClass = Class.forName("PositionSizer");
        // } catch (ClassNotFoundException e) {
        // e.printStackTrace();
        // System.err.println("Could not find
        // Agent/SignalProvider/StopAdjuster/PositionSizer classes! No Agent.");
        // return false;
        // }

        for (int i = 0; i < agentNodes.getLength(); i++) {
            Element agentElem = (Element) agentNodes.item(i);
            String agentUniqueName = agentElem.getAttribute("UniqueName");
            if (agentUniqueName == null || agentUniqueName == "") {
                System.err.println(String.format(
                        "Ignored Agent at node %1$d because it does not have a UniqueName", i));
                continue;
            }

            Agent agent = new Agent();

            agent.setUniqueName(agentUniqueName);
            if (settingsOutput.getAgents().containsKey(agentUniqueName)) {
                System.err.println("Duplicate Agent key found. Ignored: "
                        + agentUniqueName);
                continue;
            }

            String attr = null;
            if ((attr = agentElem.getAttribute("AccountId")) != "")
        	agent.setOpeningAccountId(attr);
            else
        	System.err.println("No account set! Failure imminent");
            
            if ((attr = agentElem.getAttribute("Float")) != "")
        	agent.setFloat(Double.valueOf(attr));
            else
        	System.err.println("Opening weight not set");
            
            if ((attr = agentElem.getAttribute("MaxAcceptableSpreadPips")) != "")
        	agent.setMaxAcceptableSpreadPips(Double.valueOf(attr));
            else
        	System.out.println("Max acceptable pips not set. Default will apply");
            
            NodeList secNodes = agentElem.getElementsByTagName("Security");
            if (secNodes.getLength() == 0) {
                System.err.println(String.format("No Security supplied for Agent '%1$s'." 
                        + "This agent will be ignored", agentUniqueName));
                continue;
            } else if (secNodes.getLength() > 1) {
                System.err.println(String.format("More than one Security supplied for Agent '%1$s'."
                        + "Only the first will be used", agentUniqueName));
            }

            Element secElem = (Element) secNodes.item(0);
            String ticker = secElem.getAttribute("Ticker");
            if (ticker == null || ticker == "") {
                System.err.println(String.format("Blank Security ticker encountered while parsing Agent '%1$s'." 
                        + "This agent will be ignored", agentUniqueName));
                continue;
            }
            Security s = new Security(ticker, secElem.getAttribute("Name"));
            agent.setSecurity(s);

            // *** signal provider
            NodeList sigNodes = agentElem.getElementsByTagName("SignalProvider");
            if (sigNodes.getLength() == 0) {
                System.err.println(String.format("No SignalProvider supplied for Agent '%1$s'."
                        + "This agent will be ignored", agentUniqueName));
                continue;
            } else if (sigNodes.getLength() > 1) {
                System.err.println(String.format(
                        "More than one SignalProvider supplied for Agent '%1$s'."
                                + "Only the first will be used", agentUniqueName));
            }

            Element sigElem = (Element) sigNodes.item(0);
            SignalProvider sig = (SignalProvider) getReflectedObject(sigElem
                    .getAttribute("ClassName"), SignalProvider.class);
            if (sig == null) {
                System.err.println("Failed to construct SignalProvider for: "
                        + agentUniqueName);
                continue;
            }
            applyCustomSettings(sig, sigElem);
            agent.setSignalProvider(sig);

            // *** PositionSizer
            NodeList sizerNodes = agentElem.getElementsByTagName("PositionSizer");
            if (sizerNodes.getLength() == 0) {
                System.err.println(String.format("No PositionSizer supplied for Agent '%1$s'."
                        + "This agent will be ignored", agentUniqueName));
                continue;
            } else if (sizerNodes.getLength() > 1) {
                System.err.println(String.format("More than one PositionSizer supplied for Agent '%1$s'."
                                + "Only the first will be used", agentUniqueName));
            }

            Element sizerElem = (Element) sizerNodes.item(0);
            PositionSizer sizer = (PositionSizer) getReflectedObject(sizerElem
                    .getAttribute("ClassName"), PositionSizer.class);
            if (sizer == null) {
                System.err.println("Failed to construct PositionSizer for: "
                        + agentUniqueName);
                continue;
            }
            applyCustomSettings(sizer, sizerElem);
            agent.setPositionSizer(sizer);

            // stop adjuster
            NodeList stopNodes = agentElem.getElementsByTagName("StopAdjuster");
            if (stopNodes.getLength() == 0) {
                System.err.println(String.format("No StopAdjuster supplied for Agent '%1$s'."
                                        + "This agent will be ignored", agentUniqueName));
                continue;
            } else if (stopNodes.getLength() > 1) {
                System.err.println(String.format(
                        "More than one StopAdjuster supplied for Agent '%1$s'."
                                + "Only the first will be used", agentUniqueName));
            }

            Element stopElem = (Element) stopNodes.item(0);
            StopAdjuster stop = (StopAdjuster) getReflectedObject(stopElem
                    .getAttribute("ClassName"), StopAdjuster.class);
            if (stop == null) {
                System.err.println("Failed to construct StopAdjuster for: "
                        + agentUniqueName);
                continue;
            }
            applyCustomSettings(stop, stopElem);
            agent.setStopAdjuster(stop);
                        
            //apply any custom settings for the agent itself
            applyCustomSettings(agent, agentElem);
            
            
            settingsOutput.getAgents().put(agentUniqueName, agent);
        }

        return true;
    }

    private static Object getReflectedObject(String className,
            Class<?> requiredSuperType) {
        Class hpcClass = null;
        Object hpcObj = null;
        try {
            hpcClass = Class.forName(className);
            hpcObj = hpcClass.newInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        if (hpcObj == null) {
            System.err.println(String.format("Error during reflection of '%$'",
                    className));
            return null;
        }

        if (!requiredSuperType.isAssignableFrom(hpcClass)) {
            System.err.println(String.format(
                    "'%1$s' is not an instanceof '%2$s", className,
                    requiredSuperType.getName()));
            return null;
        }

        return hpcObj;
    }

    private static void applyCustomSettings(Object target, Element elem) {
        Node current = elem.getFirstChild();
        if (current == null)
            return;

        do {
            String name = current.getNodeName();
            if (name != null && name.equalsIgnoreCase("CustomSetting")
                    && current instanceof Element) {
                applyCustomSetting(target, (Element) current);
            }
        } while ((current = current.getNextSibling()) != null);
    }

    // private static void applyCustomSettings(Object target, NodeList
    // customSettingElems) {
    // for (int i = 0; i < customSettingElems.getLength(); i++) {
    // if (customSettingElems.item(i) instanceof Element)
    // applyCustomSetting(target, (Element)customSettingElems.item(i));
    // }
    // }

    private static void applyCustomSetting(Object target,
            Element customSettingElem) {
        String methodName = customSettingElem.getAttribute("PropertyName");
        String argTypeName = customSettingElem.getAttribute("RuntimeType");
        String valueStr = customSettingElem.getAttribute("Value");

        boolean success = true;
        String msg = null;
        try {
            Class<?> argClass;
            Object value;
            //TODO: fix this hack
            if (argTypeName.equals("double")) {
        	argClass = Double.TYPE;
        	value = Double.valueOf(valueStr).doubleValue();
            }
            else if (argTypeName.equals("boolean")) {
        	argClass = Boolean.TYPE;
        	value = Boolean.valueOf(valueStr).booleanValue();
            }
            else if (argTypeName.equals("char")) {
        	argClass = Character.TYPE;
        	value = Character.valueOf(valueStr.charAt(0)).charValue();
            }
            else if (argTypeName.equals("byte")) {
        	argClass = Byte.TYPE;
        	value = Byte.valueOf(valueStr).byteValue();
            }
            else if (argTypeName.equals("short")) {
        	argClass = Short.TYPE;
        	value = Short.valueOf(valueStr).shortValue();
            }
            else if (argTypeName.equals("integer")) {
        	argClass = Integer.TYPE;
        	value = Integer.valueOf(valueStr).intValue();
            }
            else if (argTypeName.equals("long")) {
        	argClass = Long.TYPE;
        	value = Long.valueOf(valueStr).longValue();
            }
            else if (argTypeName.equals("float")) {
        	argClass = Float.TYPE;
        	value = Float.valueOf(valueStr).floatValue();
            }
            else if (argTypeName.equals("void")) {
        	argClass = Void.TYPE;
        	value = null;
            }
            else {
        	argClass = Class.forName(argTypeName);
    		value = valueStr;
            }
            Method targetMethod = target.getClass().getMethod(methodName, argClass);
            targetMethod.invoke(target, value);
        } catch (SecurityException e) {
            success = false;
            msg = "SecurityException: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            success = false;
            msg = "IllegalArgumentException: " + e.getMessage();
        } catch (ClassNotFoundException e) {
            success = false;
            msg = "ClassNotFoundException: " + e.getMessage();
        } catch (NoSuchMethodException e) {
            success = false;
            msg = "NoSuchMethodException: " + e.getMessage();
        } catch (IllegalAccessException e) {
            success = false;
            msg = "IllegalAccessException: " + e.getMessage();
        } catch (InvocationTargetException e) {
            success = false;
            msg = "InvocationTargetException: " + e.getMessage();
        }

        if (!success) {
            System.err.println(String.format(
                    "Error while applying custom setting '%1s' "
                            + "to method '%2$s' on type '%3$s'. Msg = %4$s",
                    valueStr, methodName, target.getClass().getName(), msg));
        }
    }
}
