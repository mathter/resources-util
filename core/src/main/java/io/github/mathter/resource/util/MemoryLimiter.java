package io.github.mathter.resource.util;

import java.util.concurrent.TimeUnit;

public interface MemoryLimiter extends AutoCloseable {
    public void check();

    public boolean check(long timeout, TimeUnit timeUnit) throws InterruptedException;
}
