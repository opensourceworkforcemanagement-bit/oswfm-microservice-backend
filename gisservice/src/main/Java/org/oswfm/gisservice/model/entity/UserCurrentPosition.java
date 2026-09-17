package org.oswfm.gisservice.model.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import org.oswfm.gisservice.model.enums.TransportMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_current_position")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCurrentPosition {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point location;

    @UpdateTimestamp
    @Column(name = "last_update", nullable = false)
    private OffsetDateTime lastUpdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", length = 20)
    private TransportMode transportMode;
}
