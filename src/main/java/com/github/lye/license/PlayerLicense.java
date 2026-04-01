package com.github.lye.license;

import java.io.Serializable;
import java.util.UUID;

public class PlayerLicense implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID playerUuid;
    private final String licenseId;
    private final long expiresAt; // Timestamp

    public PlayerLicense(UUID playerUuid, String licenseId, long expiresAt) {
        this.playerUuid = playerUuid;
        this.licenseId = licenseId;
        this.expiresAt = expiresAt;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getLicenseId() { return licenseId; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
