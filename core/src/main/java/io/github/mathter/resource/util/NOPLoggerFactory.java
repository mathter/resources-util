package io.github.mathter.resource.util;

import java.util.function.Supplier;

public class NOPLoggerFactory extends LoggerFactory {
    @Override
    protected Logger newInstance(Class<?> clazz) {
        return new Logger() {
            @Override
            public void info(Supplier<String> s) {
                // Do nothing.
            }

            @Override
            public void debug(Supplier<String> s) {
                // Do nothing.
            }

            @Override
            public void error(Supplier<String> s, Throwable t) {
                // Do nothing.
            }
        };
    }
}
