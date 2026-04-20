package com.github.lye.util;

import com.google.gson.Gson;

/**
 * Shared Gson singleton. Gson is thread-safe and designed to be reused;
 * creating a new instance per class wastes heap and metaspace.
 */
public final class GsonShared {
    public static final Gson INSTANCE = new Gson();

    private GsonShared() {}
}
