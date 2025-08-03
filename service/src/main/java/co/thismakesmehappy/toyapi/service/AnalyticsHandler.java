package co.thismakesmehappy.toyapi.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lambda function for processing analytics events from Kinesis stream.
 * Processes real-time API usage data and stores metrics in DynamoDB.
 */
public class AnalyticsHandler implements RequestHandler<KinesisEvent, String> {

    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String USAGE_METRICS_TABLE = System.getenv("USAGE_METRICS_TABLE");
    private static final String DEVELOPER_INSIGHTS_TABLE = System.getenv("DEVELOPER_INSIGHTS_TABLE");
    private static final String ENVIRONMENT = System.getenv("ENVIRONMENT");

    @Override
    public String handleRequest(KinesisEvent event, Context context) {
        context.getLogger().log("Processing " + event.getRecords().size() + " analytics events");
        
        int processedCount = 0;
        int errorCount = 0;
        
        for (KinesisEvent.KinesisEventRecord record : event.getRecords()) {
            try {
                processAnalyticsRecord(record, context);
                processedCount++;
            } catch (Exception e) {
                errorCount++;
                context.getLogger().log("Error processing record: " + e.getMessage());
                // Continue processing other records
            }
        }
        
        String result = String.format("Processed: %d, Errors: %d", processedCount, errorCount);
        context.getLogger().log(result);
        return result;
    }

    private void processAnalyticsRecord(KinesisEvent.KinesisEventRecord record, Context context) {
        try {
            // Decode the Kinesis record data
            String data = new String(record.getKinesis().getData().array());
            
            // Input validation: Check for empty or excessively large payloads
            if (data == null || data.trim().isEmpty()) {
                throw new IllegalArgumentException("Empty analytics record data");
            }
            if (data.length() > 10000) { // 10KB limit
                throw new IllegalArgumentException("Analytics record data exceeds size limit");
            }
            
            JsonNode eventData = objectMapper.readTree(data);
            
            // Extract and validate key metrics from the event
            String eventType = getValidatedStringValue(eventData, "eventType", "api_request");
            String timestamp = getValidatedTimestamp(eventData, "timestamp");
            String endpoint = getValidatedEndpoint(eventData, "endpoint");
            String method = getValidatedHttpMethod(eventData, "method");
            String apiKey = getValidatedApiKey(eventData, "apiKey");
            int responseTime = getValidatedResponseTime(eventData, "responseTime");
            int statusCode = getValidatedStatusCode(eventData, "statusCode");
            String userAgent = getValidatedUserAgent(eventData, "userAgent");
            
            // Store usage metrics
            storeUsageMetrics(eventType, timestamp, endpoint, method, apiKey, responseTime, statusCode, userAgent, context);
            
            // Update developer insights if API key is present
            if (!"anonymous".equals(apiKey)) {
                updateDeveloperInsights(apiKey, endpoint, statusCode, responseTime, context);
            }
            
        } catch (Exception e) {
            context.getLogger().log("Error parsing analytics record: " + e.getMessage());
            throw new RuntimeException("Failed to process analytics record", e);
        }
    }

    private void storeUsageMetrics(String eventType, String timestamp, String endpoint, String method, 
                                 String apiKey, int responseTime, int statusCode, String userAgent, Context context) {
        try {
            long timestampEpoch = Instant.parse(timestamp).getEpochSecond();
            long ttl = timestampEpoch + (30 * 24 * 60 * 60); // 30 days retention
            
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("metricType", AttributeValue.builder().s(eventType).build());
            item.put("timestamp", AttributeValue.builder().n(String.valueOf(timestampEpoch)).build());
            item.put("endpoint", AttributeValue.builder().s(endpoint).build());
            item.put("method", AttributeValue.builder().s(method).build());
            item.put("apiKey", AttributeValue.builder().s(apiKey).build());
            item.put("responseTime", AttributeValue.builder().n(String.valueOf(responseTime)).build());
            item.put("statusCode", AttributeValue.builder().n(String.valueOf(statusCode)).build());
            item.put("userAgent", AttributeValue.builder().s(userAgent).build());
            item.put("environment", AttributeValue.builder().s(ENVIRONMENT).build());
            item.put("recordId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(USAGE_METRICS_TABLE)
                    .item(item)
                    .build();
            
            dynamoDbClient.putItem(request);
            
        } catch (Exception e) {
            context.getLogger().log("Error storing usage metrics: " + e.getMessage());
            throw new RuntimeException("Failed to store usage metrics", e);
        }
    }

    private void updateDeveloperInsights(String apiKey, String endpoint, int statusCode, int responseTime, Context context) {
        try {
            // Update endpoint usage count
            updateInsightCounter(apiKey, "endpoint_usage", endpoint, context);
            
            // Update error count if status code indicates error
            if (statusCode >= 400) {
                updateInsightCounter(apiKey, "error_count", String.valueOf(statusCode), context);
            }
            
            // Update slow request count if response time is high
            if (responseTime > 1000) { // Greater than 1 second
                updateInsightCounter(apiKey, "slow_requests", "over_1s", context);
            }
            
            // Update daily usage
            String today = Instant.now().toString().substring(0, 10); // YYYY-MM-DD
            updateInsightCounter(apiKey, "daily_usage", today, context);
            
        } catch (Exception e) {
            context.getLogger().log("Error updating developer insights: " + e.getMessage());
            // Don't throw exception here to avoid breaking the main flow
        }
    }

    private void updateInsightCounter(String developerId, String insightType, String subKey, Context context) {
        try {
            String sortKey = insightType + "#" + subKey;
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("developerId", AttributeValue.builder().s(developerId).build());
            key.put("insightType", AttributeValue.builder().s(sortKey).build());
            
            Map<String, String> expressionAttributeNames = new HashMap<>();
            expressionAttributeNames.put("#count", "count");
            expressionAttributeNames.put("#lastUpdated", "lastUpdated");
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":inc", AttributeValue.builder().n("1").build());
            expressionAttributeValues.put(":timestamp", AttributeValue.builder().s(Instant.now().toString()).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(DEVELOPER_INSIGHTS_TABLE)
                    .key(key)
                    .updateExpression("ADD #count :inc SET #lastUpdated = :timestamp")
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            
        } catch (Exception e) {
            context.getLogger().log("Error updating insight counter for " + insightType + ": " + e.getMessage());
        }
    }

    private String getValidatedStringValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        String value = (field != null && !field.isNull()) ? field.asText() : defaultValue;
        
        // Sanitize and validate string length
        if (value == null) return defaultValue;
        value = value.trim();
        if (value.length() > 1000) { // Reasonable limit for most string fields
            throw new IllegalArgumentException("Field " + fieldName + " exceeds maximum length");
        }
        return value.isEmpty() ? defaultValue : value;
    }

    private String getValidatedTimestamp(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        String timestamp = (field != null && !field.isNull()) ? field.asText() : Instant.now().toString();
        
        try {
            // Validate timestamp format and reasonableness
            Instant instant = Instant.parse(timestamp);
            Instant now = Instant.now();
            Instant oneYearAgo = now.minus(365, java.time.temporal.ChronoUnit.DAYS);
            Instant oneHourFuture = now.plus(1, java.time.temporal.ChronoUnit.HOURS);
            
            if (instant.isBefore(oneYearAgo) || instant.isAfter(oneHourFuture)) {
                throw new IllegalArgumentException("Timestamp outside acceptable range");
            }
            return timestamp;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timestamp format: " + timestamp);
        }
    }

    private String getValidatedEndpoint(JsonNode node, String fieldName) {
        String endpoint = getValidatedStringValue(node, fieldName, "unknown");
        
        // Validate endpoint format (should start with / for REST APIs)
        if (!"unknown".equals(endpoint)) {
            if (!endpoint.startsWith("/") || endpoint.contains("..") || endpoint.length() > 200) {
                throw new IllegalArgumentException("Invalid endpoint format: " + endpoint);
            }
        }
        return endpoint;
    }

    private String getValidatedHttpMethod(JsonNode node, String fieldName) {
        String method = getValidatedStringValue(node, fieldName, "unknown").toUpperCase();
        
        // Validate HTTP method
        if (!"unknown".equals(method)) {
            if (!java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD").contains(method)) {
                throw new IllegalArgumentException("Invalid HTTP method: " + method);
            }
        }
        return method;
    }

    private String getValidatedApiKey(JsonNode node, String fieldName) {
        String apiKey = getValidatedStringValue(node, fieldName, "anonymous");
        
        // Validate API key format (should be alphanumeric if not anonymous)
        if (!"anonymous".equals(apiKey)) {
            if (!apiKey.matches("^[a-zA-Z0-9]{20,50}$")) {
                // Log but don't fail - mask the actual key for security
                return "invalid_key_format";
            }
        }
        return apiKey;
    }

    private int getValidatedResponseTime(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        int responseTime = (field != null && !field.isNull()) ? field.asInt() : 0;
        
        // Validate response time bounds (0ms to 5 minutes)
        if (responseTime < 0 || responseTime > 300000) {
            throw new IllegalArgumentException("Response time outside valid range: " + responseTime);
        }
        return responseTime;
    }

    private int getValidatedStatusCode(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        int statusCode = (field != null && !field.isNull()) ? field.asInt() : 200;
        
        // Validate HTTP status code range
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Invalid HTTP status code: " + statusCode);
        }
        return statusCode;
    }

    private String getValidatedUserAgent(JsonNode node, String fieldName) {
        String userAgent = getValidatedStringValue(node, fieldName, "unknown");
        
        // Truncate extremely long user agents and sanitize
        if (userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }
        
        // Remove potentially dangerous characters
        userAgent = userAgent.replaceAll("[<>\"']", "");
        return userAgent;
    }

}