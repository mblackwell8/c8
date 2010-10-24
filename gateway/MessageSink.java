package c8.gateway;

public interface MessageSink extends java.io.Closeable {
    //sends a message, returns true if it was successfully sent
    boolean send(Message m);
}
