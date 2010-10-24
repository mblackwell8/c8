package c8.gateway;

import java.io.*;

public class PipedSink implements MessageSink {
    
    PipedWriter m_pipe;
    
    public PipedSink() { }
    
    public PipedSink(PipedWriter pw) {
	connect(pw);
    }
    
    public void connect(PipedWriter pw) {
	m_pipe = pw;
    }

    public boolean send(Message m) {
	String mStr = m.toString();
	if (m_pipe == null)
	    return false;
	
	try {
	    m_pipe.write(mStr.toCharArray(), 0, mStr.length());
	} catch (IOException e) {
	    return false;
	}
	
	return true;
    }

    public void close() throws IOException {
	m_pipe.close();
    }

}
