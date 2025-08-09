package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Mock implementation of DynamoDbService for unit testing.
 * Provides in-memory storage without AWS dependencies.
 */
public class MockDynamoDbService implements DynamoDbService {
    
    private final Map<String, Map<String, AttributeValue>> mockTable = new HashMap<>();
    private boolean throwException = false;
    private String exceptionMessage = "Mock DynamoDB exception";
    
    /**
     * Configures the mock to throw exceptions for testing error conditions.
     * 
     * @param throwException Whether to throw exceptions
     * @param message Exception message to use
     */
    public void setThrowException(boolean throwException, String message) {
        this.throwException = throwException;
        this.exceptionMessage = message;
    }
    
    /**
     * Clears the mock table data.
     */
    public void clearTable() {
        mockTable.clear();
    }
    
    /**
     * Gets the current size of the mock table.
     * 
     * @return The number of items in the mock table
     */
    public int getTableSize() {
        return mockTable.size();
    }
    
    @Override
    public PutItemResponse putItem(PutItemRequest request) {
        if (throwException) {
            throw DynamoDbException.builder().message(exceptionMessage).build();
        }
        
        String tableName = request.tableName();
        Map<String, AttributeValue> item = request.item();
        
        // Use deterministic key generation for testing (look for common key names)
        String key = generateKey(item);
        mockTable.put(key, item);
        
        return PutItemResponse.builder().build();
    }
    
    @Override
    public GetItemResponse getItem(GetItemRequest request) {
        if (throwException) {
            throw DynamoDbException.builder().message(exceptionMessage).build();
        }
        
        String tableName = request.tableName();
        Map<String, AttributeValue> key = request.key();
        
        // Use the same key generation logic as put
        String keyString = generateKey(key);
        Map<String, AttributeValue> item = mockTable.get(keyString);
        
        return GetItemResponse.builder()
                .item(item != null ? item : new HashMap<>())
                .build();
    }
    
    @Override
    public UpdateItemResponse updateItem(UpdateItemRequest request) {
        if (throwException) {
            throw DynamoDbException.builder().message(exceptionMessage).build();
        }
        
        // Simplified update - just replace the item
        String tableName = request.tableName();
        Map<String, AttributeValue> key = request.key();
        String keyString = generateKey(key);
        
        // Apply attribute updates (simplified)
        Map<String, AttributeValue> existingItem = mockTable.get(keyString);
        Map<String, AttributeValue> item = existingItem != null ? new HashMap<>(existingItem) : new HashMap<>(key);
        
        if (request.attributeUpdates() != null) {
            request.attributeUpdates().forEach((attrName, attrUpdate) -> {
                if (attrUpdate.action() == AttributeAction.PUT && attrUpdate.value() != null) {
                    item.put(attrName, attrUpdate.value());
                }
            });
        }
        
        mockTable.put(keyString, item);
        
        return UpdateItemResponse.builder().build();
    }
    
    @Override
    public DeleteItemResponse deleteItem(DeleteItemRequest request) {
        if (throwException) {
            throw DynamoDbException.builder().message(exceptionMessage).build();
        }
        
        String tableName = request.tableName();
        Map<String, AttributeValue> key = request.key();
        String keyString = generateKey(key);
        
        mockTable.remove(keyString);
        
        return DeleteItemResponse.builder().build();
    }
    
    @Override
    public QueryResponse query(QueryRequest request) {
        if (throwException) {
            throw DynamoDbException.builder().message(exceptionMessage).build();
        }
        
        // Simplified query - return all items (mock behavior)
        List<Map<String, AttributeValue>> items = new ArrayList<>(mockTable.values());
        
        return QueryResponse.builder()
                .items(items)
                .count(items.size())
                .build();
    }
    
    @Override
    public ScanResponse scan(ScanRequest request) {
        if (throwException) {
            throw DynamoDbException.builder().message(exceptionMessage).build();
        }
        
        // Return all items from mock table
        List<Map<String, AttributeValue>> items = new ArrayList<>(mockTable.values());
        
        return ScanResponse.builder()
                .items(items)
                .count(items.size())
                .build();
    }
    
    /**
     * Generates a deterministic key for mock storage.
     * Looks for common key attributes in a predictable order.
     */
    private String generateKey(Map<String, AttributeValue> attributes) {
        // Try common key names in order
        String[] commonKeys = {"id", "pk", "key", "recordId", "developerId", "metricType"};
        
        for (String keyName : commonKeys) {
            AttributeValue value = attributes.get(keyName);
            if (value != null && value.s() != null) {
                return keyName + ":" + value.s();
            }
        }
        
        // Fall back to first available string attribute
        for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
            if (entry.getValue().s() != null) {
                return entry.getKey() + ":" + entry.getValue().s();
            }
        }
        
        // Fallback to first available attribute (any type)
        Map.Entry<String, AttributeValue> firstEntry = attributes.entrySet().iterator().next();
        String value = firstEntry.getValue().s() != null ? firstEntry.getValue().s() : 
                      firstEntry.getValue().n() != null ? firstEntry.getValue().n() : "null";
        return firstEntry.getKey() + ":" + value;
    }
}