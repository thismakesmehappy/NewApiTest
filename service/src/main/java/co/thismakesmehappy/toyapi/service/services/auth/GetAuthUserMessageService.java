package co.thismakesmehappy.toyapi.service.services.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for GET /auth/user/{userId}/message endpoint.
 * Follows Amazon/Coral pattern - one service per endpoint.
 */
public class GetAuthUserMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(GetAuthUserMessageService.class);
    
    /**
     * Execute the GET /auth/user/{userId}/message operation.
     * 
     * @param userId The user ID from the path
     * @param authenticatedUserId The user ID from the JWT token
     * @return Response map containing personalized message
     * @throws Exception if user access is forbidden
     */
    public Map<String, Object> execute(String userId, String authenticatedUserId) throws Exception {
        // Check if user is accessing their own message
        if (!userId.equals(authenticatedUserId)) {
            logger.warn("User {} attempted to access message for user {}", authenticatedUserId, userId);
            throw new SecurityException("Forbidden - user can only access their own messages");
        }
        
        // For demo purposes, we'll create a simple personalized message
        // In a real application, this might fetch user data from a database
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello " + userId + "! This is your personalized message.");
        response.put("userId", userId);
        response.put("timestamp", Instant.now().toString());
        
        logger.info("Created personalized message for user: {}", userId);
        return response;
    }
}