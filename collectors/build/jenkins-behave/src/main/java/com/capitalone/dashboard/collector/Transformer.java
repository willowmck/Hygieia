package com.capitalone.dashboard.collector;

/**
 * Transforms this thing into that thing
 */
public interface Transformer<S, O> {
    
    /**
     * Transforms a source S into an output O
     * @param source source
     * @return output
     */
    O transform(S source);
    
}
