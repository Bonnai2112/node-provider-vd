package com.ceticgroup.cloud.nodeprovider.logtriage.config;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Confidence;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.PathAllowlist;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.log-triage.safety")
public record SafetyProperties(
        boolean enabled,
        int dailyMergeRequestQuota,
        int confidenceThresholdPercent,
        List<String> allowedPathPrefixes) {

    public SafetyProperties {
        if (dailyMergeRequestQuota < 0) {
            throw new IllegalArgumentException("dailyMergeRequestQuota must be >= 0");
        }
        if (confidenceThresholdPercent < 0 || confidenceThresholdPercent > 100) {
            throw new IllegalArgumentException("confidenceThresholdPercent must be in [0,100]");
        }
        if (allowedPathPrefixes == null || allowedPathPrefixes.isEmpty()) {
            throw new IllegalArgumentException("allowedPathPrefixes must not be empty");
        }
        allowedPathPrefixes = List.copyOf(allowedPathPrefixes);
    }

    public PathAllowlist toPathAllowlist() {
        return new PathAllowlist(allowedPathPrefixes);
    }

    public Confidence toConfidenceThreshold() {
        return Confidence.of(confidenceThresholdPercent);
    }
}
