package io.netnotes.gui.nvg.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ContextPath - Hierarchical path for organizing input sources.
 * 
 * Examples:
 *   /window/main/canvas
 *   /window/dialog/buttons
 *   /global/system
 *   /overlay/commandcenter
 * 
 * Paths enable:
 * - Hierarchical input routing (handlers can listen to parent paths)
 * - Context-based filtering (disable all inputs under /window/dialog)
 * - Organized source management
 * - Input priority/bubbling
 */
public final class ContextPath {
    private final List<String> segments;
    private final String pathString;
    private final int hashCode;
    
    public static final ContextPath ROOT = new ContextPath(Collections.emptyList());
    
    /**
     * Create from path segments
     */
    private ContextPath(List<String> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.pathString = buildPathString(segments);
        this.hashCode = pathString.hashCode();
    }
    
    /**
     * Parse a path string into a ContextPath
     * Format: /segment1/segment2/segment3
     */
    public static ContextPath parse(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return ROOT;
        }
        
        // Remove leading/trailing slashes
        String normalized = path.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        if (normalized.isEmpty()) {
            return ROOT;
        }
        
        // Split and validate segments
        String[] parts = normalized.split("/");
        List<String> segments = new ArrayList<>();
        
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                validateSegment(part);
                segments.add(part);
            }
        }
        
        return new ContextPath(segments);
    }
    
    /**
     * Create from varargs segments
     */
    public static ContextPath of(String... segments) {
        if (segments == null || segments.length == 0) {
            return ROOT;
        }
        
        List<String> validSegments = new ArrayList<>();
        for (String segment : segments) {
            if (segment != null && !segment.isEmpty()) {
                validateSegment(segment);
                validSegments.add(segment);
            }
        }
        
        return new ContextPath(validSegments);
    }
    
    /**
     * Validate a path segment
     */
    private static void validateSegment(String segment) {
        if (segment.contains("/")) {
            throw new IllegalArgumentException("Segment cannot contain '/': " + segment);
        }
        if (segment.contains("\\")) {
            throw new IllegalArgumentException("Segment cannot contain '\\': " + segment);
        }
        // Allow alphanumeric, dash, underscore, dot
        if (!segment.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid segment characters: " + segment);
        }
    }
    
    /**
     * Build the string representation
     */
    private static String buildPathString(List<String> segments) {
        if (segments.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            sb.append('/').append(segment);
        }
        return sb.toString();
    }
    
    /**
     * Get the path as a string
     */
    @Override
    public String toString() {
        return pathString;
    }
    
    /**
     * Get path segments
     */
    public List<String> getSegments() {
        return segments;
    }
    
    /**
     * Get the number of segments (depth)
     */
    public int depth() {
        return segments.size();
    }
    
    /**
     * Check if this is the root path
     */
    public boolean isRoot() {
        return segments.isEmpty();
    }
    
    /**
     * Get the parent path (null if root)
     */
    public ContextPath parent() {
        if (isRoot()) {
            return null;
        }
        if (segments.size() == 1) {
            return ROOT;
        }
        return new ContextPath(segments.subList(0, segments.size() - 1));
    }
    
    /**
     * Get the last segment (name)
     */
    public String name() {
        if (isRoot()) {
            return "";
        }
        return segments.get(segments.size() - 1);
    }
    
    /**
     * Append a segment to create a child path
     */
    public ContextPath append(String segment) {
        validateSegment(segment);
        List<String> newSegments = new ArrayList<>(segments);
        newSegments.add(segment);
        return new ContextPath(newSegments);
    }
    
    /**
     * Append multiple segments
     */
    public ContextPath append(String... segmentsToAdd) {
        List<String> newSegments = new ArrayList<>(segments);
        for (String segment : segmentsToAdd) {
            validateSegment(segment);
            newSegments.add(segment);
        }
        return new ContextPath(newSegments);
    }
    
    /**
     * Append another path
     */
    public ContextPath append(ContextPath other) {
        if (other.isRoot()) {
            return this;
        }
        List<String> newSegments = new ArrayList<>(segments);
        newSegments.addAll(other.segments);
        return new ContextPath(newSegments);
    }
    
    /**
     * Check if this path starts with another path (is descendant)
     */
    public boolean startsWith(ContextPath prefix) {
        if (prefix.segments.size() > segments.size()) {
            return false;
        }
        for (int i = 0; i < prefix.segments.size(); i++) {
            if (!segments.get(i).equals(prefix.segments.get(i))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if this path starts with a string path
     */
    public boolean startsWith(String prefix) {
        return startsWith(parse(prefix));
    }
    
    /**
     * Get a subpath from start to end index
     */
    public ContextPath subpath(int start, int end) {
        if (start < 0 || end > segments.size() || start >= end) {
            throw new IllegalArgumentException("Invalid subpath range");
        }
        return new ContextPath(segments.subList(start, end));
    }
    
    /**
     * Get all ancestor paths including this one
     * Returns from most specific (this) to least specific (root)
     */
    public List<ContextPath> ancestors() {
        List<ContextPath> result = new ArrayList<>();
        ContextPath current = this;
        while (current != null) {
            result.add(current);
            current = current.parent();
        }
        return result;
    }
    
    /**
     * Get relative path from this path to another
     * Returns null if target is not under this path
     */
    public ContextPath relativize(ContextPath target) {
        if (!target.startsWith(this)) {
            return null;
        }
        if (target.equals(this)) {
            return ROOT;
        }
        return new ContextPath(target.segments.subList(segments.size(), target.segments.size()));
    }
    
    /**
     * Resolve a relative path against this path
     */
    public ContextPath resolve(String relativePath) {
        if (relativePath.startsWith("/")) {
            return parse(relativePath);
        }
        return append(parse(relativePath).segments.toArray(new String[0]));
    }
    
    /**
     * Check if this path is an ancestor of another path
     */
    public boolean isAncestorOf(ContextPath other) {
        return other.startsWith(this) && !other.equals(this);
    }
    
    /**
     * Check if this path is a descendant of another path
     */
    public boolean isDescendantOf(ContextPath other) {
        return this.startsWith(other) && !this.equals(other);
    }
    
    /**
     * Check if this path is a sibling of another path (same parent)
     */
    public boolean isSiblingOf(ContextPath other) {
        ContextPath thisParent = this.parent();
        ContextPath otherParent = other.parent();
        
        if (thisParent == null || otherParent == null) {
            return false;
        }
        
        return thisParent.equals(otherParent) && !this.equals(other);
    }
    
    /**
     * Get the common ancestor path of this and another path
     */
    public ContextPath commonAncestor(ContextPath other) {
        int minSize = Math.min(segments.size(), other.segments.size());
        int commonCount = 0;
        
        for (int i = 0; i < minSize; i++) {
            if (segments.get(i).equals(other.segments.get(i))) {
                commonCount++;
            } else {
                break;
            }
        }
        
        if (commonCount == 0) {
            return ROOT;
        }
        
        return new ContextPath(segments.subList(0, commonCount));
    }
    
    /**
     * Match against a pattern with wildcards
     * Supports: * (single segment wildcard), ** (multi-segment wildcard)
     * 
     */
    public boolean matches(String pattern) {
        return matches(parse(pattern));
    }
    
    public boolean matches(ContextPath pattern) {
        return matchesRecursive(0, pattern, 0);
    }
    
    private boolean matchesRecursive(int pathIdx, ContextPath pattern, int patternIdx) {
        // Both exhausted = match
        if (pathIdx >= segments.size() && patternIdx >= pattern.segments.size()) {
            return true;
        }
        
        // Pattern exhausted but path remains = no match
        if (patternIdx >= pattern.segments.size()) {
            return false;
        }
        
        String patternSeg = pattern.segments.get(patternIdx);
        
        // Handle **
        if ("**".equals(patternSeg)) {
            // Try matching zero or more segments
            for (int i = pathIdx; i <= segments.size(); i++) {
                if (matchesRecursive(i, pattern, patternIdx + 1)) {
                    return true;
                }
            }
            return false;
        }
        
        // Path exhausted but pattern remains = no match (unless pattern is **)
        if (pathIdx >= segments.size()) {
            return false;
        }
        
        String pathSeg = segments.get(pathIdx);
        
        // Handle * or exact match
        if ("*".equals(patternSeg) || pathSeg.equals(patternSeg)) {
            return matchesRecursive(pathIdx + 1, pattern, patternIdx + 1);
        }
        
        return false;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ContextPath)) return false;
        ContextPath other = (ContextPath) obj;
        return pathString.equals(other.pathString);
    }
    
    @Override
    public int hashCode() {
        return hashCode;
    }
    
    /**
     * Builder for constructing paths incrementally
     */
    public static class Builder {
        private final List<String> segments = new ArrayList<>();
        
        public Builder append(String segment) {
            validateSegment(segment);
            segments.add(segment);
            return this;
        }
        
        public Builder append(String... segments) {
            for (String segment : segments) {
                append(segment);
            }
            return this;
        }
        
        public ContextPath build() {
            return new ContextPath(segments);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}