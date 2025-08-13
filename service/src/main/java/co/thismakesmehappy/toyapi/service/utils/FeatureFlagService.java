package co.thismakesmehappy.toyapi.service.utils;

/**
 * Service for managing feature flags and configuration toggles.
 * Built on top of existing ParameterStore infrastructure with zero additional cost.
 * 
 * Feature flags are stored as parameters like:
 * /toyapi-{env}/features/comprehensive-validation = "true"
 * /toyapi-{env}/config/max-items-per-page = "50"
 * 
 * You can change these instantly via AWS Console or CLI.
 */
public interface FeatureFlagService {
    
    /**
     * Check if a feature is enabled.
     * 
     * @param featureName The feature flag name (e.g., "comprehensive-validation")
     * @param defaultValue Default value if flag is not set
     * @return true if feature is enabled
     */
    boolean isFeatureEnabled(String featureName, boolean defaultValue);
    
    /**
     * Get a configuration value as string.
     * 
     * @param configName The configuration name (e.g., "max-items-per-page")
     * @param defaultValue Default value if not set
     * @return Configuration value
     */
    String getConfigValue(String configName, String defaultValue);
    
    /**
     * Get a configuration value as integer.
     * 
     * @param configName The configuration name
     * @param defaultValue Default value if not set
     * @return Configuration value as integer
     */
    int getConfigValueAsInt(String configName, int defaultValue);
    
    /**
     * Get a configuration value as double.
     * 
     * @param configName The configuration name
     * @param defaultValue Default value if not set  
     * @return Configuration value as double
     */
    double getConfigValueAsDouble(String configName, double defaultValue);
    
    // Convenience methods for common feature flags
    
    /**
     * Check if comprehensive validation is enabled.
     * Parameter: /toyapi-{env}/features/comprehensive-validation
     * 
     * @return true if comprehensive validation should be used
     */
    boolean isComprehensiveValidationEnabled();
    
    /**
     * Check if enhanced error messages are enabled.
     * Parameter: /toyapi-{env}/features/enhanced-error-messages
     * 
     * @return true if enhanced error messages should be included
     */
    boolean isEnhancedErrorMessagesEnabled();
    
    /**
     * Get the maximum items per page for pagination.
     * Parameter: /toyapi-{env}/config/max-items-per-page
     * 
     * @return Maximum items per page (default: 50)
     */
    int getMaxItemsPerPage();
    
    /**
     * Get the rate limit per minute per user.
     * Parameter: /toyapi-{env}/config/rate-limit-per-minute
     * 
     * @return Rate limit per minute (default: 100)
     */
    int getRateLimitPerMinute();
    
    /**
     * Check if request tracing is enabled.
     * Parameter: /toyapi-{env}/features/request-tracing
     * 
     * @return true if requests should be traced
     */
    boolean isRequestTracingEnabled();
    
    /**
     * Check if detailed metrics collection is enabled.
     * Parameter: /toyapi-{env}/features/detailed-metrics
     * 
     * @return true if detailed metrics should be collected
     */
    boolean isDetailedMetricsEnabled();
    
    /**
     * Check if spam detection is enabled.
     * Parameter: /toyapi-{env}/features/spam-detection
     * 
     * @return true if spam detection should run
     */
    boolean isSpamDetectionEnabled();
    
    /**
     * Get spam detection threshold (0.0 to 1.0).
     * Parameter: /toyapi-{env}/config/spam-threshold
     * 
     * @return Spam detection threshold (default: 0.8)
     */
    double getSpamDetectionThreshold();
}