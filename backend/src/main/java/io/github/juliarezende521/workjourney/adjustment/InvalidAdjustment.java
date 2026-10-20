package io.github.juliarezende521.workjourney.adjustment;

public final class InvalidAdjustment extends RuntimeException {
    private final int status;
    public InvalidAdjustment() { this(400); }
    public InvalidAdjustment(int status) { this.status=status; }
    public int status() { return status; }
}
