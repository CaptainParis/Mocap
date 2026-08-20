package com.paris.mocap.runtime;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class FailForward {
    private final Logger logger;

    public FailForward(Logger logger) {
        this.logger = logger;
    }

    public void run(String operation, Runnable work) {
        try {
            work.run();
        } catch (Throwable fault) {
            this.logger.log(Level.WARNING, "Fail-forward skipped: " + operation, fault);
        }
    }

    public boolean attempt(String operation, Runnable work) {
        try {
            work.run();
            return true;
        } catch (Throwable fault) {
            this.logger.log(Level.WARNING, "Fail-forward skipped: " + operation, fault);
            return false;
        }
    }
}
