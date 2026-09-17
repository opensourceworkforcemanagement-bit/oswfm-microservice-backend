package org.oswfm.gisservice.dto;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripDTO {

    private OffsetDateTime startTime;
    private List<UserPositionHistoryDTO> points;

    public OffsetDateTime getEndTime() {
        if (points == null || points.isEmpty()) {
            return null;
        }
        return points.get(points.size() - 1).getRecordedAt();
    }
}
