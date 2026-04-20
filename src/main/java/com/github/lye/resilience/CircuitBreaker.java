package com.github.lye.resilience;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Circuit Breaker implementation for protecting external services.
 * <p>
 * Prevents cascading failures by:
 * <ul>
 *   <li>Stopping requests to failing services</li>
 *   <li>Allowing retries after a cooldown period</li>
 *   <li>Recording metrics for monitoring</li>
 * </ul>
 *
 * States: CLOSED → OPEN → HALF_OPEN → CLOSED
 *
 * @author lye
 * @since 0.1
 */
public class CircuitBreaker {

    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());

    private final String name;
    private final int failureThreshold;
    private final long openTimeoutMillis;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicBoolean halfOpenPermit = new AtomicBoolean(false);
    private volatile State state = State.CLOSED;
    private volatile long lastFailureTimeMillis;
    private volatile long circuitOpenedTimeMillis;

    /**
     * Creates a new circuit breaker.
     *
     * @param name the circuit breaker name (for logging)
     * @param failureThreshold the number of failures before opening
     * @param openTimeoutMillis how long to stay open before trying again
     */
    public CircuitBreaker(String name, int failureThreshold, long openTimeoutMillis) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openTimeoutMillis = openTimeoutMillis;
    }

    /**
     * Executes an operation with circuit breaker protection.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws CircuitBreakerOpenException if the circuit is open
     * @throws Exception if the operation fails
     */
    public <T> T execute(Supplier<T> operation) throws Exception {
        if (!tryAcquire()) {
            throw new CircuitBreakerOpenException(name, state);
        }

        long startTime = System.currentTimeMillis();
        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    /**
     * Executes an operation with circuit breaker protection (void version).
     *
     * @param operation the operation to execute
     * @throws CircuitBreakerOpenException if the circuit is open
     * @throws Exception if the operation fails
     */
    public void execute(Runnable operation) throws Exception {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * Tries to acquire permission to execute an operation.
     *
     * @return true if operation should proceed, false if circuit is open
     */
    public boolean tryAcquire() {
        State currentState = state;

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                long timeSinceOpened = System.currentTimeMillis() - circuitOpenedTimeMillis;
                if (timeSinceOpened >= openTimeoutMillis) {
                    // Atomically transition to HALF_OPEN and acquire the single test permit.
                    // compareAndSet ensures only ONE thread wins the permit even under concurrency.
                    if (halfOpenPermit.compareAndSet(false, true)) {
                        LOGGER.info("Circuit breaker '" + name + "' transitioning to HALF_OPEN");
                        state = State.HALF_OPEN;
                        successCount.set(0);
                        return true;
                    }
                    // Another thread already acquired the HALF_OPEN permit
                    return false;
                }
                return false;

            case HALF_OPEN:
                // Only allow ONE request through to test if service has recovered (fixes C5).
                return halfOpenPermit.compareAndSet(false, true);

            default:
                return false;
        }
    }

    /**
     * Records a successful operation.
     */
    private void onSuccess() {
        failureCount.set(0);

        if (state == State.HALF_OPEN) {
            // Transition to CLOSED
            LOGGER.info("Circuit breaker '" + name + "' transitioning to CLOSED");
            state = State.CLOSED;
            halfOpenPermit.set(false);
        }
    }

    /**
     * Records a failed operation.
     *
     * @param durationMillis operation duration in milliseconds
     */
    private void onFailure(long durationMillis) {
        int failures = failureCount.incrementAndGet();
        lastFailureTimeMillis = System.currentTimeMillis();

        if (state == State.HALF_OPEN) {
            // Failed during test → re-open immediately
            LOGGER.warning("Circuit breaker '" + name + "' re-opened after HALF_OPEN failure");
            state = State.OPEN;
            circuitOpenedTimeMillis = System.currentTimeMillis();
            halfOpenPermit.set(false);
        } else if (failures >= failureThreshold) {
            // Transition to OPEN
            LOGGER.warning("Circuit breaker '" + name + "' opened after " + failures + " failures");
            circuitOpenedTimeMillis = System.currentTimeMillis();
            state = State.OPEN;
        }
    }

    /**
     * Gets the current state of the circuit breaker.
     *
     * @return the current state
     */
    public State getState() {
        return state;
    }

    /**
     * Gets the number of consecutive failures.
     *
     * @return the failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Resets the circuit breaker to CLOSED state.
     */
    public void reset() {
        LOGGER.info("Circuit breaker '" + name + "' manually reset to CLOSED");
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        halfOpenPermit.set(false);
        circuitOpenedTimeMillis = 0;
    }

    /**
     * Gets metrics about the circuit breaker.
     *
     * @return a string with metrics
     */
    public String getMetrics() {
        return String.format("CircuitBreaker[name=%s, state=%s, failures=%d, threshold=%d]",
                name, state, failureCount.get(), failureThreshold);
    }

    /**
     * Circuit breaker states.
     */
    public enum State {
        /** Circuit is closed, requests pass through */
        CLOSED,
        /** Circuit is open, requests are blocked */
        OPEN,
        /** Circuit is half-open, testing if service has recovered */
        HALF_OPEN
    }

    /**
     * Exception thrown when circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends Exception {
        private final State state;

        public CircuitBreakerOpenException(String name, State state) {
            super(String.format("Circuit breaker '%s' is %s", name, state));
            this.state = state;
        }

        public State getState() {
            return state;
        }
    }

    /**
     * Builder for CircuitBreaker.
     */
    public static class Builder {
        private String name = "default";
        private int failureThreshold = 5;
        private long openTimeoutMillis = TimeUnit.SECONDS.toMillis(30);

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder failureThreshold(int threshold) {
            this.failureThreshold = threshold;
            return this;
        }

        public Builder openTimeout(long timeout, TimeUnit unit) {
            this.openTimeoutMillis = unit.toMillis(timeout);
            return this;
        }

        public CircuitBreaker build() {
            return new CircuitBreaker(name, failureThreshold, openTimeoutMillis);
        }
    }

    /**
     * Creates a new circuit breaker builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
