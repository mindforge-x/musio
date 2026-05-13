package com.musio.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "musio.memory.preference-aggregation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicMemoryMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(DynamicMemoryMaintenanceService.class);

    private final MusicProfilePreferenceUpdater preferenceUpdater;

    public DynamicMemoryMaintenanceService(MusicProfilePreferenceUpdater preferenceUpdater) {
        this.preferenceUpdater = preferenceUpdater;
    }

    @Scheduled(
            initialDelayString = "${musio.memory.preference-aggregation.initial-delay-ms:60000}",
            fixedDelayString = "${musio.memory.preference-aggregation.fixed-delay-ms:900000}"
    )
    public void refreshPreferenceProfiles() {
        try {
            int updatedProfiles = preferenceUpdater.refreshAll();
            log.info("MEMORY_MAINTENANCE stage=preference_aggregation status=checked updatedProfiles={}", updatedProfiles);
        } catch (Exception e) {
            log.warn("Dynamic memory preference aggregation failed", e);
        }
    }
}
