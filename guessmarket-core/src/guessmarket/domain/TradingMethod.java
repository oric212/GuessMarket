package guessmarket.domain;

import java.io.Serializable;

/**
 * Identifies the trading mechanism used by an event.
 *
 * <p>This interface remains deliberately minimal until LMSR and Order Book
 * provide a genuinely shared behavioral contract.</p>
 */

public interface TradingMethod extends Serializable {
    TradingMethodType getType();
}