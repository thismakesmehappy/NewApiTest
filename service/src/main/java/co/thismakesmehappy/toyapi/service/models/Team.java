package co.thismakesmehappy.toyapi.service.models;

import java.time.Instant;
import java.util.Set;

/**
 * Team model for organization-based sharing
 */
public class Team {
    
    private String teamId;
    private String name;
    private String description;
    private String ownerId;           // User who created/owns the team
    private Set<String> memberIds;    // User IDs of team members
    private Set<String> adminIds;     // User IDs of team admins
    private Instant createdAt;
    private Instant updatedAt;
    private boolean active;
    
    // Constructors
    public Team() {}
    
    public Team(String teamId, String name, String ownerId) {
        this.teamId = teamId;
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.active = true;
    }
    
    // Helper methods
    public boolean isMember(String userId) {
        return memberIds != null && memberIds.contains(userId);
    }
    
    public boolean isAdmin(String userId) {
        return adminIds != null && adminIds.contains(userId);
    }
    
    public boolean isOwner(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }
    
    public boolean canUserAccess(String userId) {
        return isOwner(userId) || isAdmin(userId) || isMember(userId);
    }
    
    public boolean canUserManage(String userId) {
        return isOwner(userId) || isAdmin(userId);
    }
    
    public void touch() {
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    
    public Set<String> getMemberIds() { return memberIds; }
    public void setMemberIds(Set<String> memberIds) { this.memberIds = memberIds; }
    
    public Set<String> getAdminIds() { return adminIds; }
    public void setAdminIds(Set<String> adminIds) { this.adminIds = adminIds; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    @Override
    public String toString() {
        return "Team{" +
                "teamId='" + teamId + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", memberIds=" + memberIds +
                ", adminIds=" + adminIds +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", active=" + active +
                '}';
    }
}