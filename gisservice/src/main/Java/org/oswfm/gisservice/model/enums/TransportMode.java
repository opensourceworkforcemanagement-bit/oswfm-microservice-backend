package org.oswfm.gisservice.model.enums;

/**
 * Coarse mode-of-transport classification reported by the client's on-device activity
 * recognition (Android Activity Recognition API / iOS Core Motion).
 */
public enum TransportMode {
    STILL,
    WALKING,
    RUNNING,
    ON_BICYCLE,
    IN_VEHICLE,
    UNKNOWN
}
