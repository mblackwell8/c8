package c8.gateway;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class DbMessageSink implements MessageSink {

    Connection m_dbConn;
    PreparedStatement m_stmt;
    Message m_lastMsg;
    String m_dbAcc = "jdbc:mysql://localhost:3306/fx_data";
    String m_userName = "mark";
    String m_pwd = "alliecat8";
    String m_pid;
    
    static final Logger LOG = LogManager.getLogger(DbMessageSink.class);
    
    public DbMessageSink() {
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
    public boolean send(Message m) {
	if (m_dbConn == null)
	    connect();
	
	if (m_dbConn == null) {
	    LOG.error("Connection is null. Cannot send() message. Returning false");
	    return false;
	}
	
	if (m_stmt == null) {
	    try {
		m_stmt = m_dbConn.prepareStatement(
			"INSERT INTO t_data_Message (MsgTimeStamp, PID, AppSetID, AppSetLinkedID, MsgAction, MsgPriority, MsgData) " +
			"VALUES (?, ?, ?, ?, ?, ?, ?)");
	    } catch (SQLException e) {
		LOG.error("Could not create INSERT statement");
	    }
	}
	
	if (m_stmt == null) {
	    LOG.error("SQL Statement is null. Cannot send() message. Returning false");
	    return false;
	}
	
	boolean done = false;
	try {
	    //this needs to be in milliseconds... is it?
	    m_stmt.setTimestamp(1, new Timestamp(m.getTimeStamp()));
	    m_stmt.setString(2, m_pid.substring(0, 9));
	    m_stmt.setInt(3, m.getId());
	    m_stmt.setInt(4, m.getLinkedId());
	    m_stmt.setString(5, m.getAction().toString());
	    m_stmt.setString(6, m.getPriority().toString());
	    m_stmt.setString(7, m.getData());
	    
	    done = (m_stmt.executeUpdate() == 1);
	} catch (SQLException e) {
	    LOG.error("Could not execute DB insert for message: " + m.toString(), e);
	}
	
	return done;
    }

    public void close() throws IOException {
	try {
	    m_dbConn.close();
	} catch (SQLException e) {
	    LOG.error("SQLException on close", e);
	}

    }

}
