package io.github.mathter.resources.util;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * An abstract factory for creating {@link Logger} instances.
 *
 * <p>This factory uses {@link java.util.ServiceLoader} to find and instantiate
 * available {@link LoggerFactory} implementations. Only one implementation
 * (not including {@link NOPLoggerFactory}) must be present in the classpath.</p>
 */
public abstract class LoggerFactory {

    private static LoggerFactory loggerFactory;

    private static LoggerFactory emptyLoggerFactory;

    static {
        final ServiceLoader<LoggerFactory> serviceLoader = ServiceLoader.load(LoggerFactory.class);
        final List<LoggerFactory> factories = new ArrayList<>();
        for (LoggerFactory factory : serviceLoader) {
            if (factory instanceof NOPLoggerFactory) {
                emptyLoggerFactory = factory;
            } else {
                factories.add(factory);
            }
        }

        if (factories.size() == 1) {
            loggerFactory = factories.get(0);
        } else {
            System.out.println((">>> There are more than one logger factory! Factories: " + factories));
        }
    }

    public static Logger logger(Class<?> clazz) {
        if (loggerFactory != null) {
            return loggerFactory.newInstance(clazz);
        } else {
            return new NOPLoggerFactory().newInstance(clazz);
        }
    }

    protected abstract Logger newInstance(Class<?> clazz);
}
