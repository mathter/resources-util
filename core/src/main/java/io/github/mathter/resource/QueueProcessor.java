package io.github.mathter.resource;

import java.util.concurrent.TimeUnit;

/**
 * Processor for queueing items and processing them.
 *
 * @param <T> the type of elements to be processed
 */
public interface QueueProcessor<T> extends AutoCloseable {
    /**
     * Offers an item to the queue, waiting indefinitely if necessary.
     *
     * @param item the item to add
     * @throws InterruptedException if the thread is interrupted
     */
    public void offer(T item);

    /**
     * Offers an item to the queue, waiting up to the specified timeout.
     *
     * @param item     the item to add
     * @param timeout  the maximum time to wait
     * @param timeUnit the time unit of the timeout argument
     * @return true if the item was added, false if the timeout elapsed
     * @throws InterruptedException if the thread is interrupted
     */
    public boolean offer(T item, long timeout, TimeUnit timeUnit) throws InterruptedException;
}
