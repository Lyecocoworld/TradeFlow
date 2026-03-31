package com.github.lye.repository;

/**
 * Repository abstraction for server-wide economic state.
 * Implementations hide the underlying MySQL or alternative storage.
 */
public interface ServerStateRepository {

    /**
     * Retrieve a state value by key.
     *
     * @param key state key
     * @return state value or {@code null} if missing
     */
    String getState(String key);

    /**
     * Persist a state value for the given key.
     *
     * @param key   state key
     * @param value state value
     */
    void setState(String key, String value);
}

