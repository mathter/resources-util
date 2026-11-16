package io.github.mathter.resources.util.slf4j;

import io.github.mathter.resources.util.Logger;
import io.github.mathter.resources.util.LoggerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class Slf4jLoggerFactoryTest {

    @Test
    void testNewInstance() {
        Slf4jLoggerFactory factory = new Slf4jLoggerFactory();
        Logger logger = factory.newInstance(Slf4jLoggerFactoryTest.class);
        assertNotNull(logger);
    }

    @Test
    void testLoggingOutput() {
        Slf4jLoggerFactory factory = new Slf4jLoggerFactory();
        Logger logger = factory.newInstance(Slf4jLoggerFactoryTest.class);
        logger.info("Testing Slf4jLoggerFactory output");
        logger.debug("Testing Slf4jLoggerFactory debug output");
        logger.error("Testing Slf4jLoggerFactory error output", new RuntimeException("Test exception"));
    }
}
