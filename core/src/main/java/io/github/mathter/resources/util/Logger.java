package io.github.mathter.resources.util;

import java.util.function.Supplier;

/**
 * An interface for logging messages.
 */
public interface Logger {
    /**
     * Logs an info message using a supplier.
     *
     * @param s the message supplier
     */
    public void info(Supplier<String> s);

    /**
     * Logs an info message.
     *
     * @param s the message
     */
    default void info(String s) {
        this.info(() -> s);
    }

    /**
     * Logs a debug message using a supplier.
     *
     * @param s the message supplier
     */
    public void debug(Supplier<String> s);

    /**
     * Logs a debug message.
     *
     * @param s the message
     */
    default void debug(String s) {
        this.debug(() -> s);
    }

    /**
     * Logs an error message using a supplier and an exception.
     *
     * @param s the message supplier
     * @param t the exception
     */
    public void error(Supplier<String> s, Throwable t);

    /**
     * Logs an error message.
     *
     * @param s the message
     * @param t the exception
     */
    default public void error(String s, Throwable t) {
        this.error(() -> s, t);
    }
}
