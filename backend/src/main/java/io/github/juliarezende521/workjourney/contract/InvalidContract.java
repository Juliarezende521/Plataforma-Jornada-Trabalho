package io.github.juliarezende521.workjourney.contract;

public class InvalidContract extends RuntimeException {
    private final int status;

    InvalidContract() {
        this(400);
    }

    InvalidContract(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
