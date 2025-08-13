package co.thismakesmehappy.toyapi.service.utils;

/**
 * Implementation of FeatureFlagService using existing ParameterStore infrastructure.
 * Zero additional cost - uses your current Parameter Store setup.
 * 
 * Feature flags are stored as:
 * /toyapi-{env}/features/{feature-name} = "true"/"false"
 * /toyapi-{env}/config/{config-name} = "value"
 * 
 * Change values instantly via:
 * - AWS Console: Systems Manager → Parameter Store
 * - AWS CLI: aws ssm put-parameter --name "/toyapi-prod/features/spam-detection" --value "true" --overwrite
 * - CDK: new StringParameter(stack, 'SpamDetection', { parameterName: '/toyapi-prod/features/spam-detection', stringValue: 'true' })
 */
public class ParameterStoreFeatureFlagService implements FeatureFlagService {
    
    private final ParameterStoreService parameterStore;
    
    public ParameterStoreFeatureFlagService(ParameterStoreService parameterStore) {
        this.parameterStore = parameterStore;
    }
    
    @Override
    public boolean isFeatureEnabled(String featureName, boolean defaultValue) {
        String value = parameterStore.getParameter("features/" + featureName, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(value.trim());
    }
    
    @Override
    public String getConfigValue(String configName, String defaultValue) {
        return parameterStore.getParameter("config/" + configName, defaultValue);
    }
    
    @Override
    public int getConfigValueAsInt(String configName, int defaultValue) {
        try {
            String value = parameterStore.getParameter("config/" + configName, String.valueOf(defaultValue));
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            // Log warning and return default
            System.err.println("Warning: Invalid integer value for config/" + configName + ", using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    @Override
    public double getConfigValueAsDouble(String configName, double defaultValue) {
        try {
            String value = parameterStore.getParameter("config/" + configName, String.valueOf(defaultValue));
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            // Log warning and return default
            System.err.println("Warning: Invalid double value for config/" + configName + ", using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    // Convenience methods with sensible defaults
    
    @Override
    public boolean isComprehensiveValidationEnabled() {
        return isFeatureEnabled("comprehensive-validation", true); // Default: enabled
    }
    
    @Override
    public boolean isEnhancedErrorMessagesEnabled() {
        return isFeatureEnabled("enhanced-error-messages", true); // Default: enabled
    }
    
    @Override
    public int getMaxItemsPerPage() {
        return getConfigValueAsInt("max-items-per-page", 50); // Default: 50
    }
    
    @Override
    public int getRateLimitPerMinute() {
        return getConfigValueAsInt("rate-limit-per-minute", 100); // Default: 100
    }
    
    @Override
    public boolean isRequestTracingEnabled() {
        return isFeatureEnabled("request-tracing", true); // Default: enabled
    }
    
    @Override
    public boolean isDetailedMetricsEnabled() {
        return isFeatureEnabled("detailed-metrics", false); // Default: disabled (can be expensive)
    }
    
    @Override
    public boolean isSpamDetectionEnabled() {
        return isFeatureEnabled("spam-detection", true); // Default: enabled
    }
    
    @Override
    public double getSpamDetectionThreshold() {
        return getConfigValueAsDouble("spam-threshold", 0.8); // Default: 80% confidence threshold
    }
}