package c8.util;

import java.util.Currency;

public class Security implements Comparable<Security> {
    String m_ticker;

    String m_name;

    public static Security Empty;
    
    static {
        Empty = new Security("Empty");
    }

    public Security(String ticker) {
        m_ticker = ticker;
        m_name = ticker;
    }

    public Security(String ticker, String name) {
        m_ticker = ticker;
        m_name = name;
    }

    public Security() {
        Empty = new Security("");
    }

    public String toString() {
        return m_ticker;
    }

    public int hashCode() {
        return m_ticker.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof Security)) {
            return false;
        }

        return this.equals((Security) obj);
    }

    public boolean equals(Security s) {
        return m_ticker.equals(s.m_ticker);
    }

    public int compareTo(Security other) {
        return m_ticker.compareTo(other.m_ticker);
    }

    public String getTicker() {
        return m_ticker;
    }

    public String getName() {
        return m_name;
    }
    
    public Currency parseFxCcy() {
	String[] parts = m_ticker.split("/");
	
	return Currency.getInstance(parts[1]);
    }
}