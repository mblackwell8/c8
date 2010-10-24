package c8.trading;

import org.apache.log4j.Logger;

public class FixedPositionSizer implements PositionSizer {
    private double m_positionPercent;

    public static double DEFAULT_POSITION = 0.02;
    
    private static Logger LOG = Logger.getLogger(FixedPositionSizer.class);

    public FixedPositionSizer() {
        m_positionPercent = DEFAULT_POSITION;
    }

    public FixedPositionSizer(double posnPercent) {
        setPositionPercent(posnPercent);
    }

    public double getPositionPercent() {
        return m_positionPercent;
    }

    public void setPositionPercent(double value) {
        if (value > 1.0) {
            LOG.info(String.format("Fixed Position set @ >100%. Assumed to mean %1$6.2f % (1/100 of setting)", value));
            m_positionPercent = value / 100;
        } else {
            m_positionPercent = value;
        }
        
        LOG.info("Position percent set to: " + Double.toString(value));
    }

    public double calculatePosition() {
        return m_positionPercent;
    }

    public String getDescription() {
        return "Allocates a fixed portion of the available capital to each trade";
    }

    public String getName() {
        return "Fixed Position Sizer";
    }

    public PositionSizer clone() {
        return new FixedPositionSizer(m_positionPercent);
    }
    
    public String toString() {
	return String.format("Fixed Position Sizer at %1$6.2f %", m_positionPercent * 100);
    }
}
