package co.thismakesmehappy.toyapi.service.pipeline;

import co.thismakesmehappy.toyapi.service.validation.ValidationResult;

/**
 * Amazon-style service pipeline for API operations with comprehensive validation support.
 * 
 * Pipeline Flow:
 * 1. Input Validation - Fast validation before expensive operations (supports comprehensive)
 * 2. Context Creation - Create initial pipeline context
 * 3. Decoration - Data enrichment, fetching related data
 * 4. Business Validation - Complex validation with enriched data (supports comprehensive)
 * 5. Persistence - Data persistence (write operations only)
 * 6. Response Building - Construct final response
 * 
 * @param <TRequest> The input request type
 * @param <TContext> The context type that flows through the pipeline
 * @param <TResponse> The output response type
 */
public abstract class ServicePipeline<TRequest, TContext extends ServiceContext, TResponse> {
    
    /**
     * Execute the complete service pipeline with enhanced validation support.
     * 
     * @param request The input request
     * @return The final response
     * @throws ServicePipelineException if any pipeline stage fails
     */
    public final TResponse execute(TRequest request) throws ServicePipelineException {
        TContext context = null;
        
        try {
            // Phase 1: Comprehensive Input Validation
            ValidationResult inputValidation = validateInputComprehensive(request);
            if (!inputValidation.isValid()) {
                throw new ValidationException(inputValidation.getAllErrorsAsString());
            }
            
            // Phase 2: Create initial context
            context = createContext(request);
            
            // Phase 3: Decoration - Enrich data, fetch related information
            decorate(context);
            
            // Phase 4: Comprehensive Business Validation
            ValidationResult businessValidation = validateBusinessComprehensive(context);
            if (!businessValidation.isValid()) {
                throw new ValidationException(businessValidation.getAllErrorsAsString());
            }
            
            // Add warnings to context for monitoring
            if (businessValidation.hasWarnings()) {
                context.addMetadata("warnings", businessValidation.getAllWarningsAsString());
            }
            
            // Phase 5: Persistence - Write operations only
            if (requiresPersistence()) {
                persist(context);
            }
            
            // Phase 6: Response Building - Construct final response
            return buildResponse(context);
            
        } catch (ServicePipelineException e) {
            // Add context information to exception if available
            if (context != null) {
                e.addContextIfMissing("requestId", context.getRequestId());
                e.addContextIfMissing("operation", context.getMetadata("operation", String.class));
            }
            throw e;
        } catch (Exception e) {
            ServicePipelineException pipelineException = new ServicePipelineException("Pipeline execution failed", e);
            if (context != null) {
                pipelineException.addContext("requestId", context.getRequestId());
            }
            throw pipelineException;
        }
    }
    
    /**
     * Phase 1: Validate input parameters.
     * Should be fast and fail early for obviously invalid input.
     * 
     * @param request The input request
     * @throws ValidationException if input is invalid
     */
    protected void validateInput(TRequest request) throws ValidationException {
        // Default implementation does nothing - override if validation needed
    }
    
    /**
     * Phase 2: Create the initial context from the request.
     * This method is required - all services must implement context creation.
     * 
     * @param request The input request
     * @return Initial context for the pipeline
     */
    protected abstract TContext createContext(TRequest request);
    
    /**
     * Phase 3: Decorate the context with additional data.
     * This is where expensive operations like database lookups,
     * external API calls, and data enrichment happen.
     * 
     * @param context The pipeline context to decorate
     * @throws DecorationException if decoration fails
     */
    protected void decorate(TContext context) throws DecorationException {
        // Default implementation does nothing - override if decoration needed
    }
    
    /**
     * Phase 4: Validate business rules with enriched data.
     * Complex validation that requires the decorated context.
     * 
     * @param context The decorated pipeline context
     * @throws ValidationException if business rules are violated
     */
    protected void validateBusiness(TContext context) throws ValidationException {
        // Default implementation does nothing - override if business validation needed
    }
    
    /**
     * Phase 5: Persist data changes (write operations only).
     * Default implementation does nothing - override for write operations.
     * 
     * @param context The validated pipeline context
     * @throws PersistenceException if persistence fails
     */
    protected void persist(TContext context) throws PersistenceException {
        // Default implementation does nothing - override for write operations that need persistence
    }
    
    /**
     * Phase 6: Build the final response.
     * This method is required - all services must implement response building.
     * 
     * @param context The processed pipeline context
     * @return The final response
     */
    protected abstract TResponse buildResponse(TContext context);
    
    /**
     * Determine if this operation requires persistence.
     * Default implementation returns false (for read operations).
     * Override to return true for write operations that need to persist data.
     * 
     * @return true if persistence is required, false otherwise
     */
    protected boolean requiresPersistence() {
        return false; // Default for read operations - override for write operations
    }
    
    /**
     * Phase 1: Comprehensive input validation that collects ALL errors.
     * Override this for batch validation, or use the simple validateInput method.
     * Default implementation calls validateInput and converts exceptions to ValidationResult.
     */
    protected ValidationResult validateInputComprehensive(TRequest request) {
        ValidationResult result = new ValidationResult();
        try {
            validateInput(request);
        } catch (ValidationException e) {
            result.addError(e.getMessage());
        }
        return result;
    }
    
    /**
     * Phase 4: Comprehensive business validation that can collect multiple errors.
     * Override this for batch validation, or use the simple validateBusiness method.
     * Default implementation calls validateBusiness and converts exceptions to ValidationResult.
     */
    protected ValidationResult validateBusinessComprehensive(TContext context) {
        ValidationResult result = new ValidationResult();
        try {
            validateBusiness(context);
        } catch (ValidationException e) {
            result.addError(e.getMessage());
        }
        return result;
    }
}