package co.thismakesmehappy.toyapi.service.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.AwsDynamoDbService;

import java.time.Instant;
import java.util.*;

/**
 * Lambda handler for developer API key management
 * Handles CRUD operations for developer accounts and API keys
 */
public class DeveloperHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbService dynamoDbService;
    private ApiGatewayClient apiGatewayClient;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String apiNamePrefix;
    private final String usagePlanPrefix;
    
    // Cached values discovered at runtime
    private String restApiId;
    private String usagePlanId;

    /**
     * Default constructor for Lambda runtime.
     * Creates services with default AWS clients.
     */
    public DeveloperHandler() {
        DynamoDbClient dynamoClient = DynamoDbClient.builder().build();
        this.dynamoDbService = new AwsDynamoDbService(dynamoClient);
        this.objectMapper = new ObjectMapper();
        this.tableName = System.getenv("TABLE_NAME");
        this.apiNamePrefix = System.getenv("API_NAME_PREFIX");
        this.usagePlanPrefix = System.getenv("USAGE_PLAN_PREFIX");
    }
    
    /**
     * Constructor for dependency injection (testing).
     * 
     * @param dynamoDbService The DynamoDB service to use
     */
    public DeveloperHandler(DynamoDbService dynamoDbService) {
        this.dynamoDbService = dynamoDbService;
        this.objectMapper = new ObjectMapper();
        // For testing, use system properties as fallback if env vars are not set
        this.tableName = System.getenv("TABLE_NAME") != null ? System.getenv("TABLE_NAME") : 
                        System.getProperty("TABLE_NAME", "test-table");
        this.apiNamePrefix = System.getenv("API_NAME_PREFIX") != null ? System.getenv("API_NAME_PREFIX") : 
                            System.getProperty("API_NAME_PREFIX", "test-api");
        this.usagePlanPrefix = System.getenv("USAGE_PLAN_PREFIX") != null ? System.getenv("USAGE_PLAN_PREFIX") : 
                              System.getProperty("USAGE_PLAN_PREFIX", "test-plan");
    }
    
    private synchronized ApiGatewayClient getApiGatewayClient() {
        if (apiGatewayClient == null) {
            apiGatewayClient = ApiGatewayClient.builder().build();
        }
        return apiGatewayClient;
    }
    
    /**
     * Discovers and caches the REST API ID by searching for APIs with the configured name prefix
     */
    private String getRestApiId() {
        if (restApiId != null) {
            return restApiId;
        }
        
        try {
            GetRestApisResponse response = getApiGatewayClient().getRestApis();
            for (RestApi api : response.items()) {
                if (api.name().startsWith(apiNamePrefix)) {
                    restApiId = api.id();
                    return restApiId;
                }
            }
            throw new RuntimeException("Could not find REST API with name prefix: " + apiNamePrefix);
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover REST API ID", e);
        }
    }
    
    /**
     * Discovers and caches the Usage Plan ID by searching for usage plans with the configured name prefix
     */
    private String getUsagePlanId() {
        if (usagePlanId != null) {
            return usagePlanId;
        }
        
        try {
            GetUsagePlansResponse response = getApiGatewayClient().getUsagePlans();
            for (UsagePlan plan : response.items()) {
                if (plan.name().startsWith(usagePlanPrefix)) {
                    usagePlanId = plan.id();
                    return usagePlanId;
                }
            }
            throw new RuntimeException("Could not find Usage Plan with name prefix: " + usagePlanPrefix);
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover Usage Plan ID", e);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String path = request.getPath();
            String method = request.getHttpMethod();
            
            context.getLogger().log("Processing request: " + method + " " + path);

            switch (method) {
                case "POST":
                    if (path.equals("/developer/register")) {
                        return registerDeveloper(request, context);
                    } else if (path.equals("/developer/api-key")) {
                        return createApiKey(request, context);
                    }
                    break;
                case "GET":
                    if (path.equals("/developer/profile")) {
                        return getDeveloperProfile(request, context);
                    } else if (path.equals("/developer/api-keys")) {
                        return listApiKeys(request, context);
                    }
                    break;
                case "PUT":
                    if (path.equals("/developer/profile")) {
                        return updateDeveloperProfile(request, context);
                    }
                    break;
                case "DELETE":
                    if (path.matches("/developer/api-key/.*")) {
                        return deleteApiKey(request, context);
                    }
                    break;
            }

            return createErrorResponse(404, "Endpoint not found");

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent registerDeveloper(APIGatewayProxyRequestEvent request, Context context) {
        try {
            JsonNode requestBody = objectMapper.readTree(request.getBody());
            
            String email = requestBody.get("email").asText();
            String name = requestBody.get("name").asText();
            String organization = requestBody.has("organization") ? requestBody.get("organization").asText() : "";
            String purpose = requestBody.has("purpose") ? requestBody.get("purpose").asText() : "";

            // Check if developer already exists
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("PROFILE").build()
                    ))
                    .build();

            GetItemResponse getResponse = dynamoDbService.getItem(getRequest);
            if (getResponse.hasItem()) {
                return createErrorResponse(409, "Developer already registered");
            }

            // Create developer profile
            String developerId = UUID.randomUUID().toString();
            String createdAt = Instant.now().toString();

            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("PROFILE").build(),
                        "developerId", AttributeValue.builder().s(developerId).build(),
                        "email", AttributeValue.builder().s(email).build(),
                        "name", AttributeValue.builder().s(name).build(),
                        "organization", AttributeValue.builder().s(organization).build(),
                        "purpose", AttributeValue.builder().s(purpose).build(),
                        "status", AttributeValue.builder().s("ACTIVE").build(),
                        "createdAt", AttributeValue.builder().s(createdAt).build(),
                        "updatedAt", AttributeValue.builder().s(createdAt).build()
                    ))
                    .build();

            dynamoDbService.putItem(putRequest);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("developerId", developerId);
            response.put("email", email);
            response.put("name", name);
            response.put("status", "ACTIVE");
            response.put("message", "Developer registered successfully");

            return createSuccessResponse(201, response);

        } catch (Exception e) {
            context.getLogger().log("Error registering developer: " + e.getMessage());
            return createErrorResponse(500, "Failed to register developer");
        }
    }

    private APIGatewayProxyResponseEvent createApiKey(APIGatewayProxyRequestEvent request, Context context) {
        try {
            JsonNode requestBody = objectMapper.readTree(request.getBody());
            String email = requestBody.get("email").asText();
            String keyName = requestBody.has("name") ? requestBody.get("name").asText() : "Default Key";

            // Verify developer exists
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("PROFILE").build()
                    ))
                    .build();

            GetItemResponse getResponse = dynamoDbService.getItem(getRequest);
            if (!getResponse.hasItem()) {
                return createErrorResponse(404, "Developer not found");
            }

            String developerId = getResponse.item().get("developerId").s();

            // Create API key in AWS API Gateway
            CreateApiKeyRequest createKeyRequest = CreateApiKeyRequest.builder()
                    .name(keyName + " - " + email)
                    .description("API key for " + email + " (" + developerId + ")")
                    .enabled(true)
                    .build();

            CreateApiKeyResponse createKeyResponse = getApiGatewayClient().createApiKey(createKeyRequest);
            String apiKeyId = createKeyResponse.id();
            String apiKeyValue = createKeyResponse.value();

            // Associate API key with usage plan
            CreateUsagePlanKeyRequest usagePlanKeyRequest = CreateUsagePlanKeyRequest.builder()
                    .usagePlanId(getUsagePlanId())
                    .keyId(apiKeyId)
                    .keyType("API_KEY")
                    .build();

            getApiGatewayClient().createUsagePlanKey(usagePlanKeyRequest);

            // Store API key metadata in DynamoDB
            String createdAt = Instant.now().toString();
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("APIKEY#" + apiKeyId).build(),
                        "apiKeyId", AttributeValue.builder().s(apiKeyId).build(),
                        "keyName", AttributeValue.builder().s(keyName).build(),
                        "status", AttributeValue.builder().s("ACTIVE").build(),
                        "createdAt", AttributeValue.builder().s(createdAt).build(),
                        "lastUsed", AttributeValue.builder().s("").build()
                    ))
                    .build();

            dynamoDbService.putItem(putRequest);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("apiKeyId", apiKeyId);
            response.put("apiKey", apiKeyValue);
            response.put("keyName", keyName);
            response.put("status", "ACTIVE");
            response.put("message", "API key created successfully");

            return createSuccessResponse(201, response);

        } catch (Exception e) {
            context.getLogger().log("Error creating API key: " + e.getMessage());
            return createErrorResponse(500, "Failed to create API key");
        }
    }

    private APIGatewayProxyResponseEvent getDeveloperProfile(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String email = request.getQueryStringParameters().get("email");
            if (email == null) {
                return createErrorResponse(400, "Email parameter is required");
            }

            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("PROFILE").build()
                    ))
                    .build();

            GetItemResponse getResponse = dynamoDbService.getItem(getRequest);
            if (!getResponse.hasItem()) {
                return createErrorResponse(404, "Developer not found");
            }

            Map<String, AttributeValue> item = getResponse.item();
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("developerId", item.get("developerId").s());
            response.put("email", item.get("email").s());
            response.put("name", item.get("name").s());
            response.put("organization", item.get("organization").s());
            response.put("purpose", item.get("purpose").s());
            response.put("status", item.get("status").s());
            response.put("createdAt", item.get("createdAt").s());
            response.put("updatedAt", item.get("updatedAt").s());

            return createSuccessResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Error getting developer profile: " + e.getMessage());
            return createErrorResponse(500, "Failed to get developer profile");
        }
    }

    private APIGatewayProxyResponseEvent listApiKeys(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String email = request.getQueryStringParameters().get("email");
            if (email == null) {
                return createErrorResponse(400, "Email parameter is required");
            }

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s("DEV#" + email).build(),
                        ":sk", AttributeValue.builder().s("APIKEY#").build()
                    ))
                    .build();

            QueryResponse queryResponse = dynamoDbService.query(queryRequest);
            
            List<ObjectNode> apiKeys = new ArrayList<>();
            for (Map<String, AttributeValue> item : queryResponse.items()) {
                ObjectNode apiKey = objectMapper.createObjectNode();
                apiKey.put("apiKeyId", item.get("apiKeyId").s());
                apiKey.put("keyName", item.get("keyName").s());
                apiKey.put("status", item.get("status").s());
                apiKey.put("createdAt", item.get("createdAt").s());
                if (item.containsKey("lastUsed") && !item.get("lastUsed").s().isEmpty()) {
                    apiKey.put("lastUsed", item.get("lastUsed").s());
                }
                apiKeys.add(apiKey);
            }

            ObjectNode response = objectMapper.createObjectNode();
            response.set("apiKeys", objectMapper.valueToTree(apiKeys));
            response.put("count", apiKeys.size());

            return createSuccessResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Error listing API keys: " + e.getMessage());
            return createErrorResponse(500, "Failed to list API keys");
        }
    }

    private APIGatewayProxyResponseEvent updateDeveloperProfile(APIGatewayProxyRequestEvent request, Context context) {
        try {
            JsonNode requestBody = objectMapper.readTree(request.getBody());
            String email = requestBody.get("email").asText();

            // Verify developer exists
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("PROFILE").build()
                    ))
                    .build();

            GetItemResponse getResponse = dynamoDbService.getItem(getRequest);
            if (!getResponse.hasItem()) {
                return createErrorResponse(404, "Developer not found");
            }

            // Build update expression
            Map<String, String> updateExpression = new HashMap<>();
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            List<String> updates = new ArrayList<>();

            if (requestBody.has("name")) {
                updates.add("name = :name");
                expressionValues.put(":name", AttributeValue.builder().s(requestBody.get("name").asText()).build());
            }
            if (requestBody.has("organization")) {
                updates.add("organization = :org");
                expressionValues.put(":org", AttributeValue.builder().s(requestBody.get("organization").asText()).build());
            }
            if (requestBody.has("purpose")) {
                updates.add("purpose = :purpose");
                expressionValues.put(":purpose", AttributeValue.builder().s(requestBody.get("purpose").asText()).build());
            }

            updates.add("updatedAt = :updatedAt");
            expressionValues.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());

            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("PROFILE").build()
                    ))
                    .updateExpression("SET " + String.join(", ", updates))
                    .expressionAttributeValues(expressionValues)
                    .returnValues(ReturnValue.ALL_NEW)
                    .build();

            UpdateItemResponse updateResponse = dynamoDbService.updateItem(updateRequest);
            Map<String, AttributeValue> item = updateResponse.attributes();

            ObjectNode response = objectMapper.createObjectNode();
            response.put("developerId", item.get("developerId").s());
            response.put("email", item.get("email").s());
            response.put("name", item.get("name").s());
            response.put("organization", item.get("organization").s());
            response.put("purpose", item.get("purpose").s());
            response.put("status", item.get("status").s());
            response.put("updatedAt", item.get("updatedAt").s());
            response.put("message", "Developer profile updated successfully");

            return createSuccessResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Error updating developer profile: " + e.getMessage());
            return createErrorResponse(500, "Failed to update developer profile");
        }
    }

    private APIGatewayProxyResponseEvent deleteApiKey(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String[] pathParts = request.getPath().split("/");
            String apiKeyId = pathParts[pathParts.length - 1];
            
            String email = request.getQueryStringParameters().get("email");
            if (email == null) {
                return createErrorResponse(400, "Email parameter is required");
            }

            // Delete from API Gateway
            DeleteApiKeyRequest deleteKeyRequest = DeleteApiKeyRequest.builder()
                    .apiKey(apiKeyId)
                    .build();

            try {
                getApiGatewayClient().deleteApiKey(deleteKeyRequest);
            } catch (NotFoundException e) {
                context.getLogger().log("API key not found in API Gateway, continuing with DynamoDB cleanup");
            }

            // Delete from DynamoDB
            DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                        "PK", AttributeValue.builder().s("DEV#" + email).build(),
                        "SK", AttributeValue.builder().s("APIKEY#" + apiKeyId).build()
                    ))
                    .build();

            dynamoDbService.deleteItem(deleteRequest);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("message", "API key deleted successfully");
            response.put("apiKeyId", apiKeyId);

            return createSuccessResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Error deleting API key: " + e.getMessage());
            return createErrorResponse(500, "Failed to delete API key");
        }
    }

    private APIGatewayProxyResponseEvent createSuccessResponse(int statusCode, ObjectNode body) {
        try {
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(statusCode);
            response.setBody(objectMapper.writeValueAsString(body));
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key");
            response.setHeaders(headers);
            
            return response;
        } catch (Exception e) {
            return createErrorResponse(500, "Failed to create response");
        }
    }

    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        
        ObjectNode errorBody = objectMapper.createObjectNode();
        errorBody.put("error", message);
        errorBody.put("statusCode", statusCode);
        
        try {
            response.setBody(objectMapper.writeValueAsString(errorBody));
        } catch (Exception e) {
            response.setBody("{\"error\":\"" + message + "\",\"statusCode\":" + statusCode + "}");
        }
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key");
        response.setHeaders(headers);
        
        return response;
    }
}