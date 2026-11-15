package io.github.mathter.resource;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link MemoryInfo} that provides an average of memory usage
 * over a period of time.
 *
 * <p>This implementation uses a background thread to periodically poll the
 * {@link java.lang.management.MemoryMXBean} and maintain a series of recent
 * memory usage measurements.</p>
 */
public class AvarageMemoryInfo implements MemoryInfo, AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private static int THREAD_INDEX = 0;

    private final long[] series;

    private final Thread thread;

    private final long timeout;

    private final Cleaner.Cleanable cleanable;

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    private int index = 0;

    public AvarageMemoryInfo(int length, long timeout, TimeUnit timeUnit) {
        this.series = new long[length];
        this.timeout = timeUnit.toMillis(timeout);
        this.thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final MemoryUsage memoryUsage = this.memoryMXBean.getHeapMemoryUsage();
                    this.series[this.index++ % this.series.length] = memoryUsage.getUsed();
                    Thread.sleep(this.timeout);
                } catch (InterruptedException e) {
                    // Do nothing.
                }
            }
        });
        this.thread.setName("avarage-memory-info-thread-" + THREAD_INDEX++);
        this.thread.start();
        this.cleanable = CLEANER.register(this, this.thread::interrupt);

        Arrays.fill(this.series, this.memoryMXBean.getHeapMemoryUsage().getUsed());
    }

    @Override
    public long used() {
        long result = this.series[0];

        for (int i = 1; i < this.series.length; i++) {
            result += this.series[i];
        }

        return result / this.series.length;
    }

    @Override
    public long available() {
        final MemoryUsage memoryUsage = this.memoryMXBean.getHeapMemoryUsage();
        final long max = memoryUsage.getMax();
        final long commited = memoryUsage.getCommitted();

        return max > 0 ? max : commited;
    }

    @Override
    public void close() throws Exception {
        this.cleanable.clean();
    }
}