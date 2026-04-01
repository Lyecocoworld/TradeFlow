package com.github.lye.repository;

import com.github.lye.license.PlayerLicense;
import com.github.lye.util.TradeFlowLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MapDBLicenseRepository implements LicenseRepository {

    // Using a simple map backed by MapDB logic in Database class
    private final Map<UUID, PlayerLicense> licensesMap;
    private final TradeFlowLogger logger;

    public MapDBLicenseRepository(Map<UUID, PlayerLicense> licensesMap, TradeFlowLogger logger) {
        this.licensesMap = licensesMap;
        this.logger = logger;
    }

    @Override
    public void saveLicense(PlayerLicense license) {
        licensesMap.put(license.getPlayerUuid(), license);
    }

    @Override
    public PlayerLicense getLicense(UUID playerUuid) {
        return licensesMap.get(playerUuid);
    }

    @Override
    public void deleteLicense(UUID playerUuid) {
        licensesMap.remove(playerUuid);
    }

    @Override
    public boolean hasLicense(UUID playerUuid) {
        return licensesMap.containsKey(playerUuid);
    }
}
