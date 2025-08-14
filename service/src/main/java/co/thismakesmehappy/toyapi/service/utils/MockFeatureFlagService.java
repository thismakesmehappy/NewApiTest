package co.thismakesmehappy.toyapi.service.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock implementation of FeatureFlagService for testing.
 * Allows tests to control feature flags without AWS dependencies.
 */
public class MockFeatureFlagService implements FeatureFlagService {
    
    private final Map<String, String> features = new HashMap<>();
    private final Map<String, String> configs = new HashMap<>();
    
    /**
     * Set a feature flag for testing.
     * 
     * @param featureName Feature name
     * @param enabled Whether feature is enabled
     */
    public void setFeatureFlag(String featureName, boolean enabled) {
        features.put(featureName, String.valueOf(enabled));
    }
    
    /**
     * Set a config value for testing.
     * 
     * @param configName Config name
     * @param value Config value
     */
    public void setConfigValue(String configName, String value) {
        configs.put(configName, value);
    }
    
    /**
     * Clear all flags and configs.
     */
    public void clear() {
        features.clear();
        configs.clear();
    }
    
    @Override
    public boolean isFeatureEnabled(String featureName, boolean defaultValue) {
        String value = features.get(featureName);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }
    
    @Override
    public String getConfigValue(String configName, String defaultValue) {
        return configs.getOrDefault(configName, defaultValue);
    }
    
    @Override
    public int getConfigValueAsInt(String configName, int defaultValue) {
        try {
            String value = configs.get(configName);
            return value != null ? Integer.parseInt(value.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public double getConfigValueAsDouble(String configName, double defaultValue) {
        try {
            String value = configs.get(configName);
            return value != null ? Double.parseDouble(value.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public boolean isComprehensiveValidationEnabled() {
        return isFeatureEnabled("comprehensive-validation", true);
    }
    
    @Override
    public boolean isEnhancedErrorMessagesEnabled() {
        return isFeatureEnabled("enhanced-error-messages", true);
    }
    
    @Override
    public int getMaxItemsPerPage() {
        return getConfigValueAsInt("max-items-per-page", 50);
    }
    
    @Override
    public int getRateLimitPerMinute() {
        return getConfigValueAsInt("rate-limit-per-minute", 100);
    }
    
    @Override
    public boolean isRequestTracingEnabled() {
        return isFeatureEnabled("request-tracing", true);
    }
    
    @Override
    public boolean isDetailedMetricsEnabled() {
        return isFeatureEnabled("detailed-metrics", false);
    }
    
    @Override
    public boolean isSpamDetectionEnabled() {
        return isFeatureEnabled("spam-detection", true);
    }
    
    @Override
    public double getSpamDetectionThreshold() {
        return getConfigValueAsDouble("spam-threshold", 0.8);
    }
    
    @Override
    public boolean isCustomDomainsEnabled() {
        return isFeatureEnabled("custom-domains", false); // Default: disabled to control costs
    }
}