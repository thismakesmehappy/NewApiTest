package co.thismakesmehappy.toyapi.service.components.items;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialized service for building item API responses.
 * Separates response formatting concerns from business logic.
 */
public class ItemResponseBuilder {
    
    /**
     * Build response for item creation.
     */
    public Map<String, Object> buildCreateItemResponse(
            String itemId, 
            String message, 
            String userId, 
            Instant createdAt, 
            Instant updatedAt,
            String requestId) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", itemId);
        response.put("message", message);
        response.put("userId", userId);
        response.put("createdAt", createdAt.toString());
        response.put("updatedAt", updatedAt.toString());
        
        // Add metadata for monitoring/debugging
        if (requestId != null) {
            response.put("requestId", requestId);
        }
        
        return response;
    }
    
    /**
     * Build response for item retrieval (list of items).
     */
    public Map<String, Object> buildGetItemsResponse(
            List<Map<String, Object>> items,
            String userId,
            Integer limit,
            String sortOrder,
            String nextToken,
            String requestId) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("items", items != null ? items : List.of());
        response.put("count", items != null ? items.size() : 0);
        
        // Add pagination info
        if (nextToken != null) {
            response.put("nextToken", nextToken);
            response.put("hasMore", true);
        } else {
            response.put("hasMore", false);
        }
        
        // Add request parameters for clarity
        response.put("userId", userId);
        response.put("limit", limit);
        response.put("sortOrder", sortOrder);
        
        // Add metadata for monitoring/debugging
        if (requestId != null) {
            response.put("requestId", requestId);
        }
        
        return response;
    }
    
    /**
     * Build response for single item retrieval.
     */
    public Map<String, Object> buildGetItemResponse(
            Map<String, Object> item,
            String requestId) {
        
        if (item == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("found", false);
            if (requestId != null) {
                response.put("requestId", requestId);
            }
            return response;
        }
        
        // Create response with item data
        Map<String, Object> response = new HashMap<>(item);
        response.put("found", true);
        
        // Add metadata
        if (requestId != null) {
            response.put("requestId", requestId);
        }
        
        return response;
    }
    
    /**
     * Build response for item update.
     */
    public Map<String, Object> buildUpdateItemResponse(
            String itemId,
            String message,
            String userId,
            Instant updatedAt,
            String requestId) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", itemId);
        response.put("message", message);
        response.put("userId", userId);
        response.put("updatedAt", updatedAt.toString());
        response.put("updated", true);
        
        // Add metadata
        if (requestId != null) {
            response.put("requestId", requestId);
        }
        
        return response;
    }
    
    /**
     * Build response for item deletion.
     */
    public Map<String, Object> buildDeleteItemResponse(
            String itemId,
            String userId,
            String requestId) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", itemId);
        response.put("userId", userId);
        response.put("deleted", true);
        response.put("deletedAt", Instant.now().toString());
        
        // Add metadata
        if (requestId != null) {
            response.put("requestId", requestId);
        }
        
        return response;
    }
    
    /**
     * Build error response for validation failures.
     */
    public Map<String, Object> buildValidationErrorResponse(
            String message,
            List<String> errors,
            String requestId) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        
        if (errors != null && !errors.isEmpty()) {
            response.put("validationErrors", errors);
        }
        
        // Add metadata
        if (requestId != null) {
            response.put("requestId", requestId);
        }
        
        return response;
    }
}