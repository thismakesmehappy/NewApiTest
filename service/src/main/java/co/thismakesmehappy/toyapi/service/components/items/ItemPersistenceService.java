package co.thismakesmehappy.toyapi.service.components.items;

import co.thismakesmehappy.toyapi.service.pipeline.PersistenceException;
import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized service for item persistence operations.
 * Handles data storage concerns separately from business logic.
 */
public class ItemPersistenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ItemPersistenceService.class);
    
    private final DynamoDbService dynamoDbService;
    private final String tableName;
    
    public ItemPersistenceService(DynamoDbService dynamoDbService, String tableName) {
        this.dynamoDbService = dynamoDbService;
        this.tableName = tableName;
    }
    
    /**
     * Save a new item to persistent storage.
     */
    public void saveItem(String itemId, String userId, String message, Instant createdAt, Instant updatedAt) 
            throws PersistenceException {
        
        try {
            logger.debug("Persisting item {} for user {}", itemId, userId);
            
            // Use composite key structure that matches the actual DynamoDB table schema
            Map<String, AttributeValue> itemAttributes = new HashMap<>();
            itemAttributes.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            itemAttributes.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            itemAttributes.put("id", AttributeValue.builder().s(itemId).build());
            itemAttributes.put("userId", AttributeValue.builder().s(userId).build());
            itemAttributes.put("message", AttributeValue.builder().s(message).build());
            itemAttributes.put("createdAt", AttributeValue.builder().s(createdAt.toString()).build());
            itemAttributes.put("updatedAt", AttributeValue.builder().s(updatedAt.toString()).build());
            
            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemAttributes)
                .build();
            
            dynamoDbService.putItem(request);
            
            logger.info("Item persisted successfully: {}", itemId);
            
        } catch (Exception e) {
            logger.error("Persistence failed for item: {}", itemId, e);
            throw new PersistenceException("Failed to persist item: " + itemId, e);
        }
    }
    
    /**
     * Update an existing item in persistent storage.
     */
    public void updateItem(String itemId, String userId, String message, Instant updatedAt) 
            throws PersistenceException {
        
        try {
            logger.debug("Updating item {} for user {}", itemId, userId);
            
            // In a real implementation, this would use UpdateItem with proper conditions
            // For now, we'll use PutItem with all required fields
            Map<String, AttributeValue> itemAttributes = new HashMap<>();
            itemAttributes.put("id", AttributeValue.builder().s(itemId).build());
            itemAttributes.put("userId", AttributeValue.builder().s(userId).build());
            itemAttributes.put("message", AttributeValue.builder().s(message).build());
            itemAttributes.put("updatedAt", AttributeValue.builder().s(updatedAt.toString()).build());
            
            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemAttributes)
                .build();
            
            dynamoDbService.putItem(request);
            
            logger.info("Item updated successfully: {}", itemId);
            
        } catch (Exception e) {
            logger.error("Update failed for item: {}", itemId, e);
            throw new PersistenceException("Failed to update item: " + itemId, e);
        }
    }
    
    /**
     * Soft delete an item (mark as deleted without removing).
     */
    public void softDeleteItem(String itemId, String userId) throws PersistenceException {
        
        try {
            logger.debug("Soft deleting item {} for user {}", itemId, userId);
            
            // In a real implementation, this would use UpdateItem to set deleted=true
            // For now, we'll simulate with a PutItem that includes the deleted flag
            Map<String, AttributeValue> itemAttributes = new HashMap<>();
            itemAttributes.put("id", AttributeValue.builder().s(itemId).build());
            itemAttributes.put("userId", AttributeValue.builder().s(userId).build());
            itemAttributes.put("deleted", AttributeValue.builder().bool(true).build());
            itemAttributes.put("updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());
            
            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemAttributes)
                .build();
            
            dynamoDbService.putItem(request);
            
            logger.info("Item soft deleted successfully: {}", itemId);
            
        } catch (Exception e) {
            logger.error("Soft delete failed for item: {}", itemId, e);
            throw new PersistenceException("Failed to soft delete item: " + itemId, e);
        }
    }
    
    /**
     * Archive an item (mark as archived but keep accessible).
     */
    public void archiveItem(String itemId, String userId) throws PersistenceException {
        
        try {
            logger.debug("Archiving item {} for user {}", itemId, userId);
            
            Map<String, AttributeValue> itemAttributes = new HashMap<>();
            itemAttributes.put("id", AttributeValue.builder().s(itemId).build());
            itemAttributes.put("userId", AttributeValue.builder().s(userId).build());
            itemAttributes.put("archived", AttributeValue.builder().bool(true).build());
            itemAttributes.put("updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());
            
            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemAttributes)
                .build();
            
            dynamoDbService.putItem(request);
            
            logger.info("Item archived successfully: {}", itemId);
            
        } catch (Exception e) {
            logger.error("Archive failed for item: {}", itemId, e);
            throw new PersistenceException("Failed to archive item: " + itemId, e);
        }
    }
}