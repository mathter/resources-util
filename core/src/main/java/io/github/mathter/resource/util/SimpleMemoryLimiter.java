package io.github.mathter.resource.util;

import java.lang.ref.Cleaner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class SimpleMemoryLimiter implements MemoryLimiter {
    private static final Cleaner CLEANER = Cleaner.create();

    private final double blockPercentLevel;

    private final double unblockPercentLevel;

    private final MemoryInfo memoryInfo;

    private final Lock lock = new ReentrantLock();

    private final Condition condition = this.lock.newCondition();

    private final Cleaner.Cleanable cleanable;

    private final Thread checkThread;

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
        this.cleanable = CLEANER.register(this, () -> this.checkThread.interrupt());
    }

    @Override
    public void check() {
        while (!Thread.currentThread().isInterrupted()) {
            final long available = this.memoryInfo.available();
            final long used = this.memoryInfo.used();

            if ((double) used / available >= this.blockPercentLevel) {
                try {
                    this.lock.lock();
                    this.condition.await();
                } catch (InterruptedException e) {
                } finally {
                    this.lock.unlock();
                }
            } else {
                break;
            }
        }
    }

    @Override
    public boolean check(long timeout, TimeUnit timeUnit) throws InterruptedException {
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

                        if (timeout2 <= 0 || !this.condition.await(timeout2, timeUnit)) {
                            break;
                        }
                    } catch (InterruptedException e) {
                    }
                } else {
                    result = true;
                    break;
                }
            }
        } finally {
            this.lock.unlock();
        }

        return result;
    }

    @Override
    public void close() throws Exception {
        if (this.memoryInfo instanceof AutoCloseable closeable) {
            closeable.close();
        }
        this.cleanable.clean();
    }

    private class Worker implements Runnable {
        private final long timeout;

        public Worker(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final double available = SimpleMemoryLimiter.this.memoryInfo.available();
                    final double used = SimpleMemoryLimiter.this.memoryInfo.used();

                    if (used / available <= SimpleMemoryLimiter.this.unblockPercentLevel) {
                        SimpleMemoryLimiter.this.lock.lock();

                        try {
                            SimpleMemoryLimiter.this.condition.signalAll();
                        } finally {
                            SimpleMemoryLimiter.this.lock.unlock();
                        }
                    }

                    try {
                        Thread.sleep(this.timeout);
                    } catch (InterruptedException e) {
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

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
