package com.mrstudios.shulkers.rules;

/**
 * What to do with a click. {@code rule} is the row of DESIGN.md section 3.5 that decided it (0 when
 * no rule applied), used in tests and in the blocked-action log.
 */
public record ClickDecision(Kind kind, int rule) {

    public enum Kind {
        /** Let the click through. */
        ALLOW,
        /** Cancel the click. */
        CANCEL,
        /** Cancel the click and open the clicked shulker. */
        OPEN
    }

    public static final ClickDecision ALLOW = new ClickDecision(Kind.ALLOW, 0);

    static ClickDecision allow(int rule) {
        return new ClickDecision(Kind.ALLOW, rule);
    }

    static ClickDecision cancel(int rule) {
        return new ClickDecision(Kind.CANCEL, rule);
    }

    static ClickDecision open(int rule) {
        return new ClickDecision(Kind.OPEN, rule);
    }

    public boolean cancels() {
        return kind != Kind.ALLOW;
    }
}
