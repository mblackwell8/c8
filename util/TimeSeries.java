package c8.util;

import java.util.*;

public class TimeSeries<E extends TimeStamped> extends TreeMap<Long, E>
        implements Iterable<E> {
    public TimeSeries() {
    }

    public TimeSeries(Collection<? extends E> c) {
        this.addAll(c);
    }

    public boolean add(E e) {
        // replace any existing key
        this.put(e.getTimeStamp(), e);

        return true;
    }

    public boolean addAll(Collection<? extends E> c) {
        for (E e : c)
            this.add(e);

        return true;
    }

    public boolean contains(E e) {
        return this.containsKey(e.getTimeStamp());
    }

    public Iterator<E> iterator() {
        return this.values().iterator();
    }

    public long firstDate() {
        return super.firstKey();
    }

    public long lastDate() {
        return super.lastKey();
    }

    public TimeSeries<E> headSeries(long to) {
        return headSeries(to, false);
    }

    public TimeSeries<E> headSeries(long to, boolean toInclusive) {
        NavigableMap<Long, E> map = super.headMap(to, toInclusive);
        if (map == null)
            return null;

        return new TimeSeries<E>(map.values());
    }

    // seems an unnatural inclusion... will see if useful
    // public TimeSeries<E> headSeries(int firstIndex) {
    // return subSeries(firstIndex, this.size() - 1);
    // }

    public TimeSeries<E> subSeries(long from, long to) {
        return subSeries(from, true, to, false);
    }

    public TimeSeries<E> subSeries(long from, boolean fromInclusive, long to,
            boolean toInclusive) {
        NavigableMap<Long, E> map = super.subMap(from, fromInclusive, to,
                toInclusive);
        if (map == null)
            return null;

        return new TimeSeries<E>(map.values());
    }

    // seems an unnatural inclusion... will see if useful
    // public TimeSeries<E> subSeries(int firstIndex, int lastIndex) {
    // return null;
    // }

    public TimeSeries<E> tailSeries(long fromTime) {
        return tailSeries(fromTime, false);
    }

    public TimeSeries<E> tailSeries(long fromTime, boolean fromInclusive) {
        NavigableMap<Long, E> map = super.tailMap(fromTime, fromInclusive);
        if (map == null)
            return null;

        return new TimeSeries<E>(map.values());
    }

    // seems an unnatural inclusion... will see if useful
    // public TimeSeries<E> tailSeries(int firstIndex) {
    // return null;
    // }

    public E lastBefore(long time) {
        Map.Entry<Long, E> entry = super.lowerEntry(time);
        if (entry == null)
            return null;

        return entry.getValue();
    }

    public E firstAfter(long time) {
        Map.Entry<Long, E> entry = super.higherEntry(time);
        if (entry == null)
            return null;

        return entry.getValue();
    }

    public E closestTo(long time) {
        Map.Entry<Long, E> ceiling = super.ceilingEntry(time);
        if (ceiling != null && ceiling.getKey().longValue() == time)
            return ceiling.getValue();

        Map.Entry<Long, E> floor = super.floorEntry(time);
        if (floor != null && floor.getKey().longValue() == time)
            return floor.getValue();

        if (ceiling == null && floor == null)
            return null;

        if (ceiling == null)
            // floor is not null according to logic above
            return floor.getValue();

        if (floor == null)
            // ceiling is not null according to logic above
            return ceiling.getValue();

        assert (floor != null && ceiling != null);
        if (Math.abs(floor.getKey().longValue() - time) <= Math.abs(ceiling
                .getKey().longValue()
                - time))
            return floor.getValue();

        return ceiling.getValue();
    }

}

/*
 * public class TimeSeries<E extends TimeStamped> extends AbstractSet<E>
 * implements NavigableSet<E> { LinkedList<E> m_list; //boolean isDescending;
 * 
 * public TimeSeries() { m_list = new LinkedList<E>(); }
 * 
 * public TimeSeries(Collection<? extends E> c) { m_list = new LinkedList<E>(c);
 * Collections.sort(m_list, this.comparator()); }
 *  // public E get(long timeStamp) { // Collections.binarySearch(m_list, ts,
 * this.comparator()); // }
 * 
 * @Override public Iterator<E> iterator() { return m_list.iterator(); }
 * 
 * @Override public int size() { return m_list.size(); }
 * 
 * @Override public boolean addAll(Collection<? extends E> coll) { if
 * (coll.size() == 0) return false;
 * 
 * //first sort the list by timestamp LinkedList<E> addlist = new LinkedList<E>(coll);
 * Collections.sort(addlist, this.comparator());
 * 
 * if (m_list.size() == 0) { m_list = addlist; return true; }
 * 
 * //add the shortest list if (addlist.size() > m_list.size()) { LinkedList<E>
 * temp = m_list; m_list = addlist; addlist = temp; }
 * 
 * //then add the new list int existingListIndex = 0; Iterator<E>
 * existingListIter = m_list.iterator(); assert existingListIter.hasNext();
 * boolean reachedEnd = false; E existingListCurrent = existingListIter.next();
 * 
 * Iterator<E> addListIter = addlist.iterator(); while (addListIter.hasNext() &&
 * !reachedEnd) { E addListCurrent = addListIter.next(); if
 * (addListCurrent.getTimeStamp() < existingListCurrent.getTimeStamp()) {
 * m_list.set(existingListIndex, addListCurrent); } else if
 * (addListCurrent.getTimeStamp() > existingListCurrent.getTimeStamp()) { //walk
 * the existing list until we step beyond the time of this element while
 * (!(reachedEnd = existingListIter.hasNext())) { existingListCurrent =
 * existingListIter.next(); if (addListCurrent.getTimeStamp() >
 * existingListCurrent.getTimeStamp()) { m_list.set(existingListIndex,
 * addListCurrent); break; } existingListIndex++; } } else if
 * (addListCurrent.getTimeStamp() == existingListCurrent.getTimeStamp()) {
 * //ignore any elements with the same timestamp reachedEnd =
 * existingListIter.hasNext(); if (!reachedEnd) existingListCurrent =
 * existingListIter.next(); existingListIndex++; } }
 * 
 * while (addListIter.hasNext()) { E addListCurrent = addListIter.next(); assert
 * addListCurrent.getTimeStamp() > m_list.getLast().getTimeStamp();
 * m_list.addLast(addListCurrent); }
 * 
 * return true; }
 * 
 * @Override public boolean add(E element) { if (element.getTimeStamp() >
 * m_list.getLast().getTimeStamp()) { m_list.addLast(element); return true; }
 * 
 * if (element.getTimeStamp() < m_list.getFirst().getTimeStamp()) {
 * m_list.addFirst(element); return true; }
 * 
 * //walk the list forwards until we find the correct pozzy int index = 0;
 * boolean wasAdded = false; for (TimeStamped item : m_list) { //disallow
 * multiple entries at the same timestamp if (item.getTimeStamp() ==
 * element.getTimeStamp()) { wasAdded = false; break; }
 * 
 * //find the first one after the provided element if (item.getTimeStamp() >
 * element.getTimeStamp()) { m_list.set(index, element); wasAdded = true; break; }
 * index++; }
 * 
 * assert (index > 0 && index < this.size());
 * 
 * return wasAdded; }
 * 
 * public Comparator<? super E> comparator() { return new
 * TimeStampComparator()// implements Comparator<TimeStamped> { public int
 * compare(TimeStamped o1, TimeStamped o2) { if (o1.getTimeStamp() >
 * o2.getTimeStamp()) return -1; if (o1.getTimeStamp() < o2.getTimeStamp())
 * return 1;
 * 
 * return 0; } }; }
 * 
 * public E first() { return m_list.getFirst(); }
 * 
 * public SortedSet<E> headSet(E toElement) { //return an inclusive headSet
 * return this.headSet(toElement, true); }
 * 
 * public E last() { return m_list.getLast(); }
 * 
 * public SortedSet<E> subSet(E fromElement, E toElement) { // return an
 * inclusive subSet return this.subSet(fromElement, true, toElement, true); }
 * 
 * public SortedSet<E> tailSet(E fromElement) { //return an inclusive tailSet
 * return this.tailSet(fromElement, true); }
 * 
 * //navigable set methods start here
 * 
 * //Returns the least element in this set greater than or equal to the given
 * element, //or null if there is no such element. public E ceiling(E e) { int
 * index = indexStrictlyBefore(e); if (index == -1) return null;
 * 
 * E retVal = m_list.get(index + 1); assert retVal.getTimeStamp() >=
 * e.getTimeStamp();
 * 
 * return retVal; }
 * 
 * public Iterator<E> descendingIterator() { return
 * m_list.descendingIterator(); }
 * 
 * public NavigableSet<E> descendingSet() { //this would break all kinds of
 * assumptions in this class
 *  // LinkedList<E> list = new LinkedList<E>(m_list); //
 * Collections.reverse(list); // // TimeSeries<E> ts = new TimeSeries<E>
 * 
 * throw new RuntimeException("TimeSeries.descendingSet Not implemented"); }
 * 
 * //Returns the greatest element in this set less than or equal to the given
 * element, //or null if there is no such element. public E floor(E e) { int
 * index = indexStrictlyBefore(e); if (index == -1) return m_list.getLast();
 * 
 * E retVal = m_list.get(index); if (index + 1 < m_list.size()) { E plusOne =
 * m_list.get(index + 1); if (plusOne.getTimeStamp() == e.getTimeStamp()) retVal =
 * plusOne; }
 * 
 * assert retVal.getTimeStamp() <= e.getTimeStamp();
 * 
 * return retVal; }
 * 
 * public NavigableSet<E> headSet(E toElement, boolean inclusive) { // TODO
 * Auto-generated method stub return null; }
 * 
 * //Returns the least element in this set strictly greater than the given
 * element, //or null if there is no such element. public E higher(E e) { int
 * index = indexStrictlyBefore(e) + 1; if (index >= m_list.size()) return null;
 * 
 * E retVal = m_list.get(index); if (retVal.getTimeStamp() == e.getTimeStamp()) {
 * retVal = null; if (index + 1 < m_list.size()) retVal = m_list.get(index + 1); }
 * assert retVal.getTimeStamp() >= e.getTimeStamp();
 * 
 * return retVal; }
 * 
 * //Returns the greatest element in this set strictly less than the given
 * element, //or null if there is no such element. public E lower(E e) { int
 * index = indexStrictlyBefore(e); if (index == -1) return m_list.getLast();
 * 
 * E retVal = m_list.get(index); assert retVal.getTimeStamp() <=
 * e.getTimeStamp();
 * 
 * return retVal; }
 * 
 * //Retrieves and removes the first (lowest) element, or returns null if this
 * set is empty. public E pollFirst() { return m_list.pollFirst(); }
 * 
 * //Retrieves and removes the last (highest) element, or returns null if this
 * set is empty. public E pollLast() { return m_list.pollLast(); }
 * 
 * public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E
 * toElement, boolean toInclusive) { TimeSeries<E> ts = new TimeSeries<E>();
 * int index = indexStrictlyBefore(fromElement); if (index == -1) return ts;
 * 
 * Iterator<E> iter = m_list.listIterator(index);
 * 
 * boolean checkedFirst = false; while (iter.hasNext()) { E elem = iter.next();
 * if (!checkedFirst && (!fromInclusive && elem.getTimeStamp() ==
 * fromElement.getTimeStamp())) continue; checkedFirst = true;
 * 
 * if (elem.getTimeStamp() >= toElement.getTimeStamp()) { if (!(toInclusive &&
 * elem.getTimeStamp() == toElement.getTimeStamp())) break; }
 * 
 * //we know they're in order, so just add them to the internal list
 * ts.m_list.add(elem); }
 * 
 * return ts; }
 * 
 * //Returns a view of the portion of this set whose elements are //greater than
 * (or equal to, if inclusive is true) fromElement public NavigableSet<E>
 * tailSet(E fromElement, boolean inclusive) { TimeSeries<E> ts = new
 * TimeSeries<E>(); int index = indexStrictlyBefore(fromElement); if (index ==
 * -1) return ts;
 * 
 * Iterator<E> iter = m_list.listIterator(index);
 * 
 * boolean checkedFirst = false; while (iter.hasNext()) { E elem = iter.next();
 * 
 * //avoid checking the timestamp on each iteration... the first //is the only
 * one worth checking if (!checkedFirst && (!inclusive && elem.getTimeStamp() ==
 * fromElement.getTimeStamp())) continue;
 * 
 * checkedFirst = true; //we know they're in order, so just add them to the
 * internal list ts.m_list.add(elem); }
 * 
 * return ts; }
 * 
 * //returns an iterator which, when next() is called will yield an E //which is
 * either equal to or greater than the requested E private int
 * indexStrictlyBefore(E e) { if (e.getTimeStamp() >
 * m_list.getLast().getTimeStamp()) return -1;
 * 
 * int index = 0; Iterator<E> iter = m_list.iterator();
 * 
 * if (e.getTimeStamp() < m_list.getFirst().getTimeStamp()) return 0;
 * 
 * while (iter.hasNext()) { E elem = iter.next(); if (elem.getTimeStamp() >=
 * e.getTimeStamp()) break;
 * 
 * index++; }
 * 
 * assert index < m_list.size();
 * 
 * return index; }
 *  }
 */