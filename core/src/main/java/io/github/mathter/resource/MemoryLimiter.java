package io.github.mathter.resource;

import java.util.concurrent.TimeUnit;

/**
 * An interface for limiting memory usage.
 *
 * <p>Implementations of this interface can monitor memory usage and block
 * execution when the usage exceeds a certain threshold.</p>
 */
public interface MemoryLimiter extends AutoCloseable {
    /**
     * Checks if the memory usage is within the allowed limits.
     * If the usage exceeds the threshold, this method will block until
     * the usage is within limits.
     */
    public void check();

    /**
     * Checks if the memory usage is within the allowed limits, with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param timeUnit the time unit of the timeout
     * @return true if the memory usage is within limits, false if the timeout expires
     * @throws InterruptedException if the thread is interrupted
     */
    public boolean check(long timeout, TimeUnit timeUnit) throws InterruptedException;
}
