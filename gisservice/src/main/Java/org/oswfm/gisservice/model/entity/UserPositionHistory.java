package org.oswfm.gisservice.model.entity;

import java.time.OffsetDateTime;

import org.locationtech.jts.geom.Point;

import org.oswfm.gisservice.model.enums.TransportMode;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared base for all history position tables.
 * Concrete subclasses differ only in their @Table annotation.
 */
@MappedSuperclass
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class UserPositionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Integer historyId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", length = 20)
    private TransportMode transportMode;
}
