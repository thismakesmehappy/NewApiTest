package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * Interface for DynamoDB operations to support dependency injection and testing.
 * Provides abstraction over AWS DynamoDB client operations.
 */
public interface DynamoDbService {
    
    /**
     * Puts an item into a DynamoDB table.
     * 
     * @param request The PutItemRequest
     * @return The PutItemResponse
     */
    PutItemResponse putItem(PutItemRequest request);
    
    /**
     * Gets an item from a DynamoDB table.
     * 
     * @param request The GetItemRequest
     * @return The GetItemResponse
     */
    GetItemResponse getItem(GetItemRequest request);
    
    /**
     * Updates an item in a DynamoDB table.
     * 
     * @param request The UpdateItemRequest
     * @return The UpdateItemResponse
     */
    UpdateItemResponse updateItem(UpdateItemRequest request);
    
    /**
     * Deletes an item from a DynamoDB table.
     * 
     * @param request The DeleteItemRequest
     * @return The DeleteItemResponse
     */
    DeleteItemResponse deleteItem(DeleteItemRequest request);
    
    /**
     * Queries a DynamoDB table.
     * 
     * @param request The QueryRequest
     * @return The QueryResponse
     */
    QueryResponse query(QueryRequest request);
    
    /**
     * Scans a DynamoDB table.
     * 
     * @param request The ScanRequest
     * @return The ScanResponse
     */
    ScanResponse scan(ScanRequest request);
}