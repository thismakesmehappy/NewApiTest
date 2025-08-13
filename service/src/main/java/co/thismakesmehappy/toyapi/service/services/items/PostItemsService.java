package co.thismakesmehappy.toyapi.service.services.items;

import co.thismakesmehappy.toyapi.service.components.items.*;
import co.thismakesmehappy.toyapi.service.pipeline.*;
import co.thismakesmehappy.toyapi.service.utils.DynamoDbService;
import co.thismakesmehappy.toyapi.service.utils.FeatureFlagService;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Service for POST /items endpoint.
 * Follows Amazon/Coral pattern with enhanced service pipeline architecture.
 */
public class PostItemsService extends ServicePipeline<PostItemsService.CreateItemRequest, ItemContextFactory.ItemCreationContext, Map<String, Object>> {
    
    private static final Logger logger = LoggerFactory.getLogger(PostItemsService.class);
    
    // Specialized components for each concern
    private final ItemValidationService validationService;
    private final ItemDecorationService decorationService;
    private final ItemBusinessRulesService businessRulesService;
    private final ItemPersistenceService persistenceService;
    private final ItemResponseBuilder responseBuilder;
    private final FeatureFlagService featureFlags;
    
    public PostItemsService(DynamoDbService dynamoDbService, String tableName, boolean useLocalMock) {
        this(dynamoDbService, tableName, useLocalMock, null);
    }
    
    public PostItemsService(DynamoDbService dynamoDbService, String tableName, boolean useLocalMock, FeatureFlagService featureFlags) {
        // Initialize specialized services
        this.featureFlags = featureFlags;
        this.validationService = new ItemValidationService(featureFlags);
        this.decorationService = new ItemDecorationService(dynamoDbService, useLocalMock);
        this.businessRulesService = new ItemBusinessRulesService();
        this.persistenceService = new ItemPersistenceService(dynamoDbService, tableName);
        this.responseBuilder = new ItemResponseBuilder();
    }
    
    /**
     * Request model for creating items.
     */
    public static class CreateItemRequest {
        private final String message;
        private final String userId;
        
        public CreateItemRequest(String message, String userId) {
            this.message = message;
            this.userId = userId;
        }
        
        public String getMessage() { return message; }
        public String getUserId() { return userId; }
    }
    
    // Pipeline phases delegate to specialized services
    @Override
    protected void validateInput(CreateItemRequest request) throws ValidationException {
        logger.debug("Phase 1: Input validation for user: {}", request.getUserId());
        validationService.validateCreateItemRequest(request.getMessage(), request.getUserId());
    }
    
    @Override
    protected ValidationResult validateInputComprehensive(CreateItemRequest request) {
        logger.debug("Phase 1: Comprehensive input validation for user: {}", request.getUserId());
        return validationService.validateCreateItemRequestComprehensive(request.getMessage(), request.getUserId());
    }
    
    @Override
    protected ItemContextFactory.ItemCreationContext createContext(CreateItemRequest request) {
        logger.debug("Phase 2: Creating context for user: {}", request.getUserId());
        return ItemContextFactory.createItemCreationContext(request.getMessage(), request.getUserId());
    }
    
    @Override
    protected void decorate(ItemContextFactory.ItemCreationContext context) throws DecorationException {
        logger.debug("Phase 3: Decorating context for user: {}", context.getUserId());
        
        // Get decoration results from specialized service
        ItemDecorationService.ItemDecorationContext decorationContext = 
            decorationService.enrichForItemCreation(context.getUserId());
        
        // Apply decoration results to pipeline context
        context.setUserExists(decorationContext.isUserExists());
        context.setUserItemCount(decorationContext.getUserItemCount());
        context.setUserPreferences(decorationContext.getUserPreferences());
        
        // Add decoration metadata for monitoring
        context.addMetadata("userExists", context.isUserExists());
        context.addMetadata("userItemCount", context.getUserItemCount());
        context.addMetadata("hasPreferences", context.getUserPreferences() != null);
    }
    
    @Override
    protected void validateBusiness(ItemContextFactory.ItemCreationContext context) throws ValidationException {
        logger.debug("Phase 4: Business validation for user: {}", context.getUserId());
        businessRulesService.validateItemCreation(
            context.isUserExists(),
            context.getUserItemCount(),
            context.getMessage(),
            context.getUserId()
        );
    }
    
    @Override
    protected ValidationResult validateBusinessComprehensive(ItemContextFactory.ItemCreationContext context) {
        logger.debug("Phase 4: Comprehensive business validation for user: {}", context.getUserId());
        return businessRulesService.validateItemCreationComprehensive(
            context.isUserExists(),
            context.getUserItemCount(),
            context.getMessage(),
            context.getUserId(),
            context.getUserPreferences()
        );
    }
    
    @Override
    protected void persist(ItemContextFactory.ItemCreationContext context) throws PersistenceException {
        logger.debug("Phase 5: Persisting item for user: {}", context.getUserId());
        
        persistenceService.saveItem(
            context.getItemId(),
            context.getUserId(),
            context.getMessage(),
            context.getCreatedAt(),
            context.getUpdatedAt()
        );
        
        context.addMetadata("persisted", true);
    }
    
    @Override
    protected Map<String, Object> buildResponse(ItemContextFactory.ItemCreationContext context) {
        logger.debug("Phase 6: Building response for item: {}", context.getItemId());
        
        Map<String, Object> response = responseBuilder.buildCreateItemResponse(
            context.getItemId(),
            context.getMessage(),
            context.getUserId(),
            context.getCreatedAt(),
            context.getUpdatedAt(),
            context.getRequestId()
        );
        
        // Add warnings if present
        String warnings = context.getMetadata("warnings", String.class);
        if (warnings != null && !warnings.isEmpty()) {
            response.put("warnings", warnings);
        }
        
        logger.info("Item created successfully: {}", context.getItemId());
        return response;
    }
    
    @Override
    protected boolean requiresPersistence() {
        return true; // This is a write operation
    }
}