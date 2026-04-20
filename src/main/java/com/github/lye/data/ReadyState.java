package com.github.lye.data;

public enum ReadyState {
    COLD,              // Base non connectée
    STORAGE_READY,     // MySQL / MapDB connectés
    COLLECTFIRST_READY // caches collect-first chargés
}
