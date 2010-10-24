package c8.trading;

public interface PositionSizer extends Algorithm, Cloneable {
    
    // Calculates the percent of total funds which should be allocated to a
    // trade
    double calculatePosition();

    PositionSizer clone();
}
