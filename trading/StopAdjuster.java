package c8.trading;

import c8.util.*;

import com.oanda.fxtrade.api.*;

public interface StopAdjuster extends Algorithm, Cloneable {
    // / <summary>
    // / Applies the local algorithm to decide whether to move stop loss or take
    // profit
    // / </summary>
    // / <param name="t">The up to date order</param>
    // / <returns>True, if the transactions SL or TP should be moved</returns>
    boolean shouldMoveStops(MarketOrder mo, TickPrice currentPrice);

    // / <summary>
    // / Moves the SL and TP of the provided order in accordance with the local
    // algorithm
    // / </summary>
    // / <param name="mo">The up to date order</param>
    // / <param name="currentPrice">The current price</param>
    void moveStops(MarketOrder mo, TickPrice currentPrice);

    // / <summary>
    // / Calculates the stop loss price, in it's most basic form
    // / </summary>
    // / <remarks>
    // / Makes no reference to a long or short order, nor to any historical SL
    // / </remarks>
    // / <param name="currentPrice">The current price</param>
    // / <returns>The new stop loss</returns>
    Price calculateSL(TickPrice currentPrice, boolean isLong);

    // / <summary>
    // / Calculates the take profit price, in it's most basic form
    // / </summary>
    // / /// <remarks>
    // / Makes no reference to a long or short order, nor to any historical SL
    // / </remarks>
    // / <param name="currentPrice">The take profit price</param>
    // / <returns>The new take profit</returns>
    Price calculateTP(TickPrice currentPrice, boolean isLong);

    StopAdjuster clone();
}
