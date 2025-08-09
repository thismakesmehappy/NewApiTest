package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Mock implementation of CognitoService for unit testing.
 * Provides in-memory authentication without AWS dependencies.
 */
public class MockCognitoService implements CognitoService {
    
    private boolean throwException = false;
    private String exceptionMessage = "Mock Cognito exception";
    
    // Mock user data for testing
    private final Map<String, String> mockUsers = new HashMap<>();
    
    public MockCognitoService() {
        // Add default test user
        mockUsers.put("testuser", "testpassword");
    }
    
    /**
     * Configures the mock to throw exceptions for testing error conditions.
     */
    public void setThrowException(boolean throwException, String message) {
        this.throwException = throwException;
        this.exceptionMessage = message;
    }
    
    /**
     * Adds a user to the mock service for testing.
     */
    public void addMockUser(String username, String password) {
        mockUsers.put(username, password);
    }
    
    /**
     * Clears all mock users.
     */
    public void clearMockUsers() {
        mockUsers.clear();
    }
    
    @Override
    public InitiateAuthResponse initiateAuth(InitiateAuthRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        Map<String, String> authParams = request.authParameters();
        String username = authParams.get("USERNAME");
        String password = authParams.get("PASSWORD");
        
        if (username != null && password != null && 
            mockUsers.containsKey(username) && mockUsers.get(username).equals(password)) {
            
            // Return successful authentication response
            return InitiateAuthResponse.builder()
                    .challengeName(ChallengeNameType.UNKNOWN_TO_SDK_VERSION) // No challenge needed
                    .authenticationResult(AuthenticationResultType.builder()
                            .accessToken("mock-access-token-" + username)
                            .idToken("mock-id-token-" + username)
                            .refreshToken("mock-refresh-token-" + username)
                            .expiresIn(3600)
                            .build())
                    .build();
        } else {
            throw NotAuthorizedException.builder()
                    .message("Invalid username or password")
                    .build();
        }
    }
    
    @Override
    public RespondToAuthChallengeResponse respondToAuthChallenge(RespondToAuthChallengeRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        // Mock challenge response
        return RespondToAuthChallengeResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("mock-challenge-access-token")
                        .idToken("mock-challenge-id-token")
                        .refreshToken("mock-challenge-refresh-token")
                        .expiresIn(3600)
                        .build())
                .build();
    }
    
    @Override
    public GetUserResponse getUser(GetUserRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        String accessToken = request.accessToken();
        if (accessToken != null && accessToken.startsWith("mock-access-token-")) {
            String username = accessToken.replace("mock-access-token-", "");
            
            return GetUserResponse.builder()
                    .username(username)
                    .userAttributes(
                            AttributeType.builder().name("email").value(username + "@test.com").build(),
                            AttributeType.builder().name("email_verified").value("true").build()
                    )
                    .build();
        } else {
            throw NotAuthorizedException.builder()
                    .message("Invalid access token")
                    .build();
        }
    }
    
    @Override
    public ListUsersResponse listUsers(ListUsersRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        return ListUsersResponse.builder()
                .users(List.of(
                        UserType.builder()
                                .username("testuser")
                                .userStatus(UserStatusType.CONFIRMED)
                                .userCreateDate(Instant.now())
                                .build()
                ))
                .build();
    }
    
    @Override
    public AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        return AdminCreateUserResponse.builder()
                .user(UserType.builder()
                        .username(request.username())
                        .userStatus(UserStatusType.FORCE_CHANGE_PASSWORD)
                        .userCreateDate(Instant.now())
                        .build())
                .build();
    }
    
    @Override
    public AdminSetUserPasswordResponse adminSetUserPassword(AdminSetUserPasswordRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        // Add user to mock database
        mockUsers.put(request.username(), request.password());
        
        return AdminSetUserPasswordResponse.builder().build();
    }
    
    @Override
    public AdminInitiateAuthResponse adminInitiateAuth(AdminInitiateAuthRequest request) {
        if (throwException) {
            throw CognitoIdentityProviderException.builder().message(exceptionMessage).build();
        }
        
        Map<String, String> authParams = request.authParameters();
        String username = authParams.get("USERNAME");
        String password = authParams.get("PASSWORD");
        
        if (username != null && password != null && 
            mockUsers.containsKey(username) && mockUsers.get(username).equals(password)) {
            
            // Return successful authentication response
            return AdminInitiateAuthResponse.builder()
                    .challengeName(ChallengeNameType.UNKNOWN_TO_SDK_VERSION) // No challenge needed
                    .authenticationResult(AuthenticationResultType.builder()
                            .accessToken("mock-access-token-" + username)
                            .idToken("mock-id-token-" + username)
                            .refreshToken("mock-refresh-token-" + username)
                            .expiresIn(3600)
                            .build())
                    .build();
        } else {
            throw NotAuthorizedException.builder()
                    .message("Invalid username or password")
                    .build();
        }
    }
}