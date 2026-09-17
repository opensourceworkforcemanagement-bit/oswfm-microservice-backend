package org.oswfm.gisservice.dto;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationStayDTO {

    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private List<UserPositionHistoryDTO> points;

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    public UserPositionHistoryDTO getStartPosition() {
        return points.get(0);
    }

    public UserPositionHistoryDTO getEndPosition() {
        return points.get(points.size() - 1);
    }
}
