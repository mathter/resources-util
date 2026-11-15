package io.github.mathter.resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SimpleMemoryLimiterTest {
    @Test
    public void testCheck() throws InterruptedException, ExecutionException {
        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();

        SuperObject superObject = allocate((long) (0.9 * memoryUsage.getMax()));

        final CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(() -> {
            try (MemoryLimiter memoryLimiter = SimpleMemoryLimiter.builder()
                    .unblockPercentLevel(0.7)
                    .blockPercentLevel(0.8)
                    .build()) {
                memoryLimiter.check();
            } catch (Exception e) {
            }
            return true;
        });

        Thread.sleep(100);
        superObject = null;
        System.gc();
        completableFuture.join();
        Assertions.assertTrue(completableFuture.get());
    }

    @Test
    public void testTimedCheck() throws InterruptedException, ExecutionException {
        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();

        SuperObject superObject = allocate((long) (0.9 * memoryUsage.getMax()));

        final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
            try (MemoryLimiter memoryLimiter = SimpleMemoryLimiter.builder()
                    .unblockPercentLevel(0.7)
                    .blockPercentLevel(0.8)
                    .build()) {
                Assertions.assertFalse(memoryLimiter.check(100, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
            }
        });

        completableFuture.join();
    }

    @Test
    public void testTimedCheck2() throws InterruptedException, ExecutionException {
        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();

        SuperObject superObject = allocate((long) (0.9 * memoryUsage.getMax()));

        final CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(() -> {
            try (MemoryLimiter memoryLimiter = SimpleMemoryLimiter.builder()
                    .unblockPercentLevel(0.7)
                    .blockPercentLevel(0.8)
                    .build()) {
                Assertions.assertTrue(memoryLimiter.check(100, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
            }
            return true;
        });

        superObject = null;
        System.gc();
        completableFuture.join();
    }

    private static SuperObject allocate(long size) {
        final int SIZE = Integer.MAX_VALUE / 2;
        final byte[][] buf;

        if (size <= SIZE) {
            buf = new byte[1][(int) size];
        } else {
            final int count = (int) (size / SIZE);
            final int tail = (int) (size % SIZE);

            if (tail == 0) {
                buf = new byte[count][];
                for (int i = 0; i < count; i++) {
                    buf[i] = new byte[SIZE];
                }
            } else {
                buf = new byte[count + 1][];
                for (int i = 0; i < count; i++) {
                    buf[i] = new byte[SIZE];
                }

                buf[buf.length - 1] = new byte[tail];
            }
        }

        return new SuperObject(buf);
    }

    private static class SuperObject {
        final byte[][] mem;

        public SuperObject(byte[][] mem) {
            this.mem = mem;
        }
    }
}
