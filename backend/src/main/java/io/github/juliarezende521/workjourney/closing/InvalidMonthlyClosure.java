package io.github.juliarezende521.workjourney.closing;

public class InvalidMonthlyClosure extends RuntimeException {
    private final int status;
    private final String reason;

    InvalidMonthlyClosure() { this(400, "INVALID"); }
    InvalidMonthlyClosure(int status, String reason) { this.status = status; this.reason = reason; }
    public int status() { return status; }
    public String reason() { return reason; }
}
