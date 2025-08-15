package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.dynamodb.model.*;
import java.time.Duration;
import java.time.Instant;

/**
 * Performance-optimized wrapper for DynamoDB operations.
 * Adds caching, metrics, and optimization features when enabled.
 */
public class OptimizedDynamoDbService implements DynamoDbService {
    
    private final DynamoDbService delegate;
    private final PerformanceOptimizationService performanceService;
    private final FeatureFlagService featureFlagService;
    
    public OptimizedDynamoDbService(DynamoDbService delegate, 
                                   PerformanceOptimizationService performanceService,
                                   FeatureFlagService featureFlagService) {
        this.delegate = delegate;
        this.performanceService = performanceService;
        this.featureFlagService = featureFlagService;
    }
    
    @Override
    public PutItemResponse putItem(PutItemRequest request) {
        return executeWithMetrics("putItem", () -> delegate.putItem(request));
    }
    
    @Override
    public GetItemResponse getItem(GetItemRequest request) {
        // Try cache first for read operations
        if (featureFlagService.isPerformanceOptimizationEnabled()) {
            String cacheKey = performanceService.generateCacheKey(
                request.tableName(), "getItem", 
                request.key().toString()
            );
            
            GetItemResponse cached = performanceService.getCachedResponse(cacheKey, GetItemResponse.class);
            if (cached != null) {
                performanceService.recordPerformanceMetric("getItem-cached", Duration.ofMillis(1), true);
                return cached;
            }
            
            // Execute and cache result
            GetItemResponse response = executeWithMetrics("getItem", () -> delegate.getItem(request));
            
            // Cache successful responses for 30 seconds
            if (response.hasItem()) {
                performanceService.cacheResponse(cacheKey, response, Duration.ofSeconds(30));
            }
            
            return response;
        }
        
        return executeWithMetrics("getItem", () -> delegate.getItem(request));
    }
    
    @Override
    public UpdateItemResponse updateItem(UpdateItemRequest request) {
        UpdateItemResponse response = executeWithMetrics("updateItem", () -> delegate.updateItem(request));
        
        // Invalidate cache for this item
        if (featureFlagService.isPerformanceOptimizationEnabled()) {
            String cacheKey = performanceService.generateCacheKey(
                request.tableName(), "getItem", 
                request.key().toString()
            );
            // Clear the cache by setting an expired entry
            performanceService.cacheResponse(cacheKey, null, Duration.ofSeconds(-1));
        }
        
        return response;
    }
    
    @Override
    public DeleteItemResponse deleteItem(DeleteItemRequest request) {
        DeleteItemResponse response = executeWithMetrics("deleteItem", () -> delegate.deleteItem(request));
        
        // Invalidate cache for this item
        if (featureFlagService.isPerformanceOptimizationEnabled()) {
            String cacheKey = performanceService.generateCacheKey(
                request.tableName(), "getItem", 
                request.key().toString()
            );
            performanceService.cacheResponse(cacheKey, null, Duration.ofSeconds(-1));
        }
        
        return response;
    }
    
    @Override
    public QueryResponse query(QueryRequest request) {
        // Cache query results for read-heavy operations
        if (featureFlagService.isPerformanceOptimizationEnabled()) {
            String cacheKey = performanceService.generateCacheKey(
                request.tableName(), "query",
                request.indexName(), request.keyConditionExpression(),
                request.filterExpression(), request.limit()
            );
            
            QueryResponse cached = performanceService.getCachedResponse(cacheKey, QueryResponse.class);
            if (cached != null) {
                performanceService.recordPerformanceMetric("query-cached", Duration.ofMillis(1), true);
                return cached;
            }
            
            QueryResponse response = executeWithMetrics("query", () -> delegate.query(request));
            
            // Cache query results for 15 seconds (shorter than individual items)
            if (response.hasItems()) {
                performanceService.cacheResponse(cacheKey, response, Duration.ofSeconds(15));
            }
            
            return response;
        }
        
        return executeWithMetrics("query", () -> delegate.query(request));
    }
    
    @Override
    public ScanResponse scan(ScanRequest request) {
        // Don't cache scan operations as they're typically for admin/analytics
        return executeWithMetrics("scan", () -> delegate.scan(request));
    }
    
    /**
     * Execute an operation with performance metrics tracking.
     */
    private <T> T executeWithMetrics(String operation, DynamoDbOperation<T> operation_func) {
        if (!featureFlagService.isPerformanceOptimizationEnabled()) {
            return operation_func.execute();
        }
        
        Instant start = Instant.now();
        boolean success = false;
        
        try {
            T result = operation_func.execute();
            success = true;
            return result;
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            performanceService.recordPerformanceMetric(operation, duration, success);
            
            // Log slow operations
            if (duration.toMillis() > 500) {
                System.err.println("SLOW DynamoDB operation: " + operation + " took " + duration.toMillis() + "ms");
            }
        }
    }
    
    @FunctionalInterface
    private interface DynamoDbOperation<T> {
        T execute();
    }
}