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
import java.util.UUID;

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
import co.thismakesmehappy.toyapi.service.models.User;
import co.thismakesmehappy.toyapi.service.models.Item;
import co.thismakesmehappy.toyapi.service.services.auth.AuthorizationService;

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
    private final AuthorizationService authorizationService;
    
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
        this.authorizationService = new AuthorizationService();
                           
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
        this.authorizationService = new AuthorizationService();
        
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
            User user = getUserFromRequest(input);
            
            // Get all items accessible to the user (own items + team items + admin access)
            Map<String, Object> response = getAccessibleItems(user);
            
            logger.info("Returning {} items for user: {} (role: {}) with version {}", 
                       response.get("count"), user.getUserId(), user.getRole(), version.getVersionString());
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
     * Handles POST /items - create a new item with team support
     */
    private APIGatewayProxyResponseEvent handleCreateItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        User user = null;
        try {
            user = getUserFromRequest(input);
            String body = input.getBody();
            
            if (body == null || body.trim().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "Request body is required", "BAD_REQUEST");
            }
            
            JsonNode requestJson = objectMapper.readTree(body);
            String message = requestJson.get("message") != null ? requestJson.get("message").asText() : null;
            String teamId = requestJson.get("teamId") != null ? requestJson.get("teamId").asText() : null;
            String accessLevelStr = requestJson.get("accessLevel") != null ? requestJson.get("accessLevel").asText() : null;
            
            if (message == null || message.trim().isEmpty()) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "Message is required", "BAD_REQUEST");
            }
            
            // Validate team assignment
            if (teamId != null && !authorizationService.isValidTeamAssignment(user, teamId)) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 403, "User does not have access to the specified team", "FORBIDDEN");
            }
            
            // Determine access level
            Item.AccessLevel accessLevel = Item.AccessLevel.INDIVIDUAL; // default
            if (accessLevelStr != null) {
                try {
                    accessLevel = Item.AccessLevel.valueOf(accessLevelStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return VersionedResponseBuilder.createErrorResponse(
                        version, 400, "Invalid access level. Must be: INDIVIDUAL, TEAM, or PUBLIC", "BAD_REQUEST");
                }
            } else if (teamId != null) {
                // If teamId is provided but no access level, default to TEAM
                accessLevel = Item.AccessLevel.TEAM;
            }
            
            // Validate access level consistency
            if (accessLevel == Item.AccessLevel.TEAM && teamId == null) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 400, "teamId is required when accessLevel is TEAM", "BAD_REQUEST");
            }
            
            // Create the item with team support
            Map<String, Object> response = createItemWithTeamSupport(user, message.trim(), teamId, accessLevel);
            
            logger.info("Created item for user: {} with teamId: {} accessLevel: {} version: {}", 
                       user.getUserId(), teamId, accessLevel, version.getVersionString());
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(201)
                    .withBody(response)
                    .build();
            
        } catch (Exception e) {
            String userId = user != null ? user.getUserId() : "unknown";
            logger.error("Error creating item for user: {} - Exception type: {} - Message: {}", userId, e.getClass().getSimpleName(), e.getMessage(), e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to create item: " + e.getClass().getSimpleName() + ": " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles GET /items/{id} - get a specific item with authorization
     */
    private APIGatewayProxyResponseEvent handleGetItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            User user = getUserFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            
            if (useLocalMock) {
                return handleGetItemFromMock(user, itemId, version);
            } else {
                return handleGetItemFromDynamoDB(user, itemId, version);
            }
            
        } catch (Exception e) {
            logger.error("Error getting item", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to get item: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles getting item from mock database with authorization
     */
    private APIGatewayProxyResponseEvent handleGetItemFromMock(User user, String itemId, ApiVersion version) {
        // For mock, try to find the item by scanning all items (since mock doesn't support team queries well)
        List<MockDatabase.Item> allItems = MockDatabase.getAllItems();
        
        for (MockDatabase.Item mockItem : allItems) {
            if (mockItem.id.equals(itemId)) {
                // Convert to Item model for permission checking
                Item item = new Item.Builder()
                    .id(mockItem.id)
                    .message(mockItem.message)
                    .userId(mockItem.userId)
                    .accessLevel(Item.AccessLevel.INDIVIDUAL) // Mock items are individual
                    .build();
                item.setCreatedAt(Instant.parse(mockItem.createdAt));
                item.setUpdatedAt(Instant.parse(mockItem.updatedAt));
                
                // Check permissions
                if (!authorizationService.canUserAccessItem(user, item)) {
                    return VersionedResponseBuilder.createErrorResponse(
                        version, 403, "Access denied to this item", "FORBIDDEN");
                }
                
                Map<String, Object> response = mockItem.toMap();
                response.put("accessLevel", "individual"); // Add for consistency
                
                logger.info("Retrieved item {} for user: {} (using mock database) version: {}", 
                           itemId, user.getUserId(), version.getVersionString());
                return new VersionedResponseBuilder(versioningService)
                        .withVersion(version)
                        .withStatusCode(200)
                        .withBody(response)
                        .build();
            }
        }
        
        return VersionedResponseBuilder.createErrorResponse(
            version, 404, "Item not found", "NOT_FOUND");
    }
    
    /**
     * Handles getting item from DynamoDB with authorization
     */
    private APIGatewayProxyResponseEvent handleGetItemFromDynamoDB(User user, String itemId, ApiVersion version) {
        // First, try to get the item directly from user's partition
        Item item = getItemFromUserPartition(user.getUserId(), itemId);
        
        // If not found in user's partition, search team partitions (if user has teams)
        if (item == null && user.getTeamIds() != null && !user.getTeamIds().isEmpty()) {
            item = findItemInTeams(itemId, user);
        }
        
        // If still not found and user is admin, search all partitions
        if (item == null && user.isAdmin()) {
            item = findItemInAllPartitions(itemId);
        }
        
        if (item == null) {
            return VersionedResponseBuilder.createErrorResponse(
                version, 404, "Item not found", "NOT_FOUND");
        }
        
        // Check permissions
        if (!authorizationService.canUserAccessItem(user, item)) {
            return VersionedResponseBuilder.createErrorResponse(
                version, 403, "Access denied to this item", "FORBIDDEN");
        }
        
        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("id", item.getId());
        response.put("message", item.getMessage());
        response.put("userId", item.getUserId());
        response.put("createdAt", item.getCreatedAt().toString());
        response.put("updatedAt", item.getUpdatedAt().toString());
        response.put("accessLevel", item.getAccessLevel().toString().toLowerCase());
        
        if (item.getTeamId() != null) {
            response.put("teamId", item.getTeamId());
        }
        if (item.getCreatedBy() != null) {
            response.put("createdBy", item.getCreatedBy());
        }
        
        logger.info("Retrieved item {} for user: {} version: {}", itemId, user.getUserId(), version.getVersionString());
        return new VersionedResponseBuilder(versioningService)
                .withVersion(version)
                .withStatusCode(200)
                .withBody(response)
                .build();
    }
    
    /**
     * Gets item from user's own partition
     */
    private Item getItemFromUserPartition(String userId, String itemId) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s("USER#" + userId).build());
            key.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
            
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();
            
            GetItemResponse getResponse = dynamoDbService.getItem(getRequest);
            
            if (getResponse.hasItem() && !getResponse.item().isEmpty()) {
                return convertDynamoItemToItem(getResponse.item());
            }
        } catch (Exception e) {
            logger.error("Error getting item from user partition: userId={}, itemId={}", userId, itemId, e);
        }
        
        return null;
    }
    
    /**
     * Finds item in user's team partitions
     */
    private Item findItemInTeams(String itemId, User user) {
        // This would be more efficient with a GSI in production
        // For now, we'll scan with filter for team items
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("SK = :sk AND accessLevel = :accessLevel")
                .expressionAttributeValues(Map.of(
                    ":sk", AttributeValue.builder().s("ITEM#" + itemId).build(),
                    ":accessLevel", AttributeValue.builder().s("TEAM").build()
                ))
                .build();
            
            ScanResponse response = dynamoDbService.scan(scanRequest);
            
            for (Map<String, AttributeValue> dynamoItem : response.items()) {
                Item item = convertDynamoItemToItem(dynamoItem);
                
                // Check if user has access to this team item
                if (item.getTeamId() != null && user.isMemberOfTeam(item.getTeamId())) {
                    return item;
                }
            }
        } catch (Exception e) {
            logger.error("Error finding item in teams: itemId={}", itemId, e);
        }
        
        return null;
    }
    
    /**
     * Finds item in all partitions (admin only)
     */
    private Item findItemInAllPartitions(String itemId) {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("SK = :sk")
                .expressionAttributeValues(Map.of(
                    ":sk", AttributeValue.builder().s("ITEM#" + itemId).build()
                ))
                .build();
            
            ScanResponse response = dynamoDbService.scan(scanRequest);
            
            if (!response.items().isEmpty()) {
                return convertDynamoItemToItem(response.items().get(0));
            }
        } catch (Exception e) {
            logger.error("Error finding item in all partitions: itemId={}", itemId, e);
        }
        
        return null;
    }
    
    /**
     * Handles PUT /items/{id} - update an existing item with authorization
     */
    private APIGatewayProxyResponseEvent handleUpdateItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            User user = getUserFromRequest(input);
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
            
            // First, find and validate the item exists and user can modify it
            Item existingItem = findItemForModification(user, itemId);
            if (existingItem == null) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 404, "Item not found", "NOT_FOUND");
            }
            
            // Check modify permissions
            if (!authorizationService.canUserModifyItem(user, existingItem)) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 403, "Access denied - insufficient permissions to modify this item", "FORBIDDEN");
            }
            
            if (useLocalMock) {
                return handleUpdateItemInMock(user, itemId, message.trim(), version);
            } else {
                return handleUpdateItemInDynamoDB(user, existingItem, message.trim(), version);
            }
            
        } catch (Exception e) {
            logger.error("Error updating item", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to update item: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Finds item for modification (includes permission pre-check)
     */
    private Item findItemForModification(User user, String itemId) {
        if (useLocalMock) {
            // For mock, find the item by scanning all items
            List<MockDatabase.Item> allItems = MockDatabase.getAllItems();
            for (MockDatabase.Item mockItem : allItems) {
                if (mockItem.id.equals(itemId)) {
                    return new Item.Builder()
                        .id(mockItem.id)
                        .message(mockItem.message)
                        .userId(mockItem.userId)
                        .accessLevel(Item.AccessLevel.INDIVIDUAL)
                        .build();
                }
            }
            return null;
        }
        
        // For DynamoDB, use the same search logic as get item
        Item item = getItemFromUserPartition(user.getUserId(), itemId);
        
        if (item == null && user.getTeamIds() != null && !user.getTeamIds().isEmpty()) {
            item = findItemInTeams(itemId, user);
        }
        
        if (item == null && user.isAdmin()) {
            item = findItemInAllPartitions(itemId);
        }
        
        return item;
    }
    
    /**
     * Handles updating item in mock database
     */
    private APIGatewayProxyResponseEvent handleUpdateItemInMock(User user, String itemId, String message, ApiVersion version) {
        // For mock, we'll update based on the original user (mock limitation)
        boolean updated = MockDatabase.updateItem(itemId, user.getUserId(), message);
        if (updated) {
            Optional<MockDatabase.Item> updatedItem = MockDatabase.getItem(itemId, user.getUserId());
            if (updatedItem.isPresent()) {
                Map<String, Object> response = updatedItem.get().toMap();
                response.put("accessLevel", "individual"); // Add for consistency
                
                logger.info("Updated item {} for user: {} (using mock database) version: {}", 
                           itemId, user.getUserId(), version.getVersionString());
                return new VersionedResponseBuilder(versioningService)
                        .withVersion(version)
                        .withStatusCode(200)
                        .withBody(response)
                        .build();
            }
        }
        return VersionedResponseBuilder.createErrorResponse(
            version, 404, "Item not found or update failed", "NOT_FOUND");
    }
    
    /**
     * Handles updating item in DynamoDB
     */
    private APIGatewayProxyResponseEvent handleUpdateItemInDynamoDB(User user, Item existingItem, String message, ApiVersion version) {
        String now = Instant.now().toString();
        
        // Build the key for the original item location
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s("USER#" + existingItem.getUserId()).build());
        key.put("SK", AttributeValue.builder().s("ITEM#" + existingItem.getId()).build());
        
        Map<String, AttributeValue> attributeValues = new HashMap<>();
        attributeValues.put(":message", AttributeValue.builder().s(message).build());
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
            
            Map<String, Object> response = convertDynamoItemToMap(updatedItem);
            
            logger.info("Updated item {} for user: {} (modifier: {}) version: {}", 
                       existingItem.getId(), existingItem.getUserId(), user.getUserId(), version.getVersionString());
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(200)
                    .withBody(response)
                    .build();
            
        } catch (ConditionalCheckFailedException e) {
            return VersionedResponseBuilder.createErrorResponse(
                version, 404, "Item not found or was modified by another user", "NOT_FOUND");
        }
    }
    
    /**
     * Handles DELETE /items/{id} - delete an existing item with authorization
     */
    private APIGatewayProxyResponseEvent handleDeleteItem(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            User user = getUserFromRequest(input);
            String itemId = extractItemIdFromPath(input.getPath());
            
            // First, find and validate the item exists and user can modify it
            Item existingItem = findItemForModification(user, itemId);
            if (existingItem == null) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 404, "Item not found", "NOT_FOUND");
            }
            
            // Check modify permissions (delete requires modify permission)
            if (!authorizationService.canUserModifyItem(user, existingItem)) {
                return VersionedResponseBuilder.createErrorResponse(
                    version, 403, "Access denied - insufficient permissions to delete this item", "FORBIDDEN");
            }
            
            if (useLocalMock) {
                return handleDeleteItemFromMock(user, itemId, version);
            } else {
                return handleDeleteItemFromDynamoDB(user, existingItem, version);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting item", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to delete item: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles deleting item from mock database
     */
    private APIGatewayProxyResponseEvent handleDeleteItemFromMock(User user, String itemId, ApiVersion version) {
        // For mock, we'll try to delete by scanning all items first
        List<MockDatabase.Item> allItems = MockDatabase.getAllItems();
        String originalUserId = null;
        
        for (MockDatabase.Item mockItem : allItems) {
            if (mockItem.id.equals(itemId)) {
                originalUserId = mockItem.userId;
                break;
            }
        }
        
        if (originalUserId == null) {
            return VersionedResponseBuilder.createErrorResponse(
                version, 404, "Item not found", "NOT_FOUND");
        }
        
        boolean deleted = MockDatabase.deleteItem(itemId, originalUserId);
        if (deleted) {
            logger.info("Deleted item {} (original owner: {}) by user: {} (using mock database) version: {}", 
                       itemId, originalUserId, user.getUserId(), version.getVersionString());
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(204)
                    .withBody(null)
                    .build();
        } else {
            return VersionedResponseBuilder.createErrorResponse(
                version, 404, "Item not found or delete failed", "NOT_FOUND");
        }
    }
    
    /**
     * Handles deleting item from DynamoDB
     */
    private APIGatewayProxyResponseEvent handleDeleteItemFromDynamoDB(User user, Item existingItem, ApiVersion version) {
        // Build the key for the original item location
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s("USER#" + existingItem.getUserId()).build());
        key.put("SK", AttributeValue.builder().s("ITEM#" + existingItem.getId()).build());
        
        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("attribute_exists(PK)")
                .build();
        
        try {
            dynamoDbService.deleteItem(deleteRequest);
            logger.info("Deleted item {} (owner: {}) by user: {} version: {}", 
                       existingItem.getId(), existingItem.getUserId(), user.getUserId(), version.getVersionString());
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(204)
                    .withBody(null)
                    .build();
            
        } catch (ConditionalCheckFailedException e) {
            return VersionedResponseBuilder.createErrorResponse(
                version, 404, "Item not found or was deleted by another user", "NOT_FOUND");
        }
    }
    
    /**
     * Gets all items accessible to the user based on their permissions
     */
    private Map<String, Object> getAccessibleItems(User user) {
        if (useLocalMock) {
            return getAccessibleItemsFromMock(user);
        }
        
        // For DynamoDB, we need to query for items the user can access
        return getAccessibleItemsFromDynamoDB(user);
    }
    
    /**
     * Gets accessible items from mock database (for local development)
     */
    private Map<String, Object> getAccessibleItemsFromMock(User user) {
        // For mock, get all items and filter by access
        List<MockDatabase.Item> allItems = MockDatabase.getAllItems();
        List<Map<String, Object>> accessibleItems = new ArrayList<>();
        
        for (MockDatabase.Item mockItem : allItems) {
            // Convert mock item to Item model for permission checking
            Item item = new Item.Builder()
                .id(mockItem.id)
                .message(mockItem.message)
                .userId(mockItem.userId)
                .accessLevel(Item.AccessLevel.INDIVIDUAL) // Mock items are individual for now
                .build();
            item.setCreatedAt(Instant.parse(mockItem.createdAt));
            item.setUpdatedAt(Instant.parse(mockItem.updatedAt));
            
            if (authorizationService.canUserAccessItem(user, item)) {
                accessibleItems.add(mockItem.toMap());
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("items", accessibleItems);
        response.put("count", accessibleItems.size());
        response.put("hasMore", false);
        
        return response;
    }
    
    /**
     * Gets accessible items from DynamoDB based on user permissions
     */
    private Map<String, Object> getAccessibleItemsFromDynamoDB(User user) {
        List<Map<String, Object>> accessibleItems = new ArrayList<>();
        
        if (user.isAdmin()) {
            // Admin can see all items - scan the entire table
            accessibleItems.addAll(scanAllItems());
        } else {
            // Regular users: get their own items + team items they can access
            accessibleItems.addAll(getUserOwnItems(user.getUserId()));
            
            // Add team items if user has team memberships
            if (user.getTeamIds() != null && !user.getTeamIds().isEmpty()) {
                for (String teamId : user.getTeamIds()) {
                    accessibleItems.addAll(getTeamItems(teamId, user));
                }
            }
        }
        
        // Remove duplicates (in case user owns items that are also in their teams)
        Map<String, Map<String, Object>> uniqueItems = new HashMap<>();
        for (Map<String, Object> item : accessibleItems) {
            uniqueItems.put((String) item.get("id"), item);
        }
        
        List<Map<String, Object>> finalItems = new ArrayList<>(uniqueItems.values());
        
        // Sort by updatedAt desc (most recent first)
        finalItems.sort((a, b) -> {
            String timeA = (String) a.get("updatedAt");
            String timeB = (String) b.get("updatedAt");
            return timeB.compareTo(timeA);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("items", finalItems);
        response.put("count", finalItems.size());
        response.put("hasMore", false);
        
        return response;
    }
    
    /**
     * Gets user's own items from DynamoDB
     */
    private List<Map<String, Object>> getUserOwnItems(String userId) {
        List<Map<String, Object>> items = new ArrayList<>();
        
        try {
            QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                .expressionAttributeValues(Map.of(
                    ":pk", AttributeValue.builder().s("USER#" + userId).build(),
                    ":sk", AttributeValue.builder().s("ITEM#").build()
                ))
                .build();
            
            QueryResponse response = dynamoDbService.query(queryRequest);
            
            for (Map<String, AttributeValue> item : response.items()) {
                items.add(convertDynamoItemToMap(item));
            }
        } catch (Exception e) {
            logger.error("Error querying user items for userId: {}", userId, e);
        }
        
        return items;
    }
    
    /**
     * Gets team items from DynamoDB for a specific team
     */
    private List<Map<String, Object>> getTeamItems(String teamId, User user) {
        List<Map<String, Object>> items = new ArrayList<>();
        
        try {
            // Query for team items using GSI (if available) or scan with filter
            // For now, we'll use scan with filter - in production, a GSI would be better
            ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("teamId = :teamId AND accessLevel = :accessLevel")
                .expressionAttributeValues(Map.of(
                    ":teamId", AttributeValue.builder().s(teamId).build(),
                    ":accessLevel", AttributeValue.builder().s("TEAM").build()
                ))
                .build();
            
            ScanResponse response = dynamoDbService.scan(scanRequest);
            
            for (Map<String, AttributeValue> dynamoItem : response.items()) {
                // Convert to Item model for permission checking
                Item item = convertDynamoItemToItem(dynamoItem);
                if (authorizationService.canUserAccessItem(user, item)) {
                    items.add(convertDynamoItemToMap(dynamoItem));
                }
            }
        } catch (Exception e) {
            logger.error("Error querying team items for teamId: {}", teamId, e);
        }
        
        return items;
    }
    
    /**
     * Scans all items for admin users
     */
    private List<Map<String, Object>> scanAllItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("begins_with(SK, :sk)")
                .expressionAttributeValues(Map.of(
                    ":sk", AttributeValue.builder().s("ITEM#").build()
                ))
                .build();
            
            ScanResponse response = dynamoDbService.scan(scanRequest);
            
            for (Map<String, AttributeValue> item : response.items()) {
                items.add(convertDynamoItemToMap(item));
            }
        } catch (Exception e) {
            logger.error("Error scanning all items", e);
        }
        
        return items;
    }
    
    /**
     * Converts DynamoDB item to Item model
     */
    private Item convertDynamoItemToItem(Map<String, AttributeValue> dynamoItem) {
        String itemId = extractItemIdFromSortKey(dynamoItem.get("SK").s());
        String message = dynamoItem.get("message") != null ? dynamoItem.get("message").s() : "";
        String userId = dynamoItem.get("userId") != null ? dynamoItem.get("userId").s() : "";
        String teamId = dynamoItem.get("teamId") != null ? dynamoItem.get("teamId").s() : null;
        String accessLevelStr = dynamoItem.get("accessLevel") != null ? dynamoItem.get("accessLevel").s() : "INDIVIDUAL";
        
        Item.AccessLevel accessLevel;
        try {
            accessLevel = Item.AccessLevel.valueOf(accessLevelStr);
        } catch (IllegalArgumentException e) {
            accessLevel = Item.AccessLevel.INDIVIDUAL;
        }
        
        Item item = new Item.Builder()
            .id(itemId)
            .message(message)
            .userId(userId)
            .teamId(teamId)
            .accessLevel(accessLevel)
            .build();
        
        // Set timestamps if available
        if (dynamoItem.get("createdAt") != null) {
            item.setCreatedAt(Instant.parse(dynamoItem.get("createdAt").s()));
        }
        if (dynamoItem.get("updatedAt") != null) {
            item.setUpdatedAt(Instant.parse(dynamoItem.get("updatedAt").s()));
        }
        
        return item;
    }
    
    /**
     * Converts DynamoDB item to response map
     */
    private Map<String, Object> convertDynamoItemToMap(Map<String, AttributeValue> dynamoItem) {
        Map<String, Object> item = new HashMap<>();
        
        String itemId = extractItemIdFromSortKey(dynamoItem.get("SK").s());
        item.put("id", itemId);
        item.put("message", dynamoItem.get("message") != null ? dynamoItem.get("message").s() : "");
        item.put("userId", dynamoItem.get("userId") != null ? dynamoItem.get("userId").s() : "");
        item.put("createdAt", dynamoItem.get("createdAt") != null ? dynamoItem.get("createdAt").s() : "");
        item.put("updatedAt", dynamoItem.get("updatedAt") != null ? dynamoItem.get("updatedAt").s() : "");
        
        // Add team fields if present
        if (dynamoItem.get("teamId") != null) {
            item.put("teamId", dynamoItem.get("teamId").s());
        }
        if (dynamoItem.get("accessLevel") != null) {
            item.put("accessLevel", dynamoItem.get("accessLevel").s());
        } else {
            item.put("accessLevel", "INDIVIDUAL"); // Default for backward compatibility
        }
        
        return item;
    }
    
    /**
     * Creates an item with team support
     */
    private Map<String, Object> createItemWithTeamSupport(User user, String message, String teamId, Item.AccessLevel accessLevel) {
        if (useLocalMock) {
            return createItemInMock(user, message, teamId, accessLevel);
        } else {
            return createItemInDynamoDB(user, message, teamId, accessLevel);
        }
    }
    
    /**
     * Creates item in mock database for local development
     */
    private Map<String, Object> createItemInMock(User user, String message, String teamId, Item.AccessLevel accessLevel) {
        // For mock database, we'll create a simple item (team features limited in mock)
        String itemId = MockDatabase.createItem(user.getUserId(), message);
        Optional<MockDatabase.Item> createdItem = MockDatabase.getItem(itemId, user.getUserId());
        
        if (!createdItem.isPresent()) {
            throw new RuntimeException("Failed to create item in mock database");
        }
        
        Map<String, Object> response = createdItem.get().toMap();
        
        // Add team fields to response even though mock doesn't fully support them
        if (teamId != null) {
            response.put("teamId", teamId);
        }
        response.put("accessLevel", accessLevel.toString().toLowerCase());
        
        return response;
    }
    
    /**
     * Creates item in DynamoDB with full team support
     */
    private Map<String, Object> createItemInDynamoDB(User user, String message, String teamId, Item.AccessLevel accessLevel) {
        String itemId = "item-" + UUID.randomUUID().toString();
        String now = Instant.now().toString();
        
        // Build the DynamoDB item
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s("USER#" + user.getUserId()).build());
        item.put("SK", AttributeValue.builder().s("ITEM#" + itemId).build());
        item.put("message", AttributeValue.builder().s(message).build());
        item.put("userId", AttributeValue.builder().s(user.getUserId()).build());
        item.put("createdAt", AttributeValue.builder().s(now).build());
        item.put("updatedAt", AttributeValue.builder().s(now).build());
        item.put("createdBy", AttributeValue.builder().s(user.getUserId()).build());
        item.put("accessLevel", AttributeValue.builder().s(accessLevel.toString()).build());
        
        // Add team fields if applicable
        if (teamId != null) {
            item.put("teamId", AttributeValue.builder().s(teamId).build());
        }
        
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();
        
        try {
            dynamoDbService.putItem(putRequest);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("id", itemId);
            response.put("message", message);
            response.put("userId", user.getUserId());
            response.put("createdAt", now);
            response.put("updatedAt", now);
            response.put("createdBy", user.getUserId());
            response.put("accessLevel", accessLevel.toString().toLowerCase());
            
            if (teamId != null) {
                response.put("teamId", teamId);
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to create item in DynamoDB", e);
            throw new RuntimeException("Failed to create item: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts item ID from sort key like "ITEM#item-123"
     */
    private String extractItemIdFromSortKey(String sortKey) {
        if (sortKey.startsWith("ITEM#")) {
            return sortKey.substring(5);
        }
        return sortKey;
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
     * Extracts full User object from JWT token information
     * This assumes API Gateway has already validated the token
     */
    private User getUserFromRequest(APIGatewayProxyRequestEvent input) {
        // Use mock authentication for local development
        String mockAuth = System.getenv("MOCK_AUTHENTICATION") != null ? System.getenv("MOCK_AUTHENTICATION") :
                         System.getProperty("MOCK_AUTHENTICATION");
        if ("true".equals(mockAuth)) {
            String localUserId = System.getenv("LOCAL_TEST_USER_ID") != null ? System.getenv("LOCAL_TEST_USER_ID") :
                                System.getProperty("LOCAL_TEST_USER_ID");
            if (localUserId != null) {
                return authorizationService.createUserFromJWT(localUserId, "testuser", "test@example.com");
            }
            return authorizationService.createUserFromJWT("local-user-12345", "testuser", "test@example.com");
        }
        
        // JWT token validation for AWS Cognito environments
        Map<String, String> headers = input.getHeaders();
        if (headers != null) {
            String authHeader = headers.get("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                // Extract user information from real JWT token
                try {
                    return extractUserFromJWT(token);
                } catch (Exception e) {
                    logger.warn("Failed to extract user from JWT token: {}", e.getMessage());
                }
                
                // Mock fallback for testing
                if (token.startsWith("mock-jwt-token-")) {
                    String mockUserId = "user-" + Math.abs(token.hashCode() % 100000);
                    return authorizationService.createUserFromJWT(mockUserId, "mockuser", "mock@example.com");
                }
            }
        }
        
        // Fallback to mock user
        return authorizationService.createUserFromJWT("user-12345", "fallbackuser", "fallback@example.com");
    }

    /**
     * Extracts User object from JWT token without verification
     * This assumes API Gateway has already validated the token
     */
    private User extractUserFromJWT(String token) {
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
            
            // Parse JSON to extract user information
            JsonNode jsonNode = objectMapper.readTree(decodedPayload);
            
            String userId = getJsonNodeValue(jsonNode, "sub");
            String username = getJsonNodeValue(jsonNode, "cognito:username");
            String email = getJsonNodeValue(jsonNode, "email");
            
            if (userId != null) {
                logger.debug("Extracted user from JWT: userId={}, username={}, email={}", userId, username, email);
                return authorizationService.createUserFromJWT(userId, username, email);
            } else {
                logger.warn("No 'sub' claim found in JWT token");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error extracting user from JWT token: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Helper method to safely extract string values from JsonNode
     */
    private String getJsonNodeValue(JsonNode jsonNode, String fieldName) {
        JsonNode node = jsonNode.get(fieldName);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    /**
     * Extracts user ID (sub claim) from JWT token without verification
     * This assumes API Gateway has already validated the token
     * @deprecated Use getUserFromRequest() instead for full user context
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