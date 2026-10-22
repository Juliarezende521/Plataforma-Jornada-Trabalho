package io.github.juliarezende521.workjourney.schedule;

public final class InvalidSchedule extends RuntimeException {
    private final int status;
    public InvalidSchedule(){this(400);}
    public InvalidSchedule(int status){this.status=status;}
    public int status(){return status;}
}
