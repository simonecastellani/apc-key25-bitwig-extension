package com.apcsequencer;

/**
 * Immutable value type representing a Global Scale: a root note (0–11, C=0) and a Mode.
 */
public record GlobalScale(int root, Mode mode) {
    public GlobalScale {
        if (root < 0 || root > 11) throw new IllegalArgumentException("root must be 0–11, got " + root);
    }
}
