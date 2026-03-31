package com.github.lye.repository;

import com.github.lye.license.PlayerLicense;
import java.util.UUID;

public interface LicenseRepository {
    
    void saveLicense(PlayerLicense license);
    
    PlayerLicense getLicense(UUID playerUuid);
    
    void deleteLicense(UUID playerUuid);
    
    boolean hasLicense(UUID playerUuid);
}
