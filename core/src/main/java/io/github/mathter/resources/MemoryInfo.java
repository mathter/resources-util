package io.github.mathter.resources;

/**
 * An interface representing memory usage information.
 */
public interface MemoryInfo {
    /**
     * Returns the amount of used memory.
     *
     * @return the used memory in bytes
     */
    public long used();

    /**
     * Returns the amount of available memory.
     *
     * @return the available memory in bytes
     */
    public long available();
}
