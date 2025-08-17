package co.thismakesmehappy.toyapi.service.handlers;

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
import software.amazon.awssdk.services.ssm.SsmClient;

import java.time.Instant;
import java.util.*;
import java.util.Base64;

import co.thismakesmehappy.toyapi.service.utils.MockDatabase;
import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.AwsDynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.FeatureFlagService;
import co.thismakesmehappy.toyapi.service.utils.ParameterStoreFeatureFlagService;
import co.thismakesmehappy.toyapi.service.utils.ParameterStoreService;
import co.thismakesmehappy.toyapi.service.utils.AwsParameterStoreService;
import co.thismakesmehappy.toyapi.service.services.items.GetItemsService;
import co.thismakesmehappy.toyapi.service.services.items.PostItemsService;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersion;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersioningService;
import co.thismakesmehappy.toyapi.service.versioning.VersionedResponseBuilder;

/**
 * Lambda handler for items CRUD operations.
 * Handles all item-related endpoints with proper user-based access control.
 */
public class ItemsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(ItemsHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private final DynamoDbService dynamoDbService;
    private final String environment;
    private final String tableName;
    private final boolean useLocalMock;
    private final FeatureFlagService featureFlagService;
    private final ApiVersioningService versioningService;
    
    // Service layer
    private final GetItemsService getItemsService;
    private final PostItemsService postItemsService;

    /**
     * Default constructor for Lambda runtime.
     * Creates services with default AWS clients and environment-specific configuration.
     */
    public ItemsHandler() {
        String dynamoEndpoint = System.getenv("DYNAMODB_ENDPOINT");
        String environment = System.getenv("ENVIRONMENT");
        
        DynamoDbClient dynamoDb;
        if (dynamoEndpoint != null && !dynamoEndpoint.isEmpty()) {
            // Local development with DynamoDB Local
            dynamoDb = DynamoDbClient.builder()
                    .endpointOverride(java.net.URI.create(dynamoEndpoint))
                    .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                    .credentialsProvider(() -> software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("local", "local"))
                    .build();
        } else {
            // AWS environment
            dynamoDb = DynamoDbClient.builder()
                    .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                    .build();
        }
        
        this.dynamoDbService = new AwsDynamoDbService(dynamoDb);
        this.environment = System.getenv("ENVIRONMENT");
        this.tableName = System.getenv("TABLE_NAME");
        
        // Use mock ONLY for local development (when DYNAMODB_ENDPOINT is set to local DynamoDB)
        // All AWS environments (dev, stage, prod) should use real DynamoDB
        this.useLocalMock = (dynamoEndpoint != null && !dynamoEndpoint.isEmpty() && 
                           (environment == null || "local".equals(environment)));
        
        // FeatureFlagService is optional - only initialize in production AWS environments
        this.featureFlagService = null; // Will be initialized later if needed
        this.versioningService = new ApiVersioningService();
                           
        // Initialize services - PostItemsService has a constructor without FeatureFlagService for backward compatibility
        this.getItemsService = new GetItemsService(this.dynamoDbService, this.tableName, this.useLocalMock);
        this.postItemsService = new PostItemsService(this.dynamoDbService, this.tableName, this.useLocalMock);
    }
    
    /**
     * Constructor for dependency injection (testing).
     * 
     * @param dynamoDbService The DynamoDB service to use
     */
    public ItemsHandler(DynamoDbService dynamoDbService) {
        this.dynamoDbService = dynamoDbService;
        // For testing, use system properties as fallback if env vars are not set
        this.environment = System.getenv("ENVIRONMENT") != null ? System.getenv("ENVIRONMENT") : 
                          System.getProperty("ENVIRONMENT", "test");
        this.tableName = System.getenv("TABLE_NAME") != null ? System.getenv("TABLE_NAME") : 
                        System.getProperty("TABLE_NAME", "test-table");
        this.useLocalMock = false; // When using DI, assume we're testing with mocks
        
        // For testing, use a mock feature flag service
        this.featureFlagService = null; // Tests can pass null since validation service handles it
        this.versioningService = new ApiVersioningService();
        
        // Initialize services
        this.getItemsService = new GetItemsService(this.dynamoDbService, this.tableName, this.useLocalMock);
        this.postItemsService = new PostItemsService(this.dynamoDbService, this.tableName, this.useLocalMock, this.featureFlagService);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Handling items request: {} {}", input.getHttpMethod(), input.getPath());
        
        try {
            // Extract API version from request
            ApiVersion requestedVersion = versioningService.extractVersion(input);
            logger.info("API version: {}", requestedVersion.getVersionString());
            
            // Check if version is supported
            if (!versioningService.isVersionSupported(requestedVersion)) {
                logger.warn("Unsupported API version: {}", requestedVersion.getVersionString());
                return VersionedResponseBuilder.createErrorResponse(
                    requestedVersion, 400, "Unsupported API version: " + requestedVersion.getVersionString(), "UNSUPPORTED_VERSION");
            }
            
            String path = input.getPath();
            String method = input.getHttpMethod();
            
            if ("GET".equals(method) && "/items".equals(path)) {
                return handleListItems(input, context, requestedVersion);
            } else if ("POST".equals(method) && "/items".equals(path)) {
                return handleCreateItem(input, context, requestedVersion);
            } else if ("GET".equals(method) && path.matches("/items/[^/]+")) {
                return handleGetItem(input, context, requestedVersion);
            } else if ("PUT".equals(method) && path.matches("/items/[^/]+")) {
                return handleUpdateItem(input, context, requestedVersion);
            } else if ("DELETE".equals(method) && path.matches("/items/[^/]+")) {
                return handleDeleteItem(input, context, requestedVersion);
            }
            
            // Unknown endpoint
            return VersionedResponseBuilder.createErrorResponse(
                requestedVersion, 404, "Endpoint not found", "NOT_FOUND");
            
        } catch (Exception e) {
            logger.error("Error handling items request", e);
            ApiVersion fallbackVersion = ApiVersion.getDefault();
            return VersionedResponseBuilder.createErrorResponse(
                fallbackVersion, 500, "Internal server error: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles GET /items - list all items for the authenticated user
     */
    private APIGatewayProxyResponseEvent handleListItems(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            String userId = getUserIdFromRequest(input);
            
            // Use the enhanced GetItemsService
            GetItemsService.GetItemsRequest request = new GetItemsService.GetItemsRequest(
                userId, null, null, "desc"
            );
            
            Map<String, Object> response = getItemsService.execute(request);
            
            logger.info("Returning response for user: {} with version {}", userId, version.getVersionString());
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(200)
                    .withBody(response)
                    .build();
            
        } catch (Exception e) {
            logger.error("Error listing items", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to list items: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles POST /items - create a new item
     */
    private APIGatewayProxyResponseEvent handleCreateItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        String userId = null;
        try {
            userId = getUserIdFromRequest(input);
            String body = input.getBody();
            
            if (body == null || body.trim().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "Request body is required", "BAD_REQUEST");
            }
            
            JsonNode requestJson = objectMapper.readTree(body);
            String message = requestJson.get("message") != null ? requestJson.get("message").asText() : null;
            
            if (message == null || message.trim().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "Message is required", "BAD_REQUEST");
            }
            
            // Use the enhanced PostItemsService
            logger.info("Creating item for user: {} with message length: {} version: {}", userId, message.length(), version.getVersionString());
            PostItemsService.CreateItemRequest request = new PostItemsService.CreateItemRequest(
                message.trim(), userId
            );
            
            logger.info("Executing PostItemsService for user: {}", userId);
            Map<String, Object> response = postItemsService.execute(request);
            
            logger.info("Created item for user: {}", userId);
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(201)
                    .withBody(response)
                    .build();
            
        } catch (Exception e) {
            logger.error("Error creating item for user: {} - Exception type: {} - Message: {}", userId, e.getClass().getSimpleName(), e.getMessage(), e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to create item: " + e.getClass().getSimpleName() + ": " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles GET /items/{id} - get a specific item
     */
    private APIGatewayProxyResponseEvent handleGetItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            String userId = getUserIdFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            
            if (useLocalMock) {
                // Use mock database for local development
                Optional<MockDatabase.Item> item = MockDatabase.getItem(itemId, userId);
                if (item.isPresent()) {
                    logger.info("Retrieved item {} for user: {} (using mock database) version: {}", itemId, userId, version.getVersionString());
                    return new VersionedResponseBuilder(versioningService)
                            .withVersion(version)
                            .withStatusCode(200)
                            .withBody(item.get().toMap())
                            .build();
                } else {
                    return VersionedResponseBuilder.createErrorResponse(
                        version, 404, "Item not found", "NOT_FOUND");
                }
            }
            
            // Get item from DynamoDB
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            key.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();
            
            GetItemResponse getResponse = dynamoDbService.getItem(getRequest);
            
            if (!getResponse.hasItem() || getResponse.item().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 404, "Item not found", "NOT_FOUND");
            }
            
            Map<String, AttributeValue> item = getResponse.item();
            Map<String, Object> response = new HashMap<>();
            response.put("id", itemId);
            response.put("message", item.get("message").s());
            response.put("userId", item.get("userId").s());
            response.put("createdAt", item.get("createdAt").s());
            response.put("updatedAt", item.get("updatedAt").s());
            
            logger.info("Retrieved item {} for user: {} version: {}", itemId, userId, version.getVersionString());
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(200)
                    .withBody(response)
                    .build();
            
        } catch (Exception e) {
            logger.error("Error getting item", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to get item: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles PUT /items/{id} - update an existing item
     */
    private APIGatewayProxyResponseEvent handleUpdateItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            String userId = getUserIdFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            String body = input.getBody();
            
            if (body == null || body.trim().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "Request body is required", "BAD_REQUEST");
            }
            
            JsonNode requestJson = objectMapper.readTree(body);
            String message = requestJson.get("message") != null ? requestJson.get("message").asText() : null;
            
            if (message == null || message.trim().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "Message is required", "BAD_REQUEST");
            }
            
            if (useLocalMock) {
                // Use mock database for local development
                boolean updated = MockDatabase.updateItem(itemId, userId, message.trim());
                if (updated) {
                    Optional<MockDatabase.Item> updatedItem = MockDatabase.getItem(itemId, userId);
                    if (updatedItem.isPresent()) {
                        logger.info("Updated item {} for user: {} (using mock database) version: {}", itemId, userId, version.getVersionString());
                        return new VersionedResponseBuilder(versioningService)
                                .withVersion(version)
                                .withStatusCode(200)
                                .withBody(updatedItem.get().toMap())
                                .build();
                    }
                }
                return VersionedResponseBuilder.createErrorResponse(
                    version, 404, "Item not found", "NOT_FOUND");
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
                UpdateItemResponse updateResponse = dynamoDbService.updateItem(updateRequest);
                Map<String, AttributeValue> updatedItem = updateResponse.attributes();
                
                Map<String, Object> response = new HashMap<>();
                response.put("id", itemId);
                response.put("message", updatedItem.get("message").s());
                response.put("userId", updatedItem.get("userId").s());
                response.put("createdAt", updatedItem.get("createdAt").s());
                response.put("updatedAt", updatedItem.get("updatedAt").s());
                
                logger.info("Updated item {} for user: {} version: {}", itemId, userId, version.getVersionString());
                return new VersionedResponseBuilder(versioningService)
                        .withVersion(version)
                        .withStatusCode(200)
                        .withBody(response)
                        .build();
                
            } catch (ConditionalCheckFailedException e) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 404, "Item not found", "NOT_FOUND");
            }
            
        } catch (Exception e) {
            logger.error("Error updating item", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to update item: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles DELETE /items/{id} - delete an existing item
     */
    private APIGatewayProxyResponseEvent handleDeleteItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            String userId = getUserIdFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            
            if (useLocalMock) {
                // Use mock database for local development
                boolean deleted = MockDatabase.deleteItem(itemId, userId);
                if (deleted) {
                    logger.info("Deleted item {} for user: {} (using mock database) version: {}", itemId, userId, version.getVersionString());
                    return new VersionedResponseBuilder(versioningService)
                            .withVersion(version)
                            .withStatusCode(204)
                            .withBody(null)
                            .build();
                } else {
                    return VersionedResponseBuilder.createErrorResponse(
                        version, 404, "Item not found", "NOT_FOUND");
                }
            }
            
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
                dynamoDbService.deleteItem(deleteRequest);
                logger.info("Deleted item {} for user: {} version: {}", itemId, userId, version.getVersionString());
                return new VersionedResponseBuilder(versioningService)
                        .withVersion(version)
                        .withStatusCode(204)
                        .withBody(null)
                        .build();
                
            } catch (ConditionalCheckFailedException e) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 404, "Item not found", "NOT_FOUND");
            }
            
        } catch (Exception e) {
            logger.error("Error deleting item", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to delete item: " + e.getMessage(), "INTERNAL_ERROR");
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
        // Use mock authentication for local development
        String mockAuth = System.getenv("MOCK_AUTHENTICATION") != null ? System.getenv("MOCK_AUTHENTICATION") :
                         System.getProperty("MOCK_AUTHENTICATION");
        if ("true".equals(mockAuth)) {
            String localUserId = System.getenv("LOCAL_TEST_USER_ID") != null ? System.getenv("LOCAL_TEST_USER_ID") :
                                System.getProperty("LOCAL_TEST_USER_ID");
            if (localUserId != null) {
                return localUserId;
            }
            return "local-user-12345";
        }
        
        // JWT token validation for AWS Cognito environments
        // Extracts user ID from Authorization header with proper fallback handling
        Map<String, String> headers = input.getHeaders();
        if (headers != null) {
            String authHeader = headers.get("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                // Extract user ID from real JWT token
                try {
                    String userId = extractUserIdFromJWT(token);
                    if (userId != null && !userId.isEmpty()) {
                        return userId;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to extract user ID from JWT token: {}", e.getMessage());
                }
                
                // Mock fallback for testing
                if (token.startsWith("mock-jwt-token-")) {
                    return "user-" + Math.abs(token.hashCode() % 100000);
                }
            }
        }
        
        // Fallback to mock user ID
        return "user-12345";
    }
    
    /**
     * Extracts user ID (sub claim) from JWT token without verification
     * This assumes API Gateway has already validated the token
     */
    private String extractUserIdFromJWT(String token) {
        try {
            // Split JWT token (header.payload.signature)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                logger.warn("Invalid JWT token format");
                return null;
            }
            
            // Decode the payload (second part)
            String payload = parts[1];
            
            // Add padding if needed for Base64 decoding
            while (payload.length() % 4 != 0) {
                payload += "=";
            }
            
            // Decode Base64 payload
            byte[] decodedBytes = Base64.getDecoder().decode(payload);
            String decodedPayload = new String(decodedBytes);
            
            // Parse JSON to extract 'sub' field
            JsonNode jsonNode = objectMapper.readTree(decodedPayload);
            JsonNode subNode = jsonNode.get("sub");
            
            if (subNode != null && !subNode.isNull()) {
                String userId = subNode.asText();
                logger.debug("Extracted user ID from JWT: {}", userId);
                return userId;
            } else {
                logger.warn("No 'sub' claim found in JWT token");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error extracting user ID from JWT token: {}", e.getMessage(), e);
            return null;
        }
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