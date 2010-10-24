package c8.trading;

public class KellyPositionSizer implements PositionSizer {
    // see: http://en.wikipedia.org/wiki/Kelly_criterion

    private double m_winProb;

    private double m_winOdds;
    
    private double m_kellyFraction = DEFAULT_KELLY_FRAC;

    //this combination will bet 10% of stake
    public static double DEFAULT_ODDS = 1.0;  // even money
    public static double DEFAULT_WIN_PROBABILITY = 0.55; // 50% means zero bet
    public static double DEFAULT_KELLY_FRAC = 1.0; //use to scale down generally aggressive result

    public KellyPositionSizer() {
	this(DEFAULT_WIN_PROBABILITY, DEFAULT_ODDS);
    }

    public KellyPositionSizer(double winProbability, double winOdds) {
       this(winProbability, winOdds, DEFAULT_KELLY_FRAC);
    }
    
    public KellyPositionSizer(double winProbability, double winOdds, double kellyFraction) {
        m_winProb = winProbability;
        m_winOdds = winOdds;
        m_kellyFraction = kellyFraction;
    }

    // / <summary>
    // / Gets or sets the probability that the trade will be profitable
    // / </summary>
    public double getWinProbability() {
        return m_winProb;
    }

    public void setWinProbability(double value) {
        m_winProb = value;
    }

    // / <summary>
    // / Gets or sets the odds received (the likely profit relative to the
    // likely loss)
    // / </summary>
    public double getWinOdds() {
        return m_winOdds;
    }

    public void setWinOdds(double value) {
        m_winOdds = value;
    }
    
    // / <summary>
    // / Gets or sets the odds received (the likely profit relative to the
    // likely loss)
    // / </summary>
    public double getKellyFraction() {
        return m_kellyFraction;
    }

    public void setKellyFraction(double value) {
        m_kellyFraction = value;
    }

    public PositionSizer clone() {
        return new KellyPositionSizer(m_winProb, m_winOdds, m_kellyFraction);
    }

    public double calculatePosition() {
        if (m_winOdds == 0 || m_winProb == 0)
            return 0;

        double posn = (m_winOdds * m_winProb - (1 - m_winProb)) / m_winOdds;

        // Kelly never bets the entire bankroll, and always bets something
        assert (posn < 1.0 && posn > 0.0);

        return (posn * m_kellyFraction);
    }

    public String getDescription() {
        return String.format("Implements the Kelly Criterion (odds = %1$d, win prob = %2$d, frac = %3$d)", 
        	m_winOdds * 100.0, m_winProb * 100.0, m_kellyFraction * 100.0);
    }

    public String getName() {
        return "Kelly Position Sizer";
    }

}
