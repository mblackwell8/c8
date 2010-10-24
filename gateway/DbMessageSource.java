package c8.gateway;

import java.io.IOException;
import java.sql.*;
//import java.

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import c8.ExecEnviron;

import java.lang.management.ManagementFactory;

public class DbMessageSource implements MessageSource {

    Connection m_dbConn;
    PreparedStatement m_stmt;
    Message m_lastMsg;
    //see http://dev.mysql.com/doc/refman/5.0/en/connector-j-usagenotes-troubleshooting.html#qandaitem-28-4-5-3-9
    String m_dbAcc = "jdbc:mysql://localhost:3306/fx_data";
    String m_userName = "mark";
    String m_pwd = "alliecat8";
    long m_startTime = System.currentTimeMillis();
    String m_pid;
    
    static final Logger LOG = LogManager.getLogger(DbMessageSource.class);
    
    public DbMessageSource() {
	//see http://blog.igorminar.com/2007/03/how-java-application-can-discover-its.html
	//should be of the form 28906@localhost
	m_pid = ManagementFactory.getRuntimeMXBean().getName();
    }

    public void setConnectionString(String str) {
	String[] parts = str.split(" ");
	m_dbAcc = parts[0];
	m_userName = parts[1];
	m_pwd = parts[2];
    }
  
    private void connect() {
	LOG.info("Attempting connection to " + m_dbAcc);
	try {
	    Class.forName("com.mysql.jdbc.Driver");
	    m_dbConn = DriverManager.getConnection(m_dbAcc, m_userName, m_pwd);
	} catch (ClassNotFoundException e) {
	    LOG.error("Cannot find com.mysql.jdbc.Driver", e);
	}
	catch (SQLException e) {
	    LOG.error("SQLException on connect", e);
	}
    }
    
    
    
    public Message getNext() {
	if (m_dbConn == null)
	    connect();
	
	if (m_dbConn == null) {
	    LOG.error("Connection is null. Cannot getNext() message. Returning null");
	    return null;
	}
	
	if (m_stmt == null) {
	    try {
		m_stmt = m_dbConn.prepareStatement(
			"SELECT MsgTimeStamp, AppSetID, AppSetLinkedID, MsgPriority, MsgAction, MsgData " +
			"FROM t_data_Message " +
			"WHERE PID <> '" + m_pid.substring(0, 9) + "' and AppSetID > ? " +
			"ORDER BY AppSetID");
	    } catch (SQLException e) {
		LOG.error("Could not create SELECT statement", e);
	    }
	}
	
	if (m_stmt == null) {
	    LOG.error("SQL Statement is null. Cannot getNext() message. Returning null");
	    return null;
	}
	
	Message thisMsg = null;
	try {
	    int lastId = (m_lastMsg != null ? m_lastMsg.getId() : -1);
	    m_stmt.setInt(1, lastId);
	    ResultSet rs = m_stmt.executeQuery();
	    
	    while (rs.next()) {
		long dt = rs.getTimestamp(1).getTime();
		int id = rs.getInt(2);
		if (m_lastMsg != null && m_lastMsg.getId() == id)
		    continue;
		
		int linkedId = rs.getInt(3);
		String mp = rs.getString(4);
		String ma = rs.getString(5);
		String data = rs.getString(6);
		
		//String.format("%1$d\t%2$s\t%3$d\t%4$s\t%5$s\t%6$s", m_id, date, m_linkedId, m_priority, m_action, m_data)
		String msgStr = String.format("%1$d\t%2$TF %2$TT\t%3$d\t%4$s\t%5$s\t%6$s", id, dt, linkedId, mp, ma, data);
		thisMsg = Message.parseMessage(msgStr);
		if (thisMsg != null) {
		    m_lastMsg = thisMsg;
		    break;
		}
	    }
	} catch (SQLException e) {
	    LOG.error("Could not execute DB query", e);
	}
	
	return thisMsg;
    }

    public void close() throws IOException {
	try {
	    m_dbConn.close();
	} catch (SQLException e) {
	    LOG.error("SQLException on close", e);
	}

    }

}
