package c8.gateway;

import java.io.IOException;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class MessagePasser implements MessageSink, MessageSource {

    PriorityBlockingQueue<Message> m_messageQueue;
    String m_channel;

    static final Logger LOG = LogManager.getLogger(MessagePasser.class);

    public MessagePasser(String channelName) {
        m_messageQueue = new PriorityBlockingQueue<Message>();
        m_channel = channelName;
    }

    public synchronized boolean send(Message m) {
        LOG.debug(String.format("Message rec'd (%1$s): %2$s", m_channel, m));

        if (m_messageQueue == null) {
            LOG.debug("Attempt to add message to closed queue" + m.toString());
            return false;
        }

        boolean added = m_messageQueue.add(m);

        if (!added)
            LOG.error(String.format("Incoming message on '%1$s' not added to queue: %2$s", m_channel, m));

        return added;
    }

    public synchronized void close() throws IOException {
        m_messageQueue = null;
        LOG.info("MessagePasser closed.");
    }

    public synchronized Message getNext() {
        Message next = m_messageQueue.poll();

        if (next != null)
            LOG.debug(String.format("Message posted (%1$s): %2$s", m_channel, next));

        return next;
    }

}
