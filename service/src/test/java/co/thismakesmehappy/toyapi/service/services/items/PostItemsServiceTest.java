package co.thismakesmehappy.toyapi.service.services.items;

import co.thismakesmehappy.toyapi.service.pipeline.*;
import co.thismakesmehappy.toyapi.service.utils.MockDynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.MockDatabase;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PostItemsService.
 * Tests the clean, well-architected service without "Pipeline" naming.
 */
class PostItemsServiceTest {

    private MockDynamoDbService mockDynamoDbService;
    private PostItemsService itemsService;

    @BeforeEach
    void setUp() {
        mockDynamoDbService = new MockDynamoDbService();
        mockDynamoDbService.clearTable();
        MockDatabase.clear();
        itemsService = new PostItemsService(mockDynamoDbService, "test-table", true);
    }

    @Nested
    class FullServiceTests {
        
        @Test
        void testSuccessfulItemCreation() throws Exception {
            // Given
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("Test item message", "user-123");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then
            assertNotNull(response);
            assertNotNull(response.get("id"));
            assertEquals("Test item message", response.get("message"));
            assertEquals("user-123", response.get("userId"));
            assertNotNull(response.get("createdAt"));
            assertNotNull(response.get("updatedAt"));
            assertNotNull(response.get("requestId"));
            
            // Verify persistence occurred
            assertEquals(1, mockDynamoDbService.getTableSize());
        }
        
        @Test
        void testInputValidationFailure() {
            // Given - Empty message
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("", "user-123");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("Message is required and cannot be empty"));
            
            // Verify no persistence occurred
            assertEquals(0, mockDynamoDbService.getTableSize());
        }
        
        @Test
        void testBusinessValidationFailure() {
            // Given - Non-existent user (triggers business validation failure)
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("Test message", "nonexistent-user");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("User does not exist: nonexistent-user"));
            
            // Verify no persistence occurred
            assertEquals(0, mockDynamoDbService.getTableSize());
        }
        
        @Test
        void testSpamContentRejection() {
            // Given - Message with spam content
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("This is spam content", "user-123");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("Message contains prohibited content"));
            
            // Verify no persistence occurred
            assertEquals(0, mockDynamoDbService.getTableSize());
        }
        
        @Test
        void testMessageLengthValidation() {
            // Given - Message too long
            String longMessage = "a".repeat(1001);
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest(longMessage, "user-123");
            
            // When & Then
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            assertTrue(exception.getMessage().contains("Message cannot exceed 1000 characters"));
        }
    }
    
    @Nested 
    class ComprehensiveValidationTests {
        
        @Test
        void testComprehensiveInputValidation() {
            // Given - Multiple validation issues
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("", "");
            
            // When
            ValidationResult result = itemsService.validateInputComprehensive(request);
            
            // Then
            assertFalse(result.isValid());
            assertEquals(2, result.getErrors().size());
            
            // Should have both message and userId errors
            String allErrors = result.getAllErrorsAsString();
            assertTrue(allErrors.contains("Message is required"));
            assertTrue(allErrors.contains("User ID is required"));
        }
        
        @Test
        void testInputValidationWithWarnings() {
            // Given - Message with warning trigger
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("urgent message", "user-123");
            
            // When
            ValidationResult result = itemsService.validateInputComprehensive(request);
            
            // Then
            assertTrue(result.isValid()); // Should be valid despite warning
            assertTrue(result.hasWarnings());
            assertEquals(1, result.getWarnings().size());
            assertTrue(result.getAllWarningsAsString().contains("urgent"));
        }
    }
    
    @Nested
    class ServiceArchitectureTests {
        
        @Test
        void testServiceUsesEnhancedPipelineArchitecture() throws Exception {
            // Verify the service extends ServicePipeline (architecture verification)
            assertTrue(itemsService instanceof ServicePipeline);
            
            // Verify service follows specialized component pattern
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("Test message", "user-123");
            
            Map<String, Object> response = itemsService.execute(request);
            
            // Should have all expected response fields
            assertNotNull(response.get("id"));
            assertEquals("Test message", response.get("message"));
            assertEquals("user-123", response.get("userId"));
            assertNotNull(response.get("requestId"));
        }
        
        @Test
        void testPersistenceRequirement() {
            // Verify this service correctly indicates it requires persistence
            assertTrue(itemsService.requiresPersistence());
        }
    }
    
    @Nested
    class ErrorHandlingTests {
        
        @Test
        void testExceptionContextEnrichment() {
            // Given - Request that will fail
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("Test message", "nonexistent-user");
            
            // When
            ValidationException exception = assertThrows(ValidationException.class, () -> {
                itemsService.execute(request);
            });
            
            // Then - Exception should have context information
            assertTrue(exception instanceof ServicePipelineException);
            ServicePipelineException pipelineException = (ServicePipelineException) exception;
            
            // Should have request context
            assertNotNull(pipelineException.getContext("requestId"));
            assertEquals("create_item", pipelineException.getContext("operation"));
        }
    }
    
    @Nested
    class MetadataAndMonitoringTests {
        
        @Test
        void testRequestTracking() throws Exception {
            // Given
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("Test message", "user-123");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then - Response should include request ID for tracing
            assertNotNull(response.get("requestId"));
            assertTrue(response.get("requestId").toString().startsWith("req-"));
        }
        
        @Test
        void testWarningHandling() throws Exception {
            // Given
            PostItemsService.CreateItemRequest request = 
                new PostItemsService.CreateItemRequest("content with warning keyword", "user-123");
            
            // When
            Map<String, Object> response = itemsService.execute(request);
            
            // Then - Should succeed but may include warnings in metadata
            assertNotNull(response);
            assertEquals("content with warning keyword", response.get("message"));
        }
    }
}