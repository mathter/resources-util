package io.github.mathter.resource.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleQueueProcessorTest {

    @Test
    void testBuildWithoutConsumerThrowsException() {
        final SimpleQueueProcessor.Builder<String> builder = SimpleQueueProcessor.<String>builder()
                .throwableConsumer(throwable -> {
                });

        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("Data consumer must be not null! Call `setConsumer`!", exception.getMessage());
    }

    @Test
    void testBuildWithoutThrowableConsumerThrowsException() {
        final SimpleQueueProcessor.Builder<String> builder = SimpleQueueProcessor.<String>builder()
                .consumer(collection -> {
                });

        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("Throwable consumer must be not null! Call `setThrowableConsumer`!", exception.getMessage());
    }

    @Test
    public void testBlocking() {
        final long timeout = 500;
        final int capacity = 10;
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final List<Collection<String>> result = new ArrayList<>();

        try (QueueProcessor<String> queueProcessor = SimpleQueueProcessor.<String>builder()
                .capacity(capacity)
                .processorCount(2)
                .timeout(timeout)
                .timeUnit(TimeUnit.MILLISECONDS)
                .consumer(items -> {
                    result.add(items);

                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e) {
                    }
                })
                .throwableConsumer(Throwable::printStackTrace)
                .build()) {

            IntStream.range(0, 2 * capacity + 5)
                    .mapToObj(i -> "Item " + i)
                    .forEach(queueProcessor::offer);

            Thread.sleep(3 * timeout);
            Assertions.assertEquals(3, result.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTimeoutBlocking() {
        final long timeout = 500;
        final int capacity = 10;
        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final List<Collection<String>> result = new ArrayList<>();

        try (QueueProcessor<String> queueProcessor = SimpleQueueProcessor.<String>builder()
                .capacity(capacity)
                .processorCount(1)
                .timeout(timeout)
                .timeUnit(TimeUnit.MILLISECONDS)
                .consumer(items -> {
                    lock.lock();
                    try {
                        result.add(items);
                        condition.await();
                    } catch (InterruptedException e) {
                    } finally {
                        lock.unlock();
                    }
                })
                .throwableConsumer(Throwable::printStackTrace)
                .build()) {

            IntStream.range(0, 20)
                    .mapToObj(i -> "Item " + i)
                    .forEach(queueProcessor::offer);

            Assertions.assertEquals(1, result.size());

            Assertions.assertEquals(
                    false,
                    queueProcessor.offer("Finalize", timeout / 100, TimeUnit.MILLISECONDS)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
