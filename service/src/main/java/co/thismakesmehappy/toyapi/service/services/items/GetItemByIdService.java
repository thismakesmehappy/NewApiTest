package co.thismakesmehappy.toyapi.service.services.items;

import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.MockDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for GET /items/{id} endpoint.
 * Follows Amazon/Coral pattern - one service per endpoint.
 */
public class GetItemByIdService {
    
    private static final Logger logger = LoggerFactory.getLogger(GetItemByIdService.class);
    
    private final DynamoDbService dynamoDbService;
    private final String tableName;
    private final boolean useLocalMock;
    
    public GetItemByIdService(DynamoDbService dynamoDbService, String tableName, boolean useLocalMock) {
        this.dynamoDbService = dynamoDbService;
        this.tableName = tableName;
        this.useLocalMock = useLocalMock;
    }
    
    /**
     * Execute the GET /items/{id} operation.
     * 
     * @param itemId The item ID from the path
     * @param userId The authenticated user ID (for access control)
     * @return Response map containing the item
     * @throws Exception if item not found, access forbidden, or retrieval fails
     */
    public Map<String, Object> execute(String itemId, String userId) throws Exception {
        logger.info("Getting item {} for user: {}", itemId, userId);
        
        if (useLocalMock) {
            return getItemWithMock(itemId, userId);
        } else {
            return getItemWithDynamoDB(itemId, userId);
        }
    }
    
    /**
     * Get specific item using local mock database.
     */
    private Map<String, Object> getItemWithMock(String itemId, String userId) throws Exception {
        Optional<MockDatabase.Item> itemOpt = MockDatabase.getItem(itemId, userId);
        if (!itemOpt.isPresent()) {
            throw new RuntimeException("Item not found");
        }
        
        MockDatabase.Item item = itemOpt.get();
        Map<String, Object> itemMap = item.toMap();
        
        // User access already checked by MockDatabase.getItem
        
        logger.info("Retrieved item from mock database: {}", itemId);
        return itemMap;
    }
    
    /**
     * Get specific item using DynamoDB.
     */
    private Map<String, Object> getItemWithDynamoDB(String itemId, String userId) throws Exception {
        try {
            Map<String, AttributeValue> keyAttributes = new HashMap<>();
            keyAttributes.put("id", AttributeValue.builder().s(itemId).build());
            
            GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(keyAttributes)
                .build();
            
            GetItemResponse response = dynamoDbService.getItem(request);
            
            if (response.item() == null || response.item().isEmpty()) {
                throw new RuntimeException("Item not found");
            }
            
            Map<String, Object> item = convertDynamoItemToMap(response.item());
            
            // Check user access
            if (!userId.equals(item.get("userId"))) {
                throw new SecurityException("Forbidden - user can only access their own items");
            }
            
            logger.info("Retrieved item from DynamoDB: {}", itemId);
            return item;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to get item from DynamoDB", e);
            throw new Exception("Failed to retrieve item");
        }
    }
    
    /**
     * Convert DynamoDB item attributes to a plain Map.
     */
    private Map<String, Object> convertDynamoItemToMap(Map<String, AttributeValue> dynamoItem) {
        Map<String, Object> item = new HashMap<>();
        
        for (Map.Entry<String, AttributeValue> entry : dynamoItem.entrySet()) {
            String key = entry.getKey();
            AttributeValue value = entry.getValue();
            
            if (value.s() != null) {
                item.put(key, value.s());
            } else if (value.n() != null) {
                item.put(key, value.n());
            } else if (value.bool() != null) {
                item.put(key, value.bool());
            }
            // Add more type conversions as needed
        }
        
        return item;
    }
}