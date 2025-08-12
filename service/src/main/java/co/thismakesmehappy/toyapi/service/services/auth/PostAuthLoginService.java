package co.thismakesmehappy.toyapi.service.services.auth;

import co.thismakesmehappy.toyapi.service.utils.CognitoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for POST /auth/login endpoint.
 * Follows Amazon/Coral pattern - one service per endpoint.
 */
public class PostAuthLoginService {
    
    private static final Logger logger = LoggerFactory.getLogger(PostAuthLoginService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final CognitoService cognitoService;
    private final String userPoolClientId;
    private final boolean mockAuthentication;
    
    public PostAuthLoginService(CognitoService cognitoService, String userPoolClientId, boolean mockAuthentication) {
        this.cognitoService = cognitoService;
        this.userPoolClientId = userPoolClientId;
        this.mockAuthentication = mockAuthentication;
    }
    
    /**
     * Login request data model.
     */
    public static class LoginRequest {
        private String username;
        private String password;
        
        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
    
    /**
     * Execute the POST /auth/login operation.
     * 
     * @param request The login request
     * @return Response map containing authentication result
     * @throws Exception if authentication fails
     */
    public Map<String, Object> execute(LoginRequest request) throws Exception {
        logger.info("Authenticating user: {}", request.getUsername());
        
        if (mockAuthentication) {
            return createMockAuthResponse(request.getUsername());
        }
        
        try {
            // Attempt to authenticate with Cognito
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(userPoolClientId)
                .authParameters(Map.of(
                    "USERNAME", request.getUsername(),
                    "PASSWORD", request.getPassword()
                ))
                .build();
            
            InitiateAuthResponse authResponse = cognitoService.initiateAuth(authRequest);
            
            if (authResponse.authenticationResult() != null) {
                // Successful authentication
                AuthenticationResultType authResult = authResponse.authenticationResult();
                String idToken = authResult.idToken();
                String accessToken = authResult.accessToken();
                
                // Extract user ID from ID token
                String userId = extractUserIdFromIdToken(idToken);
                
                Map<String, Object> response = new HashMap<>();
                response.put("token", idToken); // Use ID token for API calls
                response.put("accessToken", accessToken);
                response.put("userId", userId);
                response.put("expiresIn", authResult.expiresIn());
                
                logger.info("Authentication successful for user: {}", userId);
                return response;
            } else {
                logger.warn("Authentication challenge required for user: {}", request.getUsername());
                throw new Exception("Authentication challenge not supported");
            }
            
        } catch (NotAuthorizedException e) {
            logger.warn("Authentication failed for user: {} - {}", request.getUsername(), e.getMessage());
            throw new Exception("Invalid credentials");
        } catch (Exception e) {
            logger.error("Authentication error for user: {}", request.getUsername(), e);
            throw new Exception("Authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Create a mock authentication response for testing.
     */
    private Map<String, Object> createMockAuthResponse(String username) {
        logger.info("Creating mock auth response for user: {}", username);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", "mock-id-token-" + username);
        response.put("accessToken", "mock-access-token-" + username);
        response.put("userId", "mock-user-" + username);
        response.put("expiresIn", 3600);
        
        return response;
    }
    
    /**
     * Extract user ID from Cognito ID token.
     * This is a simplified version - in production, you'd want proper JWT validation.
     */
    private String extractUserIdFromIdToken(String idToken) throws Exception {
        try {
            // Split the JWT token
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new Exception("Invalid JWT token format");
            }
            
            // Decode the payload (second part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payloadNode = objectMapper.readTree(payload);
            
            // Extract the user ID (sub claim)
            JsonNode subNode = payloadNode.get("sub");
            if (subNode != null) {
                return subNode.asText();
            }
            
            throw new Exception("User ID not found in token");
        } catch (Exception e) {
            logger.error("Failed to extract user ID from ID token", e);
            throw new Exception("Invalid token format");
        }
    }
}