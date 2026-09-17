package org.oswfm.gisservice.dto;

import java.time.OffsetDateTime;

import org.oswfm.gisservice.model.enums.TransportMode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPositionHistoryDTO {

    private Integer historyId;
    private Integer userId;
    private Double latitude;
    private Double longitude;
    private OffsetDateTime recordedAt;
    private TransportMode transportMode;
}
