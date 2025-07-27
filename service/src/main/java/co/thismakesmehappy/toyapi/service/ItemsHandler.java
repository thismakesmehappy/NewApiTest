package co.thismakesmehappy.toyapi.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Lambda handler for items CRUD operations.
 * Handles all item-related endpoints with proper user-based access control.
 */
public class ItemsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(ItemsHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private final String environment = System.getenv("ENVIRONMENT");
    private final String tableName = System.getenv("TABLE_NAME");
    private final DynamoDbClient dynamoDb;

    public ItemsHandler() {
        this.dynamoDb = DynamoDbClient.builder().build();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Handling items request: {} {}", input.getHttpMethod(), input.getPath());
        
        try {
            String path = input.getPath();
            String method = input.getHttpMethod();
            
            if ("GET".equals(method) && "/items".equals(path)) {
                return handleListItems(input, context);
            } else if ("POST".equals(method) && "/items".equals(path)) {
                return handleCreateItem(input, context);
            } else if ("GET".equals(method) && path.matches("/items/[^/]+")) {
                return handleGetItem(input, context);
            } else if ("PUT".equals(method) && path.matches("/items/[^/]+")) {
                return handleUpdateItem(input, context);
            } else if ("DELETE".equals(method) && path.matches("/items/[^/]+")) {
                return handleDeleteItem(input, context);
            }
            
            // Unknown endpoint
            return createErrorResponse(404, "NOT_FOUND", "Endpoint not found", null);
            
        } catch (Exception e) {
            logger.error("Error handling items request", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Internal server error", e.getMessage());
        }
    }
    
    /**
     * Handles GET /items - list all items for the authenticated user
     */
    private APIGatewayProxyResponseEvent handleListItems(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = getUserIdFromRequest(input);
            
            // Query items for this user
            Map<String, AttributeValue> keyCondition = new HashMap<>();
            keyCondition.put(":userId", AttributeValue.builder().s(userId).build());
            
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("UserIndex")
                    .keyConditionExpression("userId = :userId")
                    .expressionAttributeValues(keyCondition)
                    .build();
            
            QueryResponse queryResponse = dynamoDb.query(queryRequest);
            
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, AttributeValue> item : queryResponse.items()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("id", item.get("SK").s().substring(5)); // Remove "ITEM#" prefix
                itemMap.put("message", item.get("message").s());
                itemMap.put("userId", item.get("userId").s());
                itemMap.put("createdAt", item.get("createdAt").s());
                itemMap.put("updatedAt", item.get("updatedAt").s());
                items.add(itemMap);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("count", items.size());
            
            logger.info("Returning {} items for user: {}", items.size(), userId);
            return createSuccessResponse(200, response);
            
        } catch (Exception e) {
            logger.error("Error listing items", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to list items", e.getMessage());
        }
    }
    
    /**
     * Handles POST /items - create a new item
     */
    private APIGatewayProxyResponseEvent handleCreateItem(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = getUserIdFromRequest(input);
            String body = input.getBody();
            
            if (body == null || body.trim().isEmpty()) {
                return createErrorResponse(400, "BAD_REQUEST", "Request body is required", null);
            }
            
            JsonNode requestJson = objectMapper.readTree(body);
            String message = requestJson.get("message") != null ? requestJson.get("message").asText() : null;
            
            if (message == null || message.trim().isEmpty()) {
                return createErrorResponse(400, "BAD_REQUEST", "Message is required", null);
            }
            
            String itemId = "item-" + UUID.randomUUID().toString();
            String now = Instant.now().toString();
            
            // Create item in DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            item.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            item.put("userId", AttributeValue.builder().s(userId).build());
            item.put("message", AttributeValue.builder().s(message.trim()).build());
            item.put("createdAt", AttributeValue.builder().s(now).build());
            item.put("updatedAt", AttributeValue.builder().s(now).build());
            
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();
            
            dynamoDb.putItem(putRequest);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", itemId);
            response.put("message", message.trim());
            response.put("userId", userId);
            response.put("createdAt", now);
            response.put("updatedAt", now);
            
            logger.info("Created item {} for user: {}", itemId, userId);
            return createSuccessResponse(201, response);
            
        } catch (Exception e) {
            logger.error("Error creating item", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to create item", e.getMessage());
        }
    }
    
    /**
     * Handles GET /items/{id} - get a specific item
     */
    private APIGatewayProxyResponseEvent handleGetItem(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = getUserIdFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            
            // Get item from DynamoDB
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            key.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();
            
            GetItemResponse getResponse = dynamoDb.getItem(getRequest);
            
            if (!getResponse.hasItem() || getResponse.item().isEmpty()) {
                return createErrorResponse(404, "NOT_FOUND", "Item not found", null);
            }
            
            Map<String, AttributeValue> item = getResponse.item();
            Map<String, Object> response = new HashMap<>();
            response.put("id", itemId);
            response.put("message", item.get("message").s());
            response.put("userId", item.get("userId").s());
            response.put("createdAt", item.get("createdAt").s());
            response.put("updatedAt", item.get("updatedAt").s());
            
            logger.info("Retrieved item {} for user: {}", itemId, userId);
            return createSuccessResponse(200, response);
            
        } catch (Exception e) {
            logger.error("Error getting item", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to get item", e.getMessage());
        }
    }
    
    /**
     * Handles PUT /items/{id} - update an existing item
     */
    private APIGatewayProxyResponseEvent handleUpdateItem(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = getUserIdFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            String body = input.getBody();
            
            if (body == null || body.trim().isEmpty()) {
                return createErrorResponse(400, "BAD_REQUEST", "Request body is required", null);
            }
            
            JsonNode requestJson = objectMapper.readTree(body);
            String message = requestJson.get("message") != null ? requestJson.get("message").asText() : null;
            
            if (message == null || message.trim().isEmpty()) {
                return createErrorResponse(400, "BAD_REQUEST", "Message is required", null);
            }
            
            String now = Instant.now().toString();
            
            // Update item in DynamoDB
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            key.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            
            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":message", AttributeValue.builder().s(message.trim()).build());
            attributeValues.put(":updatedAt", AttributeValue.builder().s(now).build());
            
            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET message = :message, updatedAt = :updatedAt")
                    .expressionAttributeValues(attributeValues)
                    .conditionExpression("attribute_exists(PK)")
                    .returnValues(ReturnValue.ALL_NEW)
                    .build();
            
            try {
                UpdateItemResponse updateResponse = dynamoDb.updateItem(updateRequest);
                Map<String, AttributeValue> updatedItem = updateResponse.attributes();
                
                Map<String, Object> response = new HashMap<>();
                response.put("id", itemId);
                response.put("message", updatedItem.get("message").s());
                response.put("userId", updatedItem.get("userId").s());
                response.put("createdAt", updatedItem.get("createdAt").s());
                response.put("updatedAt", updatedItem.get("updatedAt").s());
                
                logger.info("Updated item {} for user: {}", itemId, userId);
                return createSuccessResponse(200, response);
                
            } catch (ConditionalCheckFailedException e) {
                return createErrorResponse(404, "NOT_FOUND", "Item not found", null);
            }
            
        } catch (Exception e) {
            logger.error("Error updating item", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to update item", e.getMessage());
        }
    }
    
    /**
     * Handles DELETE /items/{id} - delete an existing item
     */
    private APIGatewayProxyResponseEvent handleDeleteItem(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = getUserIdFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            
            // Delete item from DynamoDB
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            key.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            
            DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .conditionExpression("attribute_exists(PK)")
                    .build();
            
            try {
                dynamoDb.deleteItem(deleteRequest);
                logger.info("Deleted item {} for user: {}", itemId, userId);
                return createSuccessResponse(204, null);
                
            } catch (ConditionalCheckFailedException e) {
                return createErrorResponse(404, "NOT_FOUND", "Item not found", null);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting item", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to delete item", e.getMessage());
        }
    }
    
    /**
     * Extracts item ID from path like /items/item-123
     */
    private String extractItemIdFromPath(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }
    
    /**
     * Extracts user ID from the request (from JWT token or mock authorization)
     */
    private String getUserIdFromRequest(APIGatewayProxyRequestEvent input) {
        // TODO: Implement proper JWT token validation
        // For now, extract from Authorization header or use mock user
        Map<String, String> headers = input.getHeaders();
        if (headers != null) {
            String authHeader = headers.get("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // Mock: extract user ID from token (in real implementation, decode JWT)
                if (token.startsWith("mock-jwt-token-")) {
                    return "user-" + Math.abs(token.hashCode() % 100000);
                }
            }
        }
        
        // Fallback to mock user ID
        return "user-12345";
    }
    
    /**
     * Creates a successful API Gateway response
     */
    private APIGatewayProxyResponseEvent createSuccessResponse(int statusCode, Object body) {
        try {
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(statusCode);
            
            if (body != null) {
                response.setBody(objectMapper.writeValueAsString(body));
            }
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setHeaders(headers);
            
            return response;
        } catch (Exception e) {
            logger.error("Error creating success response", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to serialize response", e.getMessage());
        }
    }
    
    /**
     * Creates an error API Gateway response
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String error, String message, String details) {
        try {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", error);
            errorBody.put("message", message);
            errorBody.put("timestamp", Instant.now().toString());
            if (details != null) {
                errorBody.put("details", details);
            }
            
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(statusCode);
            response.setBody(objectMapper.writeValueAsString(errorBody));
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setHeaders(headers);
            
            return response;
        } catch (Exception e) {
            logger.error("Error creating error response", e);
            // Fallback response
            APIGatewayProxyResponseEvent fallback = new APIGatewayProxyResponseEvent();
            fallback.setStatusCode(500);
            fallback.setBody("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Failed to create error response\"}");
            return fallback;
        }
    }
}