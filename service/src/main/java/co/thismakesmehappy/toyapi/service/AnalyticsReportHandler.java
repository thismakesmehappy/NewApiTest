package co.thismakesmehappy.toyapi.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lambda function for generating scheduled analytics reports.
 * Aggregates usage data and creates summary reports for operational insights.
 */
public class AnalyticsReportHandler implements RequestHandler<ScheduledEvent, String> {

    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String USAGE_METRICS_TABLE = System.getenv("USAGE_METRICS_TABLE");
    private static final String DEVELOPER_INSIGHTS_TABLE = System.getenv("DEVELOPER_INSIGHTS_TABLE");
    private static final String ENVIRONMENT = System.getenv("ENVIRONMENT");

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        // Input validation: Check required environment variables
        if (USAGE_METRICS_TABLE == null || USAGE_METRICS_TABLE.trim().isEmpty()) {
            throw new IllegalStateException("USAGE_METRICS_TABLE environment variable is required");
        }
        if (DEVELOPER_INSIGHTS_TABLE == null || DEVELOPER_INSIGHTS_TABLE.trim().isEmpty()) {
            throw new IllegalStateException("DEVELOPER_INSIGHTS_TABLE environment variable is required");
        }
        if (ENVIRONMENT == null || ENVIRONMENT.trim().isEmpty()) {
            throw new IllegalStateException("ENVIRONMENT environment variable is required");
        }
        
        context.getLogger().log("Starting analytics report generation for environment: " + ENVIRONMENT);
        
        try {
            AnalyticsReport report = new AnalyticsReport();
            
            // Generate different types of reports
            report.dailySummary = generateDailySummary(context);
            report.topEndpoints = getTopEndpoints(context);
            report.errorAnalysis = generateErrorAnalysis(context);
            report.performanceMetrics = generatePerformanceMetrics(context);
            report.developerEngagement = generateDeveloperEngagement(context);
            
            // Log the report summary
            String reportSummary = formatReportSummary(report);
            context.getLogger().log("Analytics Report Generated:\n" + reportSummary);
            
            // Store the report for future reference
            storeReport(report, context);
            
            return "Analytics report generated successfully";
            
        } catch (Exception e) {
            context.getLogger().log("Error generating analytics report: " + e.getMessage());
            throw new RuntimeException("Failed to generate analytics report", e);
        }
    }

    private DailySummary generateDailySummary(Context context) {
        DailySummary summary = new DailySummary();
        
        try {
            // Query last 24 hours of data
            long endTime = Instant.now().getEpochSecond();
            long startTime = endTime - (24 * 60 * 60); // 24 hours ago
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":metricType", AttributeValue.builder().s("api_request").build());
            expressionAttributeValues.put(":startTime", AttributeValue.builder().n(String.valueOf(startTime)).build());
            expressionAttributeValues.put(":endTime", AttributeValue.builder().n(String.valueOf(endTime)).build());
            
            QueryRequest request = QueryRequest.builder()
                    .tableName(USAGE_METRICS_TABLE)
                    .keyConditionExpression("metricType = :metricType AND #timestamp BETWEEN :startTime AND :endTime")
                    .expressionAttributeNames(Map.of("#timestamp", "timestamp"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            QueryResponse response = dynamoDbClient.query(request);
            
            summary.totalRequests = response.items().size();
            summary.uniqueApiKeys = response.items().stream()
                    .map(item -> item.get("apiKey").s())
                    .filter(apiKey -> !"anonymous".equals(apiKey))
                    .collect(Collectors.toSet()).size();
            
            summary.successfulRequests = (int) response.items().stream()
                    .mapToInt(item -> Integer.parseInt(item.get("statusCode").n()))
                    .filter(code -> code >= 200 && code < 300)
                    .count();
            
            summary.errorRequests = summary.totalRequests - summary.successfulRequests;
            
            summary.averageResponseTime = response.items().stream()
                    .mapToInt(item -> Integer.parseInt(item.get("responseTime").n()))
                    .average()
                    .orElse(0.0);
            
        } catch (Exception e) {
            context.getLogger().log("Error generating daily summary: " + e.getMessage());
        }
        
        return summary;
    }

    private List<EndpointUsage> getTopEndpoints(Context context) {
        List<EndpointUsage> topEndpoints = new ArrayList<>();
        
        try {
            // Query last 24 hours and aggregate by endpoint
            long endTime = Instant.now().getEpochSecond();
            long startTime = endTime - (24 * 60 * 60);
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":metricType", AttributeValue.builder().s("api_request").build());
            expressionAttributeValues.put(":startTime", AttributeValue.builder().n(String.valueOf(startTime)).build());
            expressionAttributeValues.put(":endTime", AttributeValue.builder().n(String.valueOf(endTime)).build());
            
            QueryRequest request = QueryRequest.builder()
                    .tableName(USAGE_METRICS_TABLE)
                    .keyConditionExpression("metricType = :metricType AND #timestamp BETWEEN :startTime AND :endTime")
                    .expressionAttributeNames(Map.of("#timestamp", "timestamp"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            QueryResponse response = dynamoDbClient.query(request);
            
            // Aggregate by endpoint
            Map<String, Integer> endpointCounts = new HashMap<>();
            Map<String, Double> endpointResponseTimes = new HashMap<>();
            
            for (Map<String, AttributeValue> item : response.items()) {
                // Input validation: Check for required fields in DynamoDB response
                if (item.get("endpoint") == null || item.get("responseTime") == null) {
                    context.getLogger().log("Warning: Skipping item with missing endpoint or responseTime");
                    continue;
                }
                String endpoint = item.get("endpoint").s();
                int responseTime = Integer.parseInt(item.get("responseTime").n());
                
                endpointCounts.merge(endpoint, 1, Integer::sum);
                endpointResponseTimes.merge(endpoint, (double) responseTime, (a, b) -> (a + b) / 2);
            }
            
            // Convert to sorted list
            topEndpoints = endpointCounts.entrySet().stream()
                    .map(entry -> {
                        EndpointUsage usage = new EndpointUsage();
                        usage.endpoint = entry.getKey();
                        usage.requestCount = entry.getValue();
                        usage.averageResponseTime = endpointResponseTimes.getOrDefault(entry.getKey(), 0.0);
                        return usage;
                    })
                    .sorted((a, b) -> Integer.compare(b.requestCount, a.requestCount))
                    .limit(10)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            context.getLogger().log("Error getting top endpoints: " + e.getMessage());
        }
        
        return topEndpoints;
    }

    private ErrorAnalysis generateErrorAnalysis(Context context) {
        ErrorAnalysis analysis = new ErrorAnalysis();
        
        try {
            // Query last 24 hours for error analysis
            long endTime = Instant.now().getEpochSecond();
            long startTime = endTime - (24 * 60 * 60);
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":metricType", AttributeValue.builder().s("api_request").build());
            expressionAttributeValues.put(":startTime", AttributeValue.builder().n(String.valueOf(startTime)).build());
            expressionAttributeValues.put(":endTime", AttributeValue.builder().n(String.valueOf(endTime)).build());
            
            QueryRequest request = QueryRequest.builder()
                    .tableName(USAGE_METRICS_TABLE)
                    .keyConditionExpression("metricType = :metricType AND #timestamp BETWEEN :startTime AND :endTime")
                    .expressionAttributeNames(Map.of("#timestamp", "timestamp"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            QueryResponse response = dynamoDbClient.query(request);
            
            Map<String, Integer> errorCodes = new HashMap<>();
            int totalRequests = response.items().size();
            int errorCount = 0;
            
            for (Map<String, AttributeValue> item : response.items()) {
                int statusCode = Integer.parseInt(item.get("statusCode").n());
                if (statusCode >= 400) {
                    errorCount++;
                    String errorType = getErrorType(statusCode);
                    errorCodes.merge(errorType, 1, Integer::sum);
                }
            }
            
            analysis.totalErrors = errorCount;
            analysis.errorRate = totalRequests > 0 ? (double) errorCount / totalRequests * 100 : 0.0;
            analysis.errorsByType = errorCodes;
            
        } catch (Exception e) {
            context.getLogger().log("Error generating error analysis: " + e.getMessage());
        }
        
        return analysis;
    }

    private PerformanceMetrics generatePerformanceMetrics(Context context) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        try {
            // Query last 24 hours for performance analysis
            long endTime = Instant.now().getEpochSecond();
            long startTime = endTime - (24 * 60 * 60);
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":metricType", AttributeValue.builder().s("api_request").build());
            expressionAttributeValues.put(":startTime", AttributeValue.builder().n(String.valueOf(startTime)).build());
            expressionAttributeValues.put(":endTime", AttributeValue.builder().n(String.valueOf(endTime)).build());
            
            QueryRequest request = QueryRequest.builder()
                    .tableName(USAGE_METRICS_TABLE)
                    .keyConditionExpression("metricType = :metricType AND #timestamp BETWEEN :startTime AND :endTime")
                    .expressionAttributeNames(Map.of("#timestamp", "timestamp"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            QueryResponse response = dynamoDbClient.query(request);
            
            if (!response.items().isEmpty()) {
                double[] responseTimes = response.items().stream()
                        .mapToDouble(item -> Double.parseDouble(item.get("responseTime").n()))
                        .sorted()
                        .toArray();
                
                metrics.averageResponseTime = Arrays.stream(responseTimes).average().orElse(0.0);
                metrics.p50ResponseTime = getPercentile(responseTimes, 0.5);
                metrics.p95ResponseTime = getPercentile(responseTimes, 0.95);
                metrics.p99ResponseTime = getPercentile(responseTimes, 0.99);
                metrics.slowRequestCount = (int) Arrays.stream(responseTimes).filter(time -> time > 1000).count();
            }
            
        } catch (Exception e) {
            context.getLogger().log("Error generating performance metrics: " + e.getMessage());
        }
        
        return metrics;
    }

    private DeveloperEngagement generateDeveloperEngagement(Context context) {
        DeveloperEngagement engagement = new DeveloperEngagement();
        
        try {
            // Scan developer insights table for recent activity
            ScanRequest request = ScanRequest.builder()
                    .tableName(DEVELOPER_INSIGHTS_TABLE)
                    .build();
            
            ScanResponse response = dynamoDbClient.scan(request);
            
            Set<String> activeDevelopers = new HashSet<>();
            int totalUsage = 0;
            
            for (Map<String, AttributeValue> item : response.items()) {
                String developerId = item.get("developerId").s();
                String insightType = item.get("insightType").s();
                
                activeDevelopers.add(developerId);
                
                if (insightType.startsWith("daily_usage") && item.containsKey("count")) {
                    totalUsage += Integer.parseInt(item.get("count").n());
                }
            }
            
            engagement.activeDeveloperCount = activeDevelopers.size();
            engagement.totalApiCalls = totalUsage;
            engagement.averageCallsPerDeveloper = activeDevelopers.size() > 0 ? 
                    (double) totalUsage / activeDevelopers.size() : 0.0;
            
        } catch (Exception e) {
            context.getLogger().log("Error generating developer engagement: " + e.getMessage());
        }
        
        return engagement;
    }

    private void storeReport(AnalyticsReport report, Context context) {
        try {
            String reportJson = objectMapper.writeValueAsString(report);
            long timestamp = Instant.now().getEpochSecond();
            long ttl = timestamp + (90 * 24 * 60 * 60); // 90 days retention
            
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("metricType", AttributeValue.builder().s("analytics_report").build());
            item.put("timestamp", AttributeValue.builder().n(String.valueOf(timestamp)).build());
            item.put("reportData", AttributeValue.builder().s(reportJson).build());
            item.put("environment", AttributeValue.builder().s(ENVIRONMENT).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(USAGE_METRICS_TABLE)
                    .item(item)
                    .build();
            
            dynamoDbClient.putItem(request);
            
        } catch (Exception e) {
            context.getLogger().log("Error storing report: " + e.getMessage());
        }
    }

    private String formatReportSummary(AnalyticsReport report) {
        StringBuilder summary = new StringBuilder();
        summary.append("=== DAILY ANALYTICS REPORT ===\n");
        summary.append(String.format("Total Requests: %d\n", report.dailySummary.totalRequests));
        summary.append(String.format("Success Rate: %.1f%%\n", 
                (double) report.dailySummary.successfulRequests / report.dailySummary.totalRequests * 100));
        summary.append(String.format("Active Developers: %d\n", report.dailySummary.uniqueApiKeys));
        summary.append(String.format("Average Response Time: %.1fms\n", report.dailySummary.averageResponseTime));
        summary.append(String.format("Error Rate: %.1f%%\n", report.errorAnalysis.errorRate));
        summary.append(String.format("P95 Response Time: %.1fms\n", report.performanceMetrics.p95ResponseTime));
        
        summary.append("\nTop Endpoints:\n");
        report.topEndpoints.stream().limit(5).forEach(endpoint -> 
                summary.append(String.format("  %s: %d calls (%.1fms avg)\n", 
                        endpoint.endpoint, endpoint.requestCount, endpoint.averageResponseTime)));
        
        return summary.toString();
    }

    private String getErrorType(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            return "4xx_client_errors";
        } else if (statusCode >= 500) {
            return "5xx_server_errors";
        }
        return "unknown_errors";
    }

    private double getPercentile(double[] sortedValues, double percentile) {
        if (sortedValues.length == 0) return 0.0;
        int index = (int) Math.ceil(percentile * sortedValues.length) - 1;
        return sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
    }

    // Data classes for report structure
    public static class AnalyticsReport {
        public DailySummary dailySummary;
        public List<EndpointUsage> topEndpoints;
        public ErrorAnalysis errorAnalysis;
        public PerformanceMetrics performanceMetrics;
        public DeveloperEngagement developerEngagement;
    }

    public static class DailySummary {
        public int totalRequests;
        public int successfulRequests;
        public int errorRequests;
        public int uniqueApiKeys;
        public double averageResponseTime;
    }

    public static class EndpointUsage {
        public String endpoint;
        public int requestCount;
        public double averageResponseTime;
    }

    public static class ErrorAnalysis {
        public int totalErrors;
        public double errorRate;
        public Map<String, Integer> errorsByType;
    }

    public static class PerformanceMetrics {
        public double averageResponseTime;
        public double p50ResponseTime;
        public double p95ResponseTime;
        public double p99ResponseTime;
        public int slowRequestCount;
    }

    public static class DeveloperEngagement {
        public int activeDeveloperCount;
        public int totalApiCalls;
        public double averageCallsPerDeveloper;
    }
}