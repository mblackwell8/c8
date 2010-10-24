package c8.gateway;

import java.io.*;

public class PipedSource implements MessageSource {

    private PipedReader m_pipe;
    private BufferedReader m_reader;
    
    public PipedSource() { }
    
    public PipedSource(PipedReader pipe) {
	connect(pipe);
    }
    
    public void connect(PipedReader pipe) {
	m_pipe = pipe;
	m_reader = new BufferedReader(pipe);
    }
    
    public Message getNext() {
	Message next = null;
	
	try {
	    if(m_pipe.ready()) {
		String msgStr = m_reader.readLine();
		next = Message.parseMessage(msgStr);
	    }
	} catch (IOException e) {
	    System.err.println("Piped source: " + e.toString());
	}
	
	return next;
    }

    public void close() throws IOException {
	m_reader.close();
	m_pipe.close();
    }

}
