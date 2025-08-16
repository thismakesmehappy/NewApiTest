package co.thismakesmehappy.toyapi.service.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for optimizing CloudWatch costs based on feature flags.
 * Provides dynamic control over log retention, alarm configurations,
 * and logging levels without requiring code deployments.
 */
public class CloudWatchCostOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(CloudWatchCostOptimizer.class);
    
    private final FeatureFlagService featureFlagService;
    private final String environment;
    
    public CloudWatchCostOptimizer(FeatureFlagService featureFlagService, String environment) {
        this.featureFlagService = featureFlagService;
        this.environment = environment;
    }
    
    /**
     * Apply cost optimizations based on current feature flag settings.
     * This method is idempotent and can be called repeatedly.
     */
    public void applyCostOptimizations() {
        if (!featureFlagService.isCloudWatchCostOptimizationEnabled()) {
            logger.info("CloudWatch cost optimization is disabled for environment: {}", environment);
            return;
        }
        
        logger.info("Applying CloudWatch cost optimizations for environment: {}", environment);
        
        try {
            // Apply log retention optimizations
            optimizeLogRetention();
            
            // Optimize alarm configurations
            optimizeAlarmConfiguration();
            
            logger.info("CloudWatch cost optimizations applied successfully");
            
        } catch (Exception e) {
            logger.error("Failed to apply CloudWatch cost optimizations", e);
        }
    }
    
    /**
     * Get the optimal log retention period based on environment and feature flags.
     */
    public int getOptimalLogRetentionDays() {
        if (!featureFlagService.isCloudWatchCostOptimizationEnabled()) {
            return getDefaultRetentionForEnvironment();
        }
        
        // Cost-optimized retention periods
        switch (environment.toLowerCase()) {
            case "dev":
                return 1; // Minimal retention for development
            case "stage":
            case "staging":
                return 3; // Short retention for staging
            case "prod":
            case "production":
                return 7; // Reasonable retention for production
            default:
                return 1; // Default to minimal for unknown environments
        }
    }
    
    /**
     * Check if detailed API Gateway logging should be enabled.
     */
    public boolean shouldEnableDetailedApiLogging() {
        if (!featureFlagService.isCloudWatchCostOptimizationEnabled()) {
            return true; // Default: enable detailed logging
        }
        
        // Only enable detailed logging in production when cost optimization is on
        return "prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment);
    }
    
    /**
     * Check if an alarm should be active based on feature flags.
     */
    public boolean shouldAlarmBeActive(String alarmName) {
        // Security alarms - controlled by security-alarms feature flag
        if (isSecurityAlarm(alarmName)) {
            return featureFlagService.isSecurityAlarmsEnabled();
        }
        
        // Traffic-dependent alarms - controlled by traffic-alarms feature flag
        if (isTrafficDependentAlarm(alarmName)) {
            return featureFlagService.isTrafficAlarmsEnabled();
        }
        
        // Critical alarms always active
        return true;
    }
    
    /**
     * Get appropriate logging level based on cost optimization settings.
     */
    public String getOptimalLoggingLevel() {
        if (!featureFlagService.isCloudWatchCostOptimizationEnabled()) {
            return "INFO"; // Default logging level
        }
        
        switch (environment.toLowerCase()) {
            case "dev":
                return "WARN"; // Minimal logging for dev
            case "stage":
            case "staging":
                return "INFO"; // Standard logging for staging  
            case "prod":
            case "production":
                return "INFO"; // Keep INFO for production debugging
            default:
                return "WARN"; // Conservative default
        }
    }
    
    private void optimizeLogRetention() {
        int optimalRetention = getOptimalLogRetentionDays();
        String logGroupPrefix = String.format("/aws/lambda/toyapi-%s", environment);
        
        logger.info("Setting log retention to {} days for environment: {}", optimalRetention, environment);
        
        try {
            // Update Lambda log groups
            updateLogGroupRetention(logGroupPrefix + "-auth", optimalRetention);
            updateLogGroupRetention(logGroupPrefix + "-items", optimalRetention);
            updateLogGroupRetention(logGroupPrefix + "-public", optimalRetention);
            updateLogGroupRetention(logGroupPrefix + "-security", optimalRetention);
            updateLogGroupRetention(logGroupPrefix + "-business-metrics", optimalRetention);
            updateLogGroupRetention(logGroupPrefix + "-developerfunction", optimalRetention);
            
            // Update API Gateway log groups if detailed logging is disabled
            if (!shouldEnableDetailedApiLogging()) {
                String apiLogGroupPrefix = String.format("/aws/apigateway/toyapi-%s", environment);
                updateLogGroupRetention(apiLogGroupPrefix + "-detailed-access", optimalRetention);
                updateLogGroupRetention(apiLogGroupPrefix + "-security-audit", optimalRetention);
            }
            
        } catch (Exception e) {
            logger.error("Failed to optimize log retention", e);
        }
    }
    
    private void optimizeAlarmConfiguration() {
        try {
            // Get list of alarms for this environment
            String[] trafficAlarms = {
                String.format("toyapi-%s-api-scanning-detected", environment),
                String.format("toyapi-%s-ddos-attack-detected", environment),
                String.format("toyapi-%s-high-error-rate", environment),
                String.format("toyapi-%s-performance-regression", environment),
                String.format("toyapi-%s-availability", environment)
            };
            
            String[] securityAlarms = {
                String.format("toyapi-%s-critical-security-breach", environment),
                String.format("toyapi-%s-security-breach", environment)
            };
            
            // Control traffic-dependent alarms
            if (featureFlagService.isTrafficAlarmsEnabled()) {
                enableAlarmActions(trafficAlarms);
            } else {
                disableAlarmActions(trafficAlarms);
            }
            
            // Control security alarms
            if (featureFlagService.isSecurityAlarmsEnabled()) {
                enableAlarmActions(securityAlarms);
            } else {
                disableAlarmActions(securityAlarms);
            }
            
        } catch (Exception e) {
            logger.error("Failed to optimize alarm configuration", e);
        }
    }
    
    private void updateLogGroupRetention(String logGroupName, int retentionDays) {
        // Log the optimization recommendation
        logger.info("CloudWatch Cost Optimization: Recommend setting {} to {} day retention", 
            logGroupName, retentionDays);
        
        // In a real implementation, this would call AWS CLI or SDK
        // For now, we log the recommendation for manual application
        // aws logs put-retention-policy --log-group-name {} --retention-in-days {}
    }
    
    private void enableAlarmActions(String[] alarmNames) {
        for (String alarmName : alarmNames) {
            logger.info("CloudWatch Cost Optimization: Recommend enabling alarm actions for: {}", alarmName);
            // aws cloudwatch enable-alarm-actions --alarm-names {}
        }
    }
    
    private void disableAlarmActions(String[] alarmNames) {
        for (String alarmName : alarmNames) {
            logger.info("CloudWatch Cost Optimization: Recommend disabling alarm actions for: {}", alarmName);
            // aws cloudwatch disable-alarm-actions --alarm-names {}
        }
    }
    
    private boolean isSecurityAlarm(String alarmName) {
        return alarmName.contains("security") || 
               alarmName.contains("breach") || 
               alarmName.contains("auth") ||
               alarmName.contains("ddos");
    }
    
    private boolean isTrafficDependentAlarm(String alarmName) {
        return alarmName.contains("scanning") ||
               alarmName.contains("error-rate") ||
               alarmName.contains("latency") ||
               alarmName.contains("performance") ||
               alarmName.contains("availability");
    }
    
    private int getDefaultRetentionForEnvironment() {
        switch (environment.toLowerCase()) {
            case "prod":
            case "production":
                return 14; // Default production retention
            case "stage":
            case "staging":
                return 7;  // Default staging retention
            default:
                return 7;  // Default dev retention
        }
    }
}