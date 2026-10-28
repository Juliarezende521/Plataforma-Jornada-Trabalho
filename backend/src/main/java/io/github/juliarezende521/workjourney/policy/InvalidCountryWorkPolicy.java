package io.github.juliarezende521.workjourney.policy;

public final class InvalidCountryWorkPolicy extends RuntimeException {
    private final int status;
    InvalidCountryWorkPolicy() { this(400); }
    InvalidCountryWorkPolicy(int status) { this.status = status; }
    public int status() { return status; }
}
