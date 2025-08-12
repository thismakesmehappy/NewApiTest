package co.thismakesmehappy.toyapi.service.components.items;

import co.thismakesmehappy.toyapi.service.pipeline.DecorationException;
import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.MockDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Specialized service for item data decoration and enrichment.
 * Handles expensive operations like database lookups and external API calls.
 */
public class ItemDecorationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ItemDecorationService.class);
    
    private final DynamoDbService dynamoDbService;
    private final boolean useLocalMock;
    
    public ItemDecorationService(DynamoDbService dynamoDbService, boolean useLocalMock) {
        this.dynamoDbService = dynamoDbService;
        this.useLocalMock = useLocalMock;
    }
    
    /**
     * Enrich context for item creation with user information.
     */
    public ItemDecorationContext enrichForItemCreation(String userId) throws DecorationException {
        try {
            logger.debug("Enriching context for item creation, user: {}", userId);
            
            ItemDecorationContext context = new ItemDecorationContext();
            
            // Check if user exists (expensive operation)
            boolean userExists = checkUserExists(userId);
            context.setUserExists(userExists);
            
            // Get user's current item count (database query)
            int itemCount = getUserItemCount(userId);
            context.setUserItemCount(itemCount);
            
            // Get user preferences if user exists (additional enrichment)
            if (userExists) {
                UserPreferences preferences = getUserPreferences(userId);
                context.setUserPreferences(preferences);
            }
            
            logger.debug("Enrichment complete - User exists: {}, Item count: {}", 
                        userExists, itemCount);
            
            return context;
            
        } catch (Exception e) {
            logger.error("Decoration failed for user: {}", userId, e);
            throw new DecorationException("Failed to enrich item creation context", e);
        }
    }
    
    /**
     * Enrich context for item retrieval with data fetching and permissions.
     */
    public ItemRetrievalContext enrichForItemRetrieval(String userId, Integer limit, String sortOrder) throws DecorationException {
        try {
            logger.debug("Enriching context for item retrieval, user: {}", userId);
            
            ItemRetrievalContext context = new ItemRetrievalContext();
            
            // Phase 1: Verify user exists and permissions
            boolean userExists = checkUserExists(userId);
            boolean hasPermission = checkUserPermissions(userId);
            context.setUserExists(userExists);
            context.setHasPermission(hasPermission);
            
            // Phase 2: Fetch raw items from storage (expensive operation)
            if (userExists && hasPermission) {
                List<Map<String, Object>> rawItems = fetchRawItems(userId, limit, sortOrder);
                context.setRawItems(rawItems);
                
                // Phase 3: Enrich items with additional data
                List<Map<String, Object>> enrichedItems = enrichItems(rawItems, userId);
                context.setEnrichedItems(enrichedItems);
            }
            
            logger.debug("Retrieval enrichment complete - {} items found", 
                        context.getEnrichedItems() != null ? context.getEnrichedItems().size() : 0);
            
            return context;
            
        } catch (Exception e) {
            logger.error("Decoration failed for user: {}", userId, e);
            throw new DecorationException("Failed to enrich item retrieval context", e);
        }
    }
    
    private boolean checkUserExists(String userId) {
        // Mock implementation - in real system, this would check user service/database
        return !userId.equals("nonexistent-user");
    }
    
    private boolean checkUserPermissions(String userId) {
        // Mock implementation - in real system, check permissions service
        return !userId.equals("no-permission-user");
    }
    
    private int getUserItemCount(String userId) {
        if (useLocalMock) {
            return MockDatabase.getUserItems(userId).size();
        } else {
            // In real system, this would query the database for user's item count
            return 0; // Mock implementation
        }
    }
    
    private UserPreferences getUserPreferences(String userId) {
        // Mock implementation - in real system, fetch from user preferences service
        return new UserPreferences(userId, "default", true);
    }
    
    private List<Map<String, Object>> fetchRawItems(String userId, Integer limit, String sortOrder) {
        if (useLocalMock) {
            return MockDatabase.getUserItems(userId).stream()
                    .map(MockDatabase.Item::toMap)
                    .limit(limit != null ? limit : 20)
                    .toList();
        } else {
            // In real system, query DynamoDB with proper filtering and pagination
            // This would use the dynamoDbService to scan/query items
            return List.of(); // Mock implementation
        }
    }
    
    private List<Map<String, Object>> enrichItems(List<Map<String, Object>> rawItems, String userId) {
        // Enrich each item with computed fields and additional data
        return rawItems.stream()
                .map(item -> enrichSingleItem(item, userId))
                .toList();
    }
    
    private Map<String, Object> enrichSingleItem(Map<String, Object> item, String userId) {
        // Create enriched copy
        Map<String, Object> enriched = new java.util.HashMap<>(item);
        
        // Add computed fields
        enriched.put("itemAge", calculateItemAge(item));
        enriched.put("canEdit", canUserEditItem(item, userId));
        enriched.put("relatedItemCount", getRelatedItemCount(item));
        
        return enriched;
    }
    
    private String calculateItemAge(Map<String, Object> item) {
        // Mock implementation - could calculate based on createdAt
        return "recent";
    }
    
    private boolean canUserEditItem(Map<String, Object> item, String userId) {
        // Check if user owns the item
        return userId.equals(item.get("userId"));
    }
    
    private int getRelatedItemCount(Map<String, Object> item) {
        // Mock implementation - in real system, might query for related items
        return 0;
    }
    
    // Context objects to hold decoration results
    public static class ItemDecorationContext {
        private boolean userExists;
        private int userItemCount;
        private UserPreferences userPreferences;
        
        public boolean isUserExists() { return userExists; }
        public void setUserExists(boolean userExists) { this.userExists = userExists; }
        public int getUserItemCount() { return userItemCount; }
        public void setUserItemCount(int userItemCount) { this.userItemCount = userItemCount; }
        public UserPreferences getUserPreferences() { return userPreferences; }
        public void setUserPreferences(UserPreferences userPreferences) { this.userPreferences = userPreferences; }
    }
    
    public static class ItemRetrievalContext {
        private boolean userExists;
        private boolean hasPermission;
        private List<Map<String, Object>> rawItems;
        private List<Map<String, Object>> enrichedItems;
        
        public boolean isUserExists() { return userExists; }
        public void setUserExists(boolean userExists) { this.userExists = userExists; }
        public boolean isHasPermission() { return hasPermission; }
        public void setHasPermission(boolean hasPermission) { this.hasPermission = hasPermission; }
        public List<Map<String, Object>> getRawItems() { return rawItems; }
        public void setRawItems(List<Map<String, Object>> rawItems) { this.rawItems = rawItems; }
        public List<Map<String, Object>> getEnrichedItems() { return enrichedItems; }
        public void setEnrichedItems(List<Map<String, Object>> enrichedItems) { this.enrichedItems = enrichedItems; }
    }
    
    public static class UserPreferences {
        private final String userId;
        private final String theme;
        private final boolean notificationsEnabled;
        
        public UserPreferences(String userId, String theme, boolean notificationsEnabled) {
            this.userId = userId;
            this.theme = theme;
            this.notificationsEnabled = notificationsEnabled;
        }
        
        public String getUserId() { return userId; }
        public String getTheme() { return theme; }
        public boolean isNotificationsEnabled() { return notificationsEnabled; }
    }
}