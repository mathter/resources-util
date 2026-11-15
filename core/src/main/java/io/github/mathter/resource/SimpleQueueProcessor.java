package io.github.mathter.resource;

import io.github.mathter.resource.util.Logger;
import io.github.mathter.resource.util.LoggerFactory;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A simple implementation of {@link QueueProcessor} that uses a collection of items
 * and processes them using a set of worker threads.
 *
 * <p>This implementation is thread-safe and supports capacity limits and timeouts.</p>
 *
 * <p>Processing Algorithm:
 * The processor maintains a queue of items. Worker threads continuously monitor the queue.
 * When the queue reaches its predefined capacity, the current queue is swapped with a new
 * one, and the items are dispatched to the consumer. If the queue has not reached capacity,
 * worker threads wait until the timeout expires or the capacity is reached.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * QueueProcessor&lt;String&gt; processor = new SimpleQueueProcessor.Builder&lt;String&gt;()
 *     .consumer(items -> items.forEach(System.out::println))
 *     .throwableConsumer(Throwable::printStackTrace)
 *     .capacity(100)
 *     .build();
 *
 * processor.offer("Hello, World!");
 * </pre>
 *
 * @param <T> the type of elements to be processed
 */
public class SimpleQueueProcessor<T> implements QueueProcessor<T>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.logger(SimpleMemoryLimiter.class);

    private static final Cleaner CLEANER = Cleaner.create();

    private final Consumer<Collection<T>> consumer;

    private final Consumer<Throwable> throwableConsumer;

    private final int capacity;

    private final long timeout;

    private final TimeUnit timeUnit;

    private final CollectionFactory<T> collectionFactory;

    private final Lock lock = new ReentrantLock();

    private final Condition isEmpty = this.lock.newCondition();

    private final Condition isFull = this.lock.newCondition();

    private final Thread[] threads;

    private final Cleaner.Cleanable cleanable;

    private Collection<T> queue;

    public static final <T> Builder<T> builder() {
        return new Builder<>();
    }

    private SimpleQueueProcessor(Consumer<Collection<T>> consumer,
                                 Consumer<Throwable> throwableConsumer,
                                 int capacity,
                                 long timeout,
                                 TimeUnit timeUnit,
                                 CollectionFactory<T> collectionFactory,
                                 int processorCount,
                                 ThreadFactory threadFactory) {
        this.consumer = consumer;
        this.throwableConsumer = throwableConsumer;
        this.capacity = capacity;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.collectionFactory = collectionFactory;
        this.queue = collectionFactory.apply(capacity);

        this.threads = new Thread[processorCount];

        for (int i = 0; i < processorCount; i++) {
            this.threads[i] = threadFactory.newThread(new Worker());
            this.threads[i].start();
        }

        this.cleanable = CLEANER.register(this, () -> {
            for (Thread thread : this.threads) {
                thread.interrupt();
            }
        });
    }

    @Override
    public void offer(T item) {
        this.lock.lock();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (queue.size() < this.capacity) {
                    this.queue.add(item);

                    if (this.queue.size() == this.capacity) {
                        this.isFull.signal();
                    }

                    break;
                } else {
                    try {
                        this.isEmpty.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted while offer...", e);
                    }
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean offer(T item, long timeout, TimeUnit timeUnit) throws InterruptedException {
        boolean result = false;
        final long start = System.nanoTime();

        while (this.lock.tryLock(timeout, timeUnit)) {
            try {
                if (queue.size() < this.capacity) {
                    this.queue.add(item);
                    result = true;

                    if (this.queue.size() == this.capacity) {
                        this.isFull.signal();
                    }
                } else {
                    final long timeout2 = timeout - timeUnit.convert(
                            System.nanoTime() - start,
                            TimeUnit.NANOSECONDS
                    );

                    if (timeout <= 0 || !this.isEmpty.await(timeout2, timeUnit)) {
                        break;
                    }
                }
            } finally {
                this.lock.unlock();
            }
        }

        return result;
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            boolean forceProcess = false;

            while (!Thread.currentThread().isInterrupted()) {
                LOG.debug(() -> "Check queue cycle started...");
                Collection<T> toBeProcessed;
                SimpleQueueProcessor.this.lock.lock();

                try {
                    if (SimpleQueueProcessor.this.queue.size() >= SimpleQueueProcessor.this.capacity || forceProcess) {
                        toBeProcessed = SimpleQueueProcessor.this.queue;
                        SimpleQueueProcessor.this.queue = SimpleQueueProcessor.this.collectionFactory.apply(SimpleQueueProcessor.this.capacity);
                        SimpleQueueProcessor.this.isEmpty.signalAll();
                    } else {
                        try {
                            SimpleQueueProcessor.this.isFull.await(
                                    SimpleQueueProcessor.this.timeout,
                                    SimpleQueueProcessor.this.timeUnit
                            );
                            forceProcess = true;
                            continue;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally {
                    SimpleQueueProcessor.this.lock.unlock();
                }

                if (!toBeProcessed.isEmpty()) {
                    LOG.info(() -> "Process incoming the queue...");
                    try {
                        SimpleQueueProcessor.this.consumer.accept(toBeProcessed);
                    } catch (Throwable e) {
                        LOG.error(() -> "An error occurred during queue processing!", e);
                        SimpleQueueProcessor.this.throwableConsumer.accept(e);
                    } finally {
                        LOG.info("Processing of the queue complete.");
                    }
                }

                forceProcess = false;
            }
        }
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    /**
     * Builder for {@link SimpleQueueProcessor}.
     */
    public static final class Builder<T> {


        public static final int DEFAULT_CAPACITY = 1000;

        public static final int DEFAULT_PROCESSOR_COUNT = 1;

        public static final long DEFAULT_TIMEOUT = 1000;

        public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;

        public static final CollectionFactory<?> DEFAULT_COLLECTION_FACTORY = ArrayList::new;

        public static final ThreadFactory DEFAULT_THREAD_FACTORY = new ThreadFactory() {
            private int index = 0;

            @Override
            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setName("simple-queue-processor-thread-" + this.index++);
                return thread;
            }
        };

        private Builder() {

        }

        private Consumer<Collection<T>> consumer;

        private Consumer<Throwable> throwableConsumer;

        private Integer capacity;

        private Long timeout;

        private TimeUnit timeUnit;

        private CollectionFactory<T> collectionFactory;

        private Integer processorCount;

        private ThreadFactory threadFactory;

        public Builder<T> consumer(Consumer<Collection<T>> consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("consumer can't ne null!");
            }

            this.consumer = consumer;
            return this;
        }

        /**
         * Sets the throwable consumer.
         *
         * @param throwableConsumer the consumer for exceptions
         * @return this builder
         */
        public Builder<T> throwableConsumer(Consumer<Throwable> throwableConsumer) {
            if (throwableConsumer == null) {
                throw new IllegalArgumentException("throwableConsumer can't ne null!");
            }

            this.throwableConsumer = throwableConsumer;
            return this;
        }

        /**
         * Sets the capacity of the queue.
         *
         * @param capacity the capacity
         * @return this builder
         */
        public Builder<T> capacity(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException(capacity + " is invalid value, capacity must be grater then 0!");
            }

            this.capacity = capacity;
            return this;
        }

        /**
         * Sets the timeout.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder<T> timeout(long timeout) {
            if (capacity <= 0) {
                throw new IllegalArgumentException(capacity + " is invalid value, timeout must be grater then 0!");
            }

            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the time unit.
         *
         * @param timeUnit the time unit
         * @return this builder
         */
        public Builder<T> timeUnit(TimeUnit timeUnit) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("timeUnit can't be null!");
            }

            this.timeUnit = timeUnit;
            return this;
        }

        /**
         * Sets the collection factory.
         *
         * @param collectionFactory the collection factory
         * @return this builder
         */
        public Builder<T> collectionFactory(CollectionFactory<T> collectionFactory) {
            if (collectionFactory == null) {
                throw new IllegalArgumentException("timeUnit can't be null!");
            }

            this.collectionFactory = collectionFactory;
            return this;
        }

        /**
         * Sets the processor count.
         *
         * @param processorCount the number of processors
         * @return this builder
         */
        public Builder<T> processorCount(int processorCount) {
            if (processorCount <= 0) {
                throw new IllegalArgumentException(processorCount + " is invalid value, processorCount must be grater then 0!");
            }

            this.processorCount = processorCount;
            return this;
        }

        /**
         * Sets the thread factory.
         *
         * @param threadFactory the thread factory
         * @return this builder
         */
        public Builder<T> threadFactory(ThreadFactory threadFactory) {
            if (threadFactory == null) {
                throw new IllegalArgumentException("threadFactory can't be null!");
            }

            this.threadFactory = threadFactory;
            return this;
        }

        public QueueProcessor<T> build() {
            if (this.consumer == null) {
                throw new IllegalStateException("Data consumer must be not null! Call `setConsumer`!");
            }

            if (this.throwableConsumer == null) {
                throw new IllegalStateException("Throwable consumer must be not null! Call `setThrowableConsumer`!");
            }


            return new SimpleQueueProcessor<T>(
                    this.consumer,
                    this.throwableConsumer,
                    this.capacity != null ? this.capacity : DEFAULT_CAPACITY,
                    this.timeout != null ? this.timeout : DEFAULT_TIMEOUT,
                    this.timeUnit != null ? this.timeUnit : DEFAULT_TIME_UNIT,
                    this.collectionFactory != null ? this.collectionFactory : (CollectionFactory<T>) DEFAULT_COLLECTION_FACTORY,
                    this.processorCount != null ? this.processorCount : DEFAULT_PROCESSOR_COUNT,
                    this.threadFactory != null ? this.threadFactory : DEFAULT_THREAD_FACTORY
            );
        }
    }

    @FunctionalInterface
    public interface CollectionFactory<T> extends Function<Integer, Collection<T>> {
    }
}
