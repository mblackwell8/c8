package c8.gateway;

import java.io.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class FileSink implements MessageSink {
    private String m_fileName;
    private File m_file;
    private boolean m_isClosed = false;
    
    static final Logger LOG = LogManager.getLogger(FileSink.class);
    
    public FileSink(String filename) throws IOException {
	if (filename == null || filename.isEmpty())
            throw new IllegalArgumentException("Provide filename is null or empty");
	
        m_file = new File(filename);
        if (!m_file.exists()) {
	    if (!m_file.createNewFile())
		throw new IOException("Could not create file at: " + filename);
	}

	m_fileName = filename;
    }
    
    public String getFilename() {
        return m_fileName;
    }

    public boolean send(Message m) {
	if (m == null) {
	    LOG.info("Attempt to send null message!");
	    return true;
	}
	
	if (m_isClosed) {
	    LOG.info("Attempt to write to closed sink");
	    return false;
	}
	
	if (!m_file.canWrite()) {
	    LOG.info("Underlying file appears to be busy: " + m_fileName);
	    return false;
	}
	
	try {
	    //LOG.debug("Accessing file sink at: " + m_fileName);
	    //want speed to avoid conflicts, so don't bother buffering??
	    //but only want to write COMPLETE lines... so that clients never read half lines
	    FileWriter fw = new FileWriter(m_fileName, true);
	    BufferedWriter writer = new BufferedWriter(fw);
	    
	    writer.write(m.toString());
	    writer.newLine();
	    writer.close();
	    fw.close();
	    //LOG.debug("Releasing file sink at: " + m_fileName);
	} catch (IOException e) {
	    LOG.error("Cannot write message: " + m.toString(), e);
	    return false;
	}
	
	return true;
    }
    
    public void close() {
	LOG.info("FileSink closed");
        m_isClosed = true;
    }

}
