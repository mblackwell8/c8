package c8.gateway;

import java.io.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class FileSource implements MessageSource {
    private String m_fileName;
    private File m_file;
    private int m_currentLineNum;
    private boolean m_isClosed;
    
    static final Logger LOG = LogManager.getLogger(FileSource.class);

    public FileSource(String filename) throws IOException {
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
    
    public Message getNext() {
	if (m_isClosed) {
	    LOG.info("Attempt to read from closed source");
	    return null;
	}
	
	//canRead tested only... should we have exclusive access?
	if (!m_file.canRead()) {
	    LOG.info("Underlying file appears to be busy: " + m_fileName);
	    return null;
	}
	
	String line = null;
	try {
	    //LOG.debug("Accessing file source at: " + m_fileName);
            FileReader fr = new FileReader(m_fileName);
	    LineNumberReader reader = new LineNumberReader(fr);
	    while ((line = reader.readLine()) != null &&
		    reader.getLineNumber() <= m_currentLineNum);
            reader.close();
            fr.close();
            //LOG.debug("Releasing file source at: " + m_fileName);
        } catch (IOException e) {
            LOG.error("Terminal exception occurred on FileSource.getNext(): " + e.toString());
            this.close();
            return null;
        }
        
        if (line != null)
            m_currentLineNum++;

        return (Message.parseMessage(line));
    }
    

    public void close() {
	m_isClosed = true;
	m_currentLineNum = 0;
    }

}
