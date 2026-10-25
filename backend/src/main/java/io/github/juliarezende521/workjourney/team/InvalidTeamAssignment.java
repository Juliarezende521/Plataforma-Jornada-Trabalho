package io.github.juliarezende521.workjourney.team;

public class InvalidTeamAssignment extends RuntimeException {
    private final int status;
    private final String reason;
    InvalidTeamAssignment() { this(400, "INVALID"); }
    InvalidTeamAssignment(int status, String reason) { this.status = status; this.reason = reason; }
    public int status() { return status; }
    public String reason() { return reason; }
}
