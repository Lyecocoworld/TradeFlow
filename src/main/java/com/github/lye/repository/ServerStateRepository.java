package com.github.lye.repository;

import java.util.concurrent.CompletableFuture;

/**
 * Repository abstraction for server-wide economic state.
 * Implementations hide the underlying MySQL or alternative storage.
 * <p>
 * All methods return {@link CompletableFuture} to ensure JDBC I/O never blocks
 * a Folia region thread. Callers on region threads must chain callbacks via
 * {@code thenAccept} / {@code thenApply} rather than calling {@code join()}.
 */
public interface ServerStateRepository {

    /**
     * Retrieve a state value by key asynchronously.
     *
     * @param key state key
     * @return future that resolves to the state value or {@code null} if missing
     */
    CompletableFuture<String> getState(String key);

    /**
     * Persist a state value for the given key asynchronously.
     *
     * @param key   state key
     * @param value state value
     * @return future that completes when the write is done
     */
    CompletableFuture<Void> setState(String key, String value);
}

