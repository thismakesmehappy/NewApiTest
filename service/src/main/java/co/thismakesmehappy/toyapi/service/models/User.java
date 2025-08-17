package co.thismakesmehappy.toyapi.service.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * User model with role-based access control and team membership
 */
public class User {
    
    private String userId;           // From Cognito JWT 'sub' field
    private String username;         // From Cognito JWT 'cognito:username' field  
    private String email;            // From Cognito JWT 'email' field
    private Role role;               // User's global role
    private Set<String> teamIds;     // Teams the user belongs to
    private Instant lastLogin;
    
    public enum Role {
        @JsonProperty("user")
        USER,        // Standard user - can only access own items and team items
        
        @JsonProperty("team_admin") 
        TEAM_ADMIN,  // Can manage teams and access all team data
        
        @JsonProperty("admin")
        ADMIN        // Can access all data across all users and teams
    }
    
    // Constructors
    public User() {}
    
    public User(String userId, String username, String email) {
        this.userId = userId;
        this.username = username; 
        this.email = email;
        this.role = Role.USER; // Default to standard user
        this.lastLogin = Instant.now();
    }
    
    // Helper methods for access control
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
    
    public boolean isTeamAdmin() {
        return role == Role.TEAM_ADMIN || role == Role.ADMIN;
    }
    
    public boolean isStandardUser() {
        return role == Role.USER;
    }
    
    public boolean isMemberOfTeam(String teamId) {
        return teamIds != null && teamIds.contains(teamId);
    }
    
    public boolean canAccessItem(Item item) {
        // Admin can access everything
        if (isAdmin()) {
            return true;
        }
        
        // User owns the item
        if (userId.equals(item.getUserId())) {
            return true;
        }
        
        // Team item and user is team member
        if (item.isTeamItem() && isMemberOfTeam(item.getTeamId())) {
            return true;
        }
        
        // Public items (future feature)
        if (item.isPublicItem()) {
            return true;
        }
        
        return false;
    }
    
    public boolean canModifyItem(Item item) {
        // Admin can modify everything
        if (isAdmin()) {
            return true;
        }
        
        // User owns the item
        if (userId.equals(item.getUserId())) {
            return true;
        }
        
        // Team admin can modify team items
        if (isTeamAdmin() && item.isTeamItem() && isMemberOfTeam(item.getTeamId())) {
            return true;
        }
        
        return false;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    
    public Set<String> getTeamIds() { return teamIds; }
    public void setTeamIds(Set<String> teamIds) { this.teamIds = teamIds; }
    
    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
    
    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", teamIds=" + teamIds +
                ", lastLogin=" + lastLogin +
                '}';
    }
}