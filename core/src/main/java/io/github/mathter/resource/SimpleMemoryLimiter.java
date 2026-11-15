package io.github.mathter.resource;

import io.github.mathter.resource.util.Logger;
import io.github.mathter.resource.util.LoggerFactory;

import java.lang.ref.Cleaner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A simple implementation of {@link MemoryLimiter} that blocks when memory usage
 * exceeds a certain percentage of available memory.
 *
 * <p>This implementation uses a background thread to monitor memory usage and
 * signals waiting threads when memory usage drops below a certain threshold.</p>
 */
public class SimpleMemoryLimiter implements MemoryLimiter {
    private static final Logger LOG = LoggerFactory.logger(SimpleMemoryLimiter.class);


    private static final Cleaner CLEANER = Cleaner.create();

    private final double blockPercentLevel;

    private final double unblockPercentLevel;

    private final MemoryInfo memoryInfo;

    private final Lock lock = new ReentrantLock();

    private final Condition condition = this.lock.newCondition();

    private final Cleaner.Cleanable cleanable;

    private final Thread checkThread;

    /**
     * Creates a builder for {@link SimpleMemoryLimiter}.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private SimpleMemoryLimiter(
            double blockPercentLevel,
            double unblockPercentLevel,
            MemoryInfo memoryInfo,
            long timeout,
            TimeUnit timeUnit
    ) {
        this.blockPercentLevel = blockPercentLevel;
        this.unblockPercentLevel = unblockPercentLevel;
        this.memoryInfo = memoryInfo;
        this.checkThread = new Thread(new Worker(timeUnit.toMillis(timeout)));
        this.checkThread.start();
        this.cleanable = CLEANER.register(this, () -> {
            LOG.info("Free resources of rate limiter.");
            this.checkThread.interrupt();
        });
    }

    @Override
    public void check() {
        while (!Thread.currentThread().isInterrupted()) {
            LOG.info("Check for available mem...");
            final long available = this.memoryInfo.available();
            final long used = this.memoryInfo.used();

            if ((double) used / available >= this.blockPercentLevel) {
                try {
                    LOG.info("Waiting for available mem...");
                    this.lock.lock();
                    this.condition.await();
                } catch (InterruptedException e) {
                } finally {
                    this.lock.unlock();
                }
            } else {
                LOG.info("Waiting of mem complete.");
                break;
            }
        }
    }

    @Override
    public boolean check(long timeout, TimeUnit timeUnit) throws InterruptedException {
        LOG.info(() -> "Check for available mem... timeout=" + timeout + " timeUnit=" + timeUnit);
        final long start = System.nanoTime();
        boolean result = false;

        try {
            while (this.lock.tryLock(timeout, timeUnit)) {
                final long available = this.memoryInfo.available();
                final long used = this.memoryInfo.used();
                if ((double) used / available >= this.blockPercentLevel) {
                    try {
                        final long timeout2 = timeout - timeUnit.convert(
                                System.nanoTime() - start,
                                TimeUnit.NANOSECONDS
                        );

                        LOG.info(() -> "Waiting for available mem alone timeout=" + timeout2);
                        if (timeout2 <= 0 || !this.condition.await(timeout2, timeUnit)) {
                            break;
                        }
                    } catch (InterruptedException e) {
                    }
                } else {
                    LOG.info(() -> "Unlock this thread.");
                    result = true;
                    break;
                }
            }
        } finally {
            this.lock.unlock();
        }

        final boolean r = result;
        LOG.info(() -> "Waiting of mem complete with result=" + r);
        return result;
    }

    @Override
    public void close() throws Exception {
        LOG.info(() -> "Close memory limiter.");
        if (this.memoryInfo instanceof AutoCloseable closeable) {
            closeable.close();
        }
        this.cleanable.clean();
    }

    /**
     * A worker thread that monitors memory usage and signals waiting threads
     * when the usage falls below the unblock threshold.
     */
    private class Worker implements Runnable {

        private final long timeout;

        public Worker(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                LOG.info(() -> "Check for available mem.");
                try {
                    final double available = SimpleMemoryLimiter.this.memoryInfo.available();
                    final double used = SimpleMemoryLimiter.this.memoryInfo.used();

                    if (used / available <= SimpleMemoryLimiter.this.unblockPercentLevel) {
                        SimpleMemoryLimiter.this.lock.lock();

                        try {
                            LOG.info("Unlock waiting threads.");
                            SimpleMemoryLimiter.this.condition.signalAll();
                        } finally {
                            SimpleMemoryLimiter.this.lock.unlock();
                        }
                    }

                    try {
                        Thread.sleep(this.timeout);
                    } catch (InterruptedException e) {
                        LOG.info("Checking for available mem interrupted.");
                    }
                } catch (Throwable t) {
                    LOG.error(() -> "An error occurred during the check for available mem!", t);
                }
            }
        }
    }

    /**
     * Builder for {@link SimpleMemoryLimiter}.
     */
    public static class Builder {

        public static final double DEFAULT_BLOCK_PERCENT_LEVEL = 20;

        public static final double DEFAULT_UNBLOCK_PERCENT_LEVEL = 30;

        public static final Supplier<MemoryInfo> DEFAULT_AVAILABLE_MEMMORY_CALC = () -> new AvarageMemoryInfo(
                10,
                100,
                TimeUnit.MILLISECONDS
        );

        public static final long DEFAULT_TIMEOUT = 1000;

        public static final TimeUnit DEFAULT_TIMEUNIT = TimeUnit.MILLISECONDS;

        private Double blockPercentLevel;

        private Double unblockPercentLevel;

        private MemoryInfo memoryInfo;

        private Long timeout;

        private TimeUnit timeUnit;

        public Builder blockPercentLevel(double blockPercentLevel) {
            if (blockPercentLevel <= 0) {
                throw new IllegalArgumentException(blockPercentLevel + " is invalid value, blockPercentLevel must be grater then 0!");
            }

            this.blockPercentLevel = blockPercentLevel;
            return this;
        }

        public Builder unblockPercentLevel(double unblockPercentLevel) {
            if (unblockPercentLevel <= 0) {
                throw new IllegalArgumentException(unblockPercentLevel + " is invalid value, unblockPercentLevel must be grater then 0!");
            }

            this.unblockPercentLevel = unblockPercentLevel;
            return this;
        }

        public Builder memmoryInfo(MemoryInfo memoryInfo) {
            this.memoryInfo = memoryInfo;
            return this;
        }

        public Builder timeUnit(TimeUnit timeUnit) {
            if (timeUnit == null) {
                throw new IllegalArgumentException("timeInit can't be null!");
            }

            this.timeUnit = timeUnit;
            return this;
        }

        public Builder timeout(long timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException(timeout + " is invalid value, timeout must be grater then 0!");
            }

            this.timeUnit = timeUnit;
            return this;
        }

        public MemoryLimiter build() {
            return new SimpleMemoryLimiter(
                    this.blockPercentLevel != null ? this.blockPercentLevel : DEFAULT_BLOCK_PERCENT_LEVEL,
                    this.unblockPercentLevel != null ? this.unblockPercentLevel : DEFAULT_UNBLOCK_PERCENT_LEVEL,
                    this.memoryInfo != null ? this.memoryInfo : DEFAULT_AVAILABLE_MEMMORY_CALC.get(),
                    this.timeout != null ? this.timeout : DEFAULT_TIMEOUT,
                    this.timeUnit != null ? this.timeUnit : DEFAULT_TIMEUNIT
            );
        }
    }
}
