package co.thismakesmehappy.toyapi.service.components.items;

import co.thismakesmehappy.toyapi.service.pipeline.ServiceContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Factory for creating pipeline contexts for item operations.
 * Centralizes context creation logic and ensures consistency.
 */
public class ItemContextFactory {
    
    /**
     * Create context for item creation pipeline.
     */
    public static ItemCreationContext createItemCreationContext(String message, String userId) {
        ItemCreationContext context = new ItemCreationContext();
        
        // Basic data from request
        context.setMessage(message != null ? message.trim() : null);
        context.setUserId(userId);
        context.setItemId(generateItemId());
        
        // Timestamps
        Instant now = Instant.now();
        context.setCreatedAt(now);
        context.setUpdatedAt(now);
        
        // Add tracing metadata
        context.addMetadata("operation", "create_item");
        context.addMetadata("userId", userId);
        
        return context;
    }
    
    /**
     * Create context for item retrieval pipeline.
     */
    public static ItemRetrievalContext createItemRetrievalContext(
            String userId, Integer limit, String lastEvaluatedKey, String sortOrder) {
        
        ItemRetrievalContext context = new ItemRetrievalContext();
        
        // Request parameters
        context.setUserId(userId);
        context.setLimit(limit != null ? limit : 20); // Default limit
        context.setLastEvaluatedKey(lastEvaluatedKey);
        context.setSortOrder(sortOrder != null ? sortOrder.toLowerCase() : "desc"); // Default sort
        
        // Add tracing metadata
        context.addMetadata("operation", "get_items");
        context.addMetadata("userId", userId);
        context.addMetadata("limit", context.getLimit());
        
        return context;
    }
    
    /**
     * Create context for single item retrieval pipeline.
     */
    public static ItemRetrievalContext createGetItemContext(String itemId, String userId) {
        ItemRetrievalContext context = new ItemRetrievalContext();
        
        // Request parameters
        context.setUserId(userId);
        context.addMetadata("itemId", itemId);
        
        // Add tracing metadata
        context.addMetadata("operation", "get_item");
        context.addMetadata("userId", userId);
        
        return context;
    }
    
    private static String generateItemId() {
        return "item-" + UUID.randomUUID().toString();
    }
    
    /**
     * Context for item creation operations.
     */
    public static class ItemCreationContext extends ServiceContext {
        private String message;
        private String userId;
        private String itemId;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean userExists;
        private int userItemCount;
        private ItemDecorationService.UserPreferences userPreferences;
        
        // Getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
        
        public boolean isUserExists() { return userExists; }
        public void setUserExists(boolean userExists) { this.userExists = userExists; }
        
        public int getUserItemCount() { return userItemCount; }
        public void setUserItemCount(int userItemCount) { this.userItemCount = userItemCount; }
        
        public ItemDecorationService.UserPreferences getUserPreferences() { return userPreferences; }
        public void setUserPreferences(ItemDecorationService.UserPreferences userPreferences) { 
            this.userPreferences = userPreferences; 
        }
    }
    
    /**
     * Context for item retrieval operations.
     */
    public static class ItemRetrievalContext extends ServiceContext {
        private String userId;
        private Integer limit;
        private String lastEvaluatedKey;
        private String sortOrder;
        private boolean userExists;
        private boolean hasPermission;
        private java.util.List<java.util.Map<String, Object>> rawItems;
        private java.util.List<java.util.Map<String, Object>> enrichedItems;
        private String nextToken;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        
        public String getLastEvaluatedKey() { return lastEvaluatedKey; }
        public void setLastEvaluatedKey(String lastEvaluatedKey) { this.lastEvaluatedKey = lastEvaluatedKey; }
        
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
        
        public boolean isUserExists() { return userExists; }
        public void setUserExists(boolean userExists) { this.userExists = userExists; }
        
        public boolean isHasPermission() { return hasPermission; }
        public void setHasPermission(boolean hasPermission) { this.hasPermission = hasPermission; }
        
        public java.util.List<java.util.Map<String, Object>> getRawItems() { return rawItems; }
        public void setRawItems(java.util.List<java.util.Map<String, Object>> rawItems) { this.rawItems = rawItems; }
        
        public java.util.List<java.util.Map<String, Object>> getEnrichedItems() { return enrichedItems; }
        public void setEnrichedItems(java.util.List<java.util.Map<String, Object>> enrichedItems) { 
            this.enrichedItems = enrichedItems; 
        }
        
        public String getNextToken() { return nextToken; }
        public void setNextToken(String nextToken) { this.nextToken = nextToken; }
    }
}