package co.thismakesmehappy.toyapi.service.services.items;

import co.thismakesmehappy.toyapi.service.components.items.*;
import co.thismakesmehappy.toyapi.service.pipeline.*;
import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Service for GET /items endpoint.
 * Follows Amazon/Coral pattern with enhanced service pipeline architecture.
 */
public class GetItemsService extends ServicePipeline<GetItemsService.GetItemsRequest, ItemContextFactory.ItemRetrievalContext, Map<String, Object>> {
    
    private static final Logger logger = LoggerFactory.getLogger(GetItemsService.class);
    
    // Specialized components for each concern
    private final ItemValidationService validationService;
    private final ItemDecorationService decorationService;
    private final ItemBusinessRulesService businessRulesService;
    private final ItemResponseBuilder responseBuilder;
    
    public GetItemsService(DynamoDbService dynamoDbService, String tableName, boolean useLocalMock) {
        // Initialize specialized services
        this.validationService = new ItemValidationService();
        this.decorationService = new ItemDecorationService(dynamoDbService, useLocalMock);
        this.businessRulesService = new ItemBusinessRulesService();
        this.responseBuilder = new ItemResponseBuilder();
    }
    
    /**
     * Request model for getting items.
     */
    public static class GetItemsRequest {
        private final String userId;
        private final Integer limit;
        private final String lastEvaluatedKey;
        private final String sortOrder;
        
        public GetItemsRequest(String userId, Integer limit, String lastEvaluatedKey, String sortOrder) {
            this.userId = userId;
            this.limit = limit;
            this.lastEvaluatedKey = lastEvaluatedKey;
            this.sortOrder = sortOrder;
        }
        
        public String getUserId() { return userId; }
        public Integer getLimit() { return limit; }
        public String getLastEvaluatedKey() { return lastEvaluatedKey; }
        public String getSortOrder() { return sortOrder; }
    }
    
    // Pipeline phases delegate to specialized services
    @Override
    protected void validateInput(GetItemsRequest request) throws ValidationException {
        logger.debug("Phase 1: Input validation for user: {}", request.getUserId());
        validationService.validateGetItemsRequest(
            request.getUserId(),
            request.getLimit(),
            request.getSortOrder()
        );
    }
    
    @Override
    protected ValidationResult validateInputComprehensive(GetItemsRequest request) {
        logger.debug("Phase 1: Comprehensive input validation for user: {}", request.getUserId());
        return validationService.validateGetItemsRequestComprehensive(
            request.getUserId(),
            request.getLimit(),
            request.getSortOrder()
        );
    }
    
    @Override
    protected ItemContextFactory.ItemRetrievalContext createContext(GetItemsRequest request) {
        logger.debug("Phase 2: Creating context for user: {}", request.getUserId());
        return ItemContextFactory.createItemRetrievalContext(
            request.getUserId(),
            request.getLimit(),
            request.getLastEvaluatedKey(),
            request.getSortOrder()
        );
    }
    
    @Override
    protected void decorate(ItemContextFactory.ItemRetrievalContext context) throws DecorationException {
        logger.debug("Phase 3: Decorating context for user: {}", context.getUserId());
        
        // Get decoration results from specialized service
        ItemDecorationService.ItemRetrievalContext decorationContext = 
            decorationService.enrichForItemRetrieval(
                context.getUserId(),
                context.getLimit(),
                context.getSortOrder()
            );
        
        // Apply decoration results to pipeline context
        context.setUserExists(decorationContext.isUserExists());
        context.setHasPermission(decorationContext.isHasPermission());
        context.setRawItems(decorationContext.getRawItems());
        context.setEnrichedItems(decorationContext.getEnrichedItems());
        
        // Add decoration metadata for monitoring
        context.addMetadata("userExists", context.isUserExists());
        context.addMetadata("hasPermission", context.isHasPermission());
        context.addMetadata("rawItemCount", context.getRawItems() != null ? context.getRawItems().size() : 0);
        context.addMetadata("enrichedItemCount", context.getEnrichedItems() != null ? context.getEnrichedItems().size() : 0);
        
        logger.debug("Decoration complete - Found {} items for user: {}", 
                    context.getEnrichedItems() != null ? context.getEnrichedItems().size() : 0, 
                    context.getUserId());
    }
    
    @Override
    protected void validateBusiness(ItemContextFactory.ItemRetrievalContext context) throws ValidationException {
        logger.debug("Phase 4: Business validation for user: {}", context.getUserId());
        businessRulesService.validateItemRetrieval(
            context.isUserExists(),
            context.isHasPermission(),
            context.getUserId()
        );
        
        // Apply business rule filtering
        if (context.getEnrichedItems() != null) {
            List<Map<String, Object>> filteredItems = businessRulesService.filterItemsForUser(
                context.getEnrichedItems(),
                context.getUserId()
            );
            context.setEnrichedItems(filteredItems);
            context.addMetadata("filteredItemCount", filteredItems.size());
        }
    }
    
    @Override
    protected ValidationResult validateBusinessComprehensive(ItemContextFactory.ItemRetrievalContext context) {
        logger.debug("Phase 4: Comprehensive business validation for user: {}", context.getUserId());
        
        ValidationResult result = businessRulesService.validateItemRetrievalComprehensive(
            context.isUserExists(),
            context.isHasPermission(),
            context.getUserId(),
            context.getEnrichedItems()
        );
        
        // Apply business rule filtering
        if (context.getEnrichedItems() != null && result.isValid()) {
            List<Map<String, Object>> filteredItems = businessRulesService.filterItemsForUser(
                context.getEnrichedItems(),
                context.getUserId()
            );
            context.setEnrichedItems(filteredItems);
            context.addMetadata("filteredItemCount", filteredItems.size());
        }
        
        return result;
    }
    
    @Override
    protected boolean requiresPersistence() {
        return false; // This is a read operation
    }
    
    @Override
    protected Map<String, Object> buildResponse(ItemContextFactory.ItemRetrievalContext context) {
        logger.debug("Phase 6: Building response for user: {}", context.getUserId());
        
        Map<String, Object> response = responseBuilder.buildGetItemsResponse(
            context.getEnrichedItems(),
            context.getUserId(),
            context.getLimit(),
            context.getSortOrder(),
            context.getNextToken(),
            context.getRequestId()
        );
        
        // Add warnings if present
        String warnings = context.getMetadata("warnings", String.class);
        if (warnings != null && !warnings.isEmpty()) {
            response.put("warnings", warnings);
        }
        
        // Add diagnostic metadata for monitoring
        Integer rawItemCount = context.getMetadata("rawItemCount", Integer.class);
        Integer enrichedItemCount = context.getMetadata("enrichedItemCount", Integer.class);
        Integer filteredItemCount = context.getMetadata("filteredItemCount", Integer.class);
        
        response.put("metadata", Map.of(
            "rawItemCount", rawItemCount != null ? rawItemCount : 0,
            "enrichedItemCount", enrichedItemCount != null ? enrichedItemCount : 0,
            "filteredItemCount", filteredItemCount != null ? filteredItemCount : 0
        ));
        
        logger.info("Retrieved {} items for user: {}", 
                   context.getEnrichedItems() != null ? context.getEnrichedItems().size() : 0, 
                   context.getUserId());
        
        return response;
    }
}