package co.thismakesmehappy.toyapi.service.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests demonstrating feature flag functionality with Parameter Store.
 * Shows how to control application behavior through configuration.
 */
class FeatureFlagServiceTest {
    
    private MockParameterStoreService mockParameterStore;
    private ParameterStoreFeatureFlagService featureFlags;
    
    @BeforeEach
    void setUp() {
        mockParameterStore = new MockParameterStoreService();
        featureFlags = new ParameterStoreFeatureFlagService(mockParameterStore);
    }
    
    @Nested
    class FeatureFlagTests {
        
        @Test
        void testFeatureEnabledByDefault() {
            // When no parameter is set, use default
            boolean enabled = featureFlags.isFeatureEnabled("new-feature", true);
            assertTrue(enabled);
            
            boolean disabled = featureFlags.isFeatureEnabled("experimental-feature", false);
            assertFalse(disabled);
        }
        
        @Test
        void testFeatureControlledByParameterStore() {
            // Given - feature is enabled in Parameter Store
            mockParameterStore.setParameter("features/spam-detection", "true");
            
            // When
            boolean enabled = featureFlags.isFeatureEnabled("spam-detection", false);
            
            // Then
            assertTrue(enabled);
        }
        
        @Test
        void testFeatureCanBeDisabled() {
            // Given - feature is disabled in Parameter Store
            mockParameterStore.setParameter("features/comprehensive-validation", "false");
            
            // When
            boolean enabled = featureFlags.isComprehensiveValidationEnabled();
            
            // Then
            assertFalse(enabled);
        }
        
        @Test
        void testFeatureFlagIsCaseInsensitive() {
            // Given - mixed case values
            mockParameterStore.setParameter("features/test-feature", "TRUE");
            
            // When
            boolean enabled = featureFlags.isFeatureEnabled("test-feature", false);
            
            // Then
            assertTrue(enabled);
        }
    }
    
    @Nested
    class ConfigurationTests {
        
        @Test
        void testStringConfiguration() {
            // Given
            mockParameterStore.setParameter("config/api-version", "v2.1");
            
            // When
            String version = featureFlags.getConfigValue("api-version", "v1.0");
            
            // Then
            assertEquals("v2.1", version);
        }
        
        @Test
        void testIntegerConfiguration() {
            // Given
            mockParameterStore.setParameter("config/max-items-per-page", "75");
            
            // When
            int maxItems = featureFlags.getMaxItemsPerPage();
            
            // Then
            assertEquals(75, maxItems);
        }
        
        @Test
        void testIntegerConfigurationWithInvalidValue() {
            // Given - invalid integer value
            mockParameterStore.setParameter("config/rate-limit-per-minute", "not-a-number");
            
            // When
            int rateLimit = featureFlags.getRateLimitPerMinute();
            
            // Then - should use default
            assertEquals(100, rateLimit);
        }
        
        @Test
        void testDoubleConfiguration() {
            // Given
            mockParameterStore.setParameter("config/spam-threshold", "0.95");
            
            // When
            double threshold = featureFlags.getSpamDetectionThreshold();
            
            // Then
            assertEquals(0.95, threshold, 0.001);
        }
    }
    
    @Nested
    class ConvenienceMethodTests {
        
        @Test
        void testComprehensiveValidationFlag() {
            // Default should be enabled
            assertTrue(featureFlags.isComprehensiveValidationEnabled());
            
            // Can be disabled
            mockParameterStore.setParameter("features/comprehensive-validation", "false");
            assertFalse(featureFlags.isComprehensiveValidationEnabled());
        }
        
        @Test
        void testSpamDetectionConfiguration() {
            // Default behavior
            assertTrue(featureFlags.isSpamDetectionEnabled());
            assertEquals(0.8, featureFlags.getSpamDetectionThreshold(), 0.001);
            
            // Can be customized
            mockParameterStore.setParameter("features/spam-detection", "false");
            mockParameterStore.setParameter("config/spam-threshold", "0.9");
            
            assertFalse(featureFlags.isSpamDetectionEnabled());
            assertEquals(0.9, featureFlags.getSpamDetectionThreshold(), 0.001);
        }
        
        @Test
        void testRateLimitingConfiguration() {
            // Default
            assertEquals(100, featureFlags.getRateLimitPerMinute());
            
            // Can be adjusted
            mockParameterStore.setParameter("config/rate-limit-per-minute", "200");
            assertEquals(200, featureFlags.getRateLimitPerMinute());
        }
        
        @Test
        void testPaginationConfiguration() {
            // Default
            assertEquals(50, featureFlags.getMaxItemsPerPage());
            
            // Can be adjusted
            mockParameterStore.setParameter("config/max-items-per-page", "25");
            assertEquals(25, featureFlags.getMaxItemsPerPage());
        }
        
        @Test
        void testMonitoringFlags() {
            // Default behavior
            assertTrue(featureFlags.isRequestTracingEnabled());
            assertFalse(featureFlags.isDetailedMetricsEnabled()); // Expensive by default
            
            // Can be toggled
            mockParameterStore.setParameter("features/request-tracing", "false");
            mockParameterStore.setParameter("features/detailed-metrics", "true");
            
            assertFalse(featureFlags.isRequestTracingEnabled());
            assertTrue(featureFlags.isDetailedMetricsEnabled());
        }
    }
    
    @Nested
    class RealWorldScenarios {
        
        @Test
        void testGradualFeatureRollout() {
            // Scenario: Rolling out comprehensive validation
            
            // Stage 1: Feature disabled (current state)
            mockParameterStore.setParameter("features/comprehensive-validation", "false");
            assertFalse(featureFlags.isComprehensiveValidationEnabled());
            
            // Stage 2: Enable for testing
            mockParameterStore.setParameter("features/comprehensive-validation", "true");
            assertTrue(featureFlags.isComprehensiveValidationEnabled());
            
            // Stage 3: If issues occur, instant rollback
            mockParameterStore.setParameter("features/comprehensive-validation", "false");
            assertFalse(featureFlags.isComprehensiveValidationEnabled());
        }
        
        @Test
        void testPerformanceTuning() {
            // Scenario: Adjusting performance settings based on load
            
            // High load - reduce limits
            mockParameterStore.setParameter("config/max-items-per-page", "25");
            mockParameterStore.setParameter("config/rate-limit-per-minute", "50");
            mockParameterStore.setParameter("features/detailed-metrics", "false");
            
            assertEquals(25, featureFlags.getMaxItemsPerPage());
            assertEquals(50, featureFlags.getRateLimitPerMinute());
            assertFalse(featureFlags.isDetailedMetricsEnabled());
            
            // Normal load - restore defaults
            mockParameterStore.setParameter("config/max-items-per-page", "50");
            mockParameterStore.setParameter("config/rate-limit-per-minute", "100");
            
            assertEquals(50, featureFlags.getMaxItemsPerPage());
            assertEquals(100, featureFlags.getRateLimitPerMinute());
        }
        
        @Test
        void testSecurityConfiguration() {
            // Scenario: Adjusting security settings
            
            // Increase security
            mockParameterStore.setParameter("features/spam-detection", "true");
            mockParameterStore.setParameter("config/spam-threshold", "0.7"); // More sensitive
            mockParameterStore.setParameter("features/enhanced-error-messages", "false"); // Less info disclosure
            
            assertTrue(featureFlags.isSpamDetectionEnabled());
            assertEquals(0.7, featureFlags.getSpamDetectionThreshold(), 0.001);
            assertFalse(featureFlags.isEnhancedErrorMessagesEnabled());
        }
    }
}