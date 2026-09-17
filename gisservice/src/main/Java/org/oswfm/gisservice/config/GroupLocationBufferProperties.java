package org.oswfm.gisservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration for how {@link org.oswfm.gisservice.buffer.GroupLocationBufferService}
 * buffers and publishes per-group location updates.
 */
@Data
@Configuration("gisGroupLocationBufferProperties")
@ConfigurationProperties(prefix = "oswfm.group-location-buffer")
public class GroupLocationBufferProperties {

    /**
     * Whether a group whose members haven't all reported a position yet should still be
     * flushed (with partial data) once {@link #flushTimeoutMs} has elapsed since the group's
     * buffer was first populated in the current round.
     */
    private boolean flushOnTimeout = true;

    /** How often the timeout sweep runs, and the max age of a group's buffer before it flushes. */
    private long flushTimeoutMs = 30_000;

    /**
     * Whether a group's buffer is cleared after a successful publish (requiring every member
     * to report again before the next publish) or retained as last-known state, re-publishing
     * the merged set whenever any member reports a new position.
     */
    private boolean resetAfterPublish = true;

    /** How often the timeout sweep is checked, in milliseconds. */
    private long sweepIntervalMs = 5_000;

    /** Membership/group cache TTL in milliseconds. */
    private long membershipCacheTtlMs = 30_000;
}
