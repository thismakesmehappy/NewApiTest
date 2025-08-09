package co.thismakesmehappy.toyapi.service.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DynamoDB service implementations.
 * Tests both the mock implementation and interfaces.
 */
class DynamoDbServiceTest {

    private MockDynamoDbService mockService;

    @BeforeEach
    void setUp() {
        mockService = new MockDynamoDbService();
    }

    @Test
    void testPutAndGetItem() {
        // Create test item
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s("test-123").build());
        item.put("name", AttributeValue.builder().s("Test Item").build());
        
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("test-table")
                .item(item)
                .build();
        
        // Put the item
        PutItemResponse putResponse = mockService.putItem(putRequest);
        assertNotNull(putResponse);
        assertEquals(1, mockService.getTableSize());
        
        // Get the item
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s("test-123").build());
        
        GetItemRequest getRequest = GetItemRequest.builder()
                .tableName("test-table")
                .key(key)
                .build();
        
        GetItemResponse getResponse = mockService.getItem(getRequest);
        assertNotNull(getResponse);
        assertFalse(getResponse.item().isEmpty(), "Item should exist and not be empty");
        assertEquals("Test Item", getResponse.item().get("name").s());
    }

    @Test
    void testUpdateItem() {
        // First put an item
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s("test-456").build());
        item.put("count", AttributeValue.builder().n("1").build());
        
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("test-table")
                .item(item)
                .build();
        mockService.putItem(putRequest);
        
        // Update the item
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s("test-456").build());
        
        Map<String, AttributeValueUpdate> updates = new HashMap<>();
        updates.put("count", AttributeValueUpdate.builder()
                .action(AttributeAction.PUT)
                .value(AttributeValue.builder().n("5").build())
                .build());
        
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("test-table")
                .key(key)
                .attributeUpdates(updates)
                .build();
        
        UpdateItemResponse updateResponse = mockService.updateItem(updateRequest);
        assertNotNull(updateResponse);
        
        // Verify the update
        GetItemRequest getRequest = GetItemRequest.builder()
                .tableName("test-table")
                .key(key)
                .build();
        
        GetItemResponse getResponse = mockService.getItem(getRequest);
        assertEquals("5", getResponse.item().get("count").n());
    }

    @Test
    void testDeleteItem() {
        // Put an item
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s("test-delete").build());
        
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("test-table")
                .item(item)
                .build();
        mockService.putItem(putRequest);
        assertEquals(1, mockService.getTableSize());
        
        // Delete the item
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s("test-delete").build());
        
        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName("test-table")
                .key(key)
                .build();
        
        DeleteItemResponse deleteResponse = mockService.deleteItem(deleteRequest);
        assertNotNull(deleteResponse);
        assertEquals(0, mockService.getTableSize());
    }

    @Test
    void testQueryAndScan() {
        // Put some test items
        for (int i = 0; i < 3; i++) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("id", AttributeValue.builder().s("item-" + i).build());
            item.put("data", AttributeValue.builder().s("value-" + i).build());
            
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName("test-table")
                    .item(item)
                    .build();
            mockService.putItem(putRequest);
        }
        
        assertEquals(3, mockService.getTableSize());
        
        // Test query (mock returns all items)
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName("test-table")
                .build();
        
        QueryResponse queryResponse = mockService.query(queryRequest);
        assertNotNull(queryResponse);
        assertEquals(3, queryResponse.count());
        assertEquals(3, queryResponse.items().size());
        
        // Test scan
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("test-table")
                .build();
        
        ScanResponse scanResponse = mockService.scan(scanRequest);
        assertNotNull(scanResponse);
        assertEquals(3, scanResponse.count());
        assertEquals(3, scanResponse.items().size());
    }

    @Test
    void testExceptionHandling() {
        // Configure mock to throw exception
        mockService.setThrowException(true, "Test exception");
        
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s("test-exception").build());
        
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("test-table")
                .item(item)
                .build();
        
        // Should throw DynamoDbException
        assertThrows(DynamoDbException.class, () -> mockService.putItem(putRequest));
        
        // Reset exception handling
        mockService.setThrowException(false, null);
        
        // Should work normally now
        assertDoesNotThrow(() -> mockService.putItem(putRequest));
        assertEquals(1, mockService.getTableSize());
    }

    @Test
    void testClearTable() {
        // Add some items
        for (int i = 0; i < 5; i++) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("id", AttributeValue.builder().s("clear-test-" + i).build());
            
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName("test-table")
                    .item(item)
                    .build();
            mockService.putItem(putRequest);
        }
        
        assertEquals(5, mockService.getTableSize());
        
        // Clear the table
        mockService.clearTable();
        assertEquals(0, mockService.getTableSize());
    }
}