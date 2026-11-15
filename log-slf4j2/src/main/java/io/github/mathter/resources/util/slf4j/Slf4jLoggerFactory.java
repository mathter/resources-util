package io.github.mathter.resources.util.slf4j;

import io.github.mathter.resources.util.Logger;
import io.github.mathter.resources.util.LoggerFactory;

import java.util.function.Supplier;

public class Slf4jLoggerFactory extends LoggerFactory {
    @Override
    protected Logger newInstance(Class<?> clazz) {
        final org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(clazz);
        return new Logger() {
            @Override
            public void info(Supplier<String> s) {
                if (slf4jLogger.isInfoEnabled()) {
                    slf4jLogger.info(s.get());
                }
            }

            @Override
            public void info(String s) {
                if (slf4jLogger.isInfoEnabled()) {
                    slf4jLogger.info(s);
                }
            }

            @Override
            public void debug(Supplier<String> s) {
                if (slf4jLogger.isDebugEnabled()) {
                    slf4jLogger.debug(s.get());
                }
            }

            @Override
            public void debug(String s) {
                if (slf4jLogger.isDebugEnabled()) {
                    slf4jLogger.debug(s);
                }
            }

            @Override
            public void error(Supplier<String> s, Throwable t) {
                if (slf4jLogger.isErrorEnabled()) {
                    slf4jLogger.error(s.get(), t);
                }
            }

            @Override
            public void error(String s, Throwable t) {
                if (slf4jLogger.isErrorEnabled()) {
                    slf4jLogger.error(s, t);
                }
            }
        };
    }
}
