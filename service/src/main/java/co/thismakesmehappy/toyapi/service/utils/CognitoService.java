package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

/**
 * Service interface for Cognito Identity Provider operations.
 * Provides dependency injection and testability for AWS Cognito operations.
 */
public interface CognitoService {
    
    /**
     * Initiates authentication for a user.
     */
    InitiateAuthResponse initiateAuth(InitiateAuthRequest request);
    
    /**
     * Responds to an authentication challenge.
     */
    RespondToAuthChallengeResponse respondToAuthChallenge(RespondToAuthChallengeRequest request);
    
    /**
     * Gets user details from an access token.
     */
    GetUserResponse getUser(GetUserRequest request);
    
    /**
     * Lists users in the user pool.
     */
    ListUsersResponse listUsers(ListUsersRequest request);
    
    /**
     * Creates a new user in the user pool.
     */
    AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest request);
    
    /**
     * Sets a permanent password for a user.
     */
    AdminSetUserPasswordResponse adminSetUserPassword(AdminSetUserPasswordRequest request);
    
    /**
     * Initiates authentication for a user using admin privileges.
     */
    AdminInitiateAuthResponse adminInitiateAuth(AdminInitiateAuthRequest request);
}