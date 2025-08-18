package co.thismakesmehappy.toyapi.service.services.auth;

import co.thismakesmehappy.toyapi.service.models.Item;
import co.thismakesmehappy.toyapi.service.models.User;
import co.thismakesmehappy.toyapi.service.models.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.HashSet;

/**
 * Service for handling user authorization and access control
 */
public class AuthorizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationService.class);
    
    // For this implementation, we'll use simple in-memory user data
    // In production, this would come from a user database/directory service
    
    /**
     * Creates a User from JWT token information
     */
    public User createUserFromJWT(String userId, String username, String email) {
        User user = new User(userId, username, email);
        
        // Set role based on predefined rules (in production, this would come from database)
        user.setRole(determineUserRole(username, email));
        
        // Set team memberships (in production, this would come from database)
        user.setTeamIds(getUserTeamMemberships(userId));
        
        logger.debug("Created user from JWT: userId={}, username={}, role={}, teams={}", 
                    userId, username, user.getRole(), user.getTeamIds());
        
        return user;
    }
    
    /**
     * Determines user role based on username/email patterns
     * In production, this would query a user database
     */
    private User.Role determineUserRole(String username, String email) {
        // Demo logic - in production this would be database-driven
        if (username != null && username.startsWith("admin")) {
            return User.Role.ADMIN;
        }
        if (username != null && username.contains("teamlead")) {
            return User.Role.TEAM_ADMIN;
        }
        if (email != null && email.endsWith("@admin.toyapi.com")) {
            return User.Role.ADMIN;
        }
        
        // Team sharing test users
        if (username != null) {
            switch (username) {
                case "globaladmin":
                    return User.Role.ADMIN;
                case "engteamadmin":
                case "marketingteamadmin":
                    return User.Role.TEAM_ADMIN;
                case "standarduser":
                    return User.Role.USER;
            }
        }
        
        return User.Role.USER;
    }
    
    /**
     * Gets user's team memberships
     * In production, this would query a team membership database
     */
    private Set<String> getUserTeamMemberships(String userId) {
        Set<String> teams = new HashSet<>();
        
        // Demo logic - in production this would be database-driven
        // For now, assign some default teams based on user patterns
        if (userId.contains("team")) {
            teams.add("team-engineering");
        }
        if (userId.contains("demo")) {
            teams.add("team-demo");
            teams.add("team-qa");
        }
        
        // Team sharing test users - determine membership by userId patterns
        if (userId != null) {
            // Standard user - member of team-engineering
            if (userId.contains("standarduser")) {
                teams.add("team-engineering");
            }
            
            // Engineering team admin - admin of team-engineering
            if (userId.contains("engteamadmin")) {
                teams.add("team-engineering");
            }
            
            // Marketing team admin - admin of team-marketing
            if (userId.contains("marketingteamadmin")) {
                teams.add("team-marketing");
            }
            
            // Global admin - access to all teams (handled by role, but adding for clarity)
            if (userId.contains("globaladmin")) {
                teams.add("team-engineering");
                teams.add("team-marketing");
                teams.add("team-qa");
            }
        }
        
        return teams;
    }
    
    /**
     * Checks if user can read/access an item
     */
    public boolean canUserAccessItem(User user, Item item) {
        if (user == null || item == null) {
            return false;
        }
        
        boolean canAccess = user.canAccessItem(item);
        
        logger.debug("Access check: userId={}, itemId={}, canAccess={}, reason={}", 
                    user.getUserId(), item.getId(), canAccess, 
                    getAccessReason(user, item));
        
        return canAccess;
    }
    
    /**
     * Checks if user can modify (update/delete) an item
     */
    public boolean canUserModifyItem(User user, Item item) {
        if (user == null || item == null) {
            return false;
        }
        
        boolean canModify = user.canModifyItem(item);
        
        logger.debug("Modify check: userId={}, itemId={}, canModify={}, reason={}", 
                    user.getUserId(), item.getId(), canModify, 
                    getModifyReason(user, item));
        
        return canModify;
    }
    
    /**
     * Gets human-readable reason for access decision (for logging/debugging)
     */
    private String getAccessReason(User user, Item item) {
        if (user.isAdmin()) {
            return "admin_access";
        }
        if (user.getUserId().equals(item.getUserId())) {
            return "owner_access";
        }
        if (item.isTeamItem() && user.isMemberOfTeam(item.getTeamId())) {
            return "team_member_access";
        }
        if (item.isPublicItem()) {
            return "public_access";
        }
        return "access_denied";
    }
    
    /**
     * Gets human-readable reason for modify decision (for logging/debugging)
     */
    private String getModifyReason(User user, Item item) {
        if (user.isAdmin()) {
            return "admin_modify";
        }
        if (user.getUserId().equals(item.getUserId())) {
            return "owner_modify";
        }
        if (user.isTeamAdmin() && item.isTeamItem() && user.isMemberOfTeam(item.getTeamId())) {
            return "team_admin_modify";
        }
        return "modify_denied";
    }
    
    /**
     * Validates if a team exists and user has access to it
     * In production, this would query a team database
     */
    public boolean canUserAccessTeam(User user, String teamId) {
        if (user == null || teamId == null) {
            return false;
        }
        
        // Admin can access any team
        if (user.isAdmin()) {
            return true;
        }
        
        // User must be a member of the team
        return user.isMemberOfTeam(teamId);
    }
    
    /**
     * Validates team assignment for new items
     */
    public boolean isValidTeamAssignment(User user, String teamId) {
        if (teamId == null) {
            return true; // Individual items are always valid
        }
        
        return canUserAccessTeam(user, teamId);
    }
}