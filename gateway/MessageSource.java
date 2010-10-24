package c8.gateway;

public interface MessageSource extends java.io.Closeable {
    //returns the next message in priority order
    //or null if there are no messages
    Message getNext();

}
