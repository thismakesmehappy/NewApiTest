package co.thismakesmehappy.toyapi.service.versioning;

/**
 * Represents an API version with version number and metadata.
 */
public class ApiVersion implements Comparable<ApiVersion> {
    
    private final int major;
    private final int minor;
    private final int patch;
    private final String version;
    
    public ApiVersion(int major) {
        this(major, 0, 0);
    }
    
    public ApiVersion(int major, int minor) {
        this(major, minor, 0);
    }
    
    public ApiVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.version = major + "." + minor + "." + patch;
    }
    
    public static ApiVersion parse(String versionString) {
        if (versionString == null || versionString.trim().isEmpty()) {
            return getDefault();
        }
        
        // Remove 'v' prefix if present
        if (versionString.toLowerCase().startsWith("v")) {
            versionString = versionString.substring(1);
        }
        
        String[] parts = versionString.split("\\.");
        
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            
            return new ApiVersion(major, minor, patch);
        } catch (NumberFormatException e) {
            return getDefault();
        }
    }
    
    public static ApiVersion getDefault() {
        return new ApiVersion(1, 0, 0);  // Default to v1.0.0
    }
    
    public static ApiVersion getLatest() {
        return new ApiVersion(1, 0, 0);  // Currently latest is v1.0.0
    }
    
    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }
    public String getVersion() { return version; }
    
    public String getVersionString() {
        return "v" + version;
    }
    
    public String getMajorVersionString() {
        return "v" + major;
    }
    
    public boolean isCompatibleWith(ApiVersion other) {
        // Same major version = compatible (assuming semantic versioning)
        return this.major == other.major;
    }
    
    public boolean isNewerThan(ApiVersion other) {
        return this.compareTo(other) > 0;
    }
    
    public boolean isOlderThan(ApiVersion other) {
        return this.compareTo(other) < 0;
    }
    
    @Override
    public int compareTo(ApiVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ApiVersion that = (ApiVersion) obj;
        return major == that.major && minor == that.minor && patch == that.patch;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(major, minor, patch);
    }
    
    @Override
    public String toString() {
        return getVersionString();
    }
}