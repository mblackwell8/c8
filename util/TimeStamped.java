package c8.util;

/**
 * Any item with a time-based character
 * 
 * NB. TimeStamp should be immutable, particularly if the type is to be included
 * in a TimeSeries collection, as changes to the timestamp after insertion will
 * break the TimeSeries
 */
public interface TimeStamped {
    long getTimeStamp();
}