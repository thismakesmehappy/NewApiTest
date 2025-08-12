package co.thismakesmehappy.toyapi.service.services.items;

import co.thismakesmehappy.toyapi.service.pipeline.*;
import co.thismakesmehappy.toyapi.service.utils.MockDynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.MockDatabase;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for GetItemsService.
 * Tests the clean, well-architected service without "Pipeline" naming.
 */
class GetItemsServiceTest {

    private MockDynamoDbService mockDynamoDbService;
    private GetItemsService itemsService;

    @BeforeEach
    void setUp() {
        mockDynamoDbService = new MockDynamoDbService();
        mockDynamoDbService.clearTable();
        MockDatabase.clear();
        itemsService = new GetItemsService(mockDynamoDbService, "test-table", true);
        
        // Add some test data
        MockDatabase.createItem("user-123", "First item");
        MockDatabase.createItem("user-123", "Second item");
        MockDatabase.createItem("user-456", "Other user item");
    }

    @Nested
    class FullServiceTests {
        
        @Test
        void testSuccessfulItemRetrieval() throws Exception {
            // Given
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "desc");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then
            assertNotNull(response);
            assertNotNull(response.get("items"));
            assertNotNull(response.get("count"));
            assertNotNull(response.get("requestId"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            
            // Should have 2 items for user-123 (filtered by user)
            assertTrue(items.size() >= 2);
            
            // Verify all items belong to user-123
            for (Map<String, Object> item : items) {
                assertEquals("user-123", item.get("userId"));
            }
        }
        
        @Test
        void testBusinessValidationForNonExistentUser() {
            // Given - nonexistent-user triggers business validation failure
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("nonexistent-user", null, null, "desc");
            
            // When & Then - Should fail business validation
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("User does not exist: nonexistent-user"));
        }
        
        @Test
        void testInputValidationFailure() {
            // Given - Empty user ID
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("", null, null, "desc");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("User ID is required"));
        }
        
        @Test
        void testInvalidSortOrder() {
            // Given - Invalid sort order
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "invalid");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("Sort order must be"));
        }
        
        @Test
        void testInvalidLimit() {
            // Given - Limit too large (validation service limits to 100)
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", 101, null, "desc");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("Limit must be between 1 and 100"));
        }
    }
    
    @Nested 
    class ComprehensiveValidationTests {
        
        @Test
        void testComprehensiveInputValidation() {
            // Given - Multiple validation issues
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("", 101, null, "invalid");
            
            // When
            ValidationResult result = itemsService.validateInputComprehensive(request);
            
            // Then
            assertFalse(result.isValid());
            assertTrue(result.getErrors().size() >= 2);
            
            // Should have multiple errors
            String allErrors = result.getAllErrorsAsString();
            assertTrue(allErrors.contains("User ID is required"));
            assertTrue(allErrors.contains("Limit must be between 1 and 100") || allErrors.contains("Sort order must be"));
        }
        
        @Test
        void testInputValidationWithWarnings() {
            // Given - Valid request that might trigger warnings
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", 100, null, "desc");
            
            // When
            ValidationResult result = itemsService.validateInputComprehensive(request);
            
            // Then
            assertTrue(result.isValid()); // Should be valid
            // May or may not have warnings - depends on business rules
        }
    }
    
    @Nested
    class ServiceArchitectureTests {
        
        @Test
        void testServiceUsesEnhancedPipelineArchitecture() throws Exception {
            // Verify the service extends ServicePipeline (architecture verification)
            assertTrue(itemsService instanceof ServicePipeline);
            
            // Verify service follows specialized component pattern
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "desc");
            
            Map<String, Object> response = itemsService.execute(request);
            
            // Should have all expected response fields
            assertNotNull(response.get("items"));
            assertNotNull(response.get("count"));
            assertNotNull(response.get("requestId"));
        }
        
        @Test
        void testPersistenceRequirement() {
            // Verify this service correctly indicates it does NOT require persistence (read operation)
            assertFalse(itemsService.requiresPersistence());
        }
    }
    
    @Nested
    class DataFilteringTests {
        
        @Test
        void testUserIsolation() throws Exception {
            // Given - Request from user-123
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "desc");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            
            // Should only contain items from user-123, not user-456
            for (Map<String, Object> item : items) {
                assertEquals("user-123", item.get("userId"));
                assertNotEquals("user-456", item.get("userId"));
            }
        }
        
        @Test
        void testBusinessRulesFiltering() throws Exception {
            // Given
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "desc");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then - Should have metadata about filtering
            assertNotNull(response.get("metadata"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
            assertNotNull(metadata.get("rawItemCount"));
            assertNotNull(metadata.get("enrichedItemCount"));
            assertNotNull(metadata.get("filteredItemCount"));
        }
    }
    
    @Nested
    class MetadataAndMonitoringTests {
        
        @Test
        void testRequestTracking() throws Exception {
            // Given
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "desc");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then - Response should include request ID for tracing
            assertNotNull(response.get("requestId"));
            assertTrue(response.get("requestId").toString().startsWith("req-"));
        }
        
        @Test
        void testPipelineMetadata() throws Exception {
            // Given
            GetItemsService.GetItemsRequest request = 
                new GetItemsService.GetItemsRequest("user-123", null, null, "desc");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then - Should have pipeline metadata
            assertNotNull(response.get("metadata"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
            
            // Verify pipeline tracking metadata
            assertTrue(metadata.containsKey("rawItemCount"));
            assertTrue(metadata.containsKey("enrichedItemCount"));
            assertTrue(metadata.containsKey("filteredItemCount"));
        }
    }
}