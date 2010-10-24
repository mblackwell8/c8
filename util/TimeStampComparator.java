package c8.util;

import java.util.Comparator;

public class TimeStampComparator implements Comparator<TimeStamped> {
    public int compare(TimeStamped o1, TimeStamped o2) {
        if (o1.getTimeStamp() > o2.getTimeStamp())
            return -1;
        if (o1.getTimeStamp() < o2.getTimeStamp())
            return 1;

        return 0;
    }
}
