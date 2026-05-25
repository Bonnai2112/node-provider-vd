package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.safety;

import com.ceticgroup.cloud.nodeprovider.logtriage.config.SafetyProperties;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out.FeatureTogglePort;
import java.util.Objects;

public final class ConfigFeatureToggle implements FeatureTogglePort {

    private final SafetyProperties properties;

    public ConfigFeatureToggle(SafetyProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public boolean isLogTriageEnabled() {
        return properties.enabled();
    }
}
