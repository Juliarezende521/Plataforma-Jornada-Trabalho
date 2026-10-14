package io.github.juliarezende521.workjourney.journey.api;

final class InvalidJourneyRequest extends RuntimeException {
    private final int status;
    InvalidJourneyRequest(int status) { super("Invalid journey request"); this.status = status; }
    int status() { return status; }
}
