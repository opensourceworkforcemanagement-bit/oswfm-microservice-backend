package org.oswfm.gisservice.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.oswfm.gisservice.dto.UserPositionHistoryDTO;
import org.oswfm.gisservice.model.entity.UserPositionHistory;
import org.oswfm.gisservice.repository.User180DayHistoryPositionRepository;
import org.oswfm.gisservice.repository.User31DayHistoryPositionRepository;
import org.oswfm.gisservice.repository.User366DayHistoryPositionRepository;
import org.oswfm.gisservice.repository.User61DayHistoryPositionRepository;
import org.oswfm.gisservice.repository.User7DayHistoryPositionRepository;
import org.oswfm.gisservice.repository.User91DayHistoryPositionRepository;
import org.oswfm.gisservice.repository.UserPositionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserPositionHistoryService {

    private final User7DayHistoryPositionRepository   repo7;
    private final User31DayHistoryPositionRepository  repo31;
    private final User61DayHistoryPositionRepository  repo61;
    private final User91DayHistoryPositionRepository  repo91;
    private final User180DayHistoryPositionRepository repo180;
    private final User366DayHistoryPositionRepository repo366;

    public enum Window {
        DAYS_7(7), DAYS_31(31), DAYS_61(61), DAYS_91(91), DAYS_180(180), DAYS_366(366);

        public final int days;
        Window(int days) { this.days = days; }
    }

    @Transactional(readOnly = true)
    public List<UserPositionHistoryDTO> getByUserId(Window window, Integer userId) {
        return repo(window).findByUserIdOrderByRecordedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserPositionHistoryDTO> getByUserIdChronological(Window window, Integer userId) {
        return repo(window).findByUserIdOrderByRecordedAtAsc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserPositionHistoryDTO> getByUserIdInRange(
            Window window, Integer userId, OffsetDateTime from, OffsetDateTime to) {
        return repo(window).findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserPositionHistoryDTO> getSince(Window window, OffsetDateTime since) {
        return repo(window).findByRecordedAtAfterOrderByRecordedAtDesc(since)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public void purgeExpired() {
        for (Window w : Window.values()) {
            repo(w).deleteExpired(OffsetDateTime.now().minusDays(w.days));
        }
    }

    private UserPositionHistoryRepository<? extends UserPositionHistory> repo(Window window) {
        return switch (window) {
            case DAYS_7   -> repo7;
            case DAYS_31  -> repo31;
            case DAYS_61  -> repo61;
            case DAYS_91  -> repo91;
            case DAYS_180 -> repo180;
            case DAYS_366 -> repo366;
        };
    }

    private UserPositionHistoryDTO toDTO(UserPositionHistory e) {
        UserPositionHistoryDTO dto = new UserPositionHistoryDTO();
        dto.setHistoryId(e.getHistoryId());
        dto.setUserId(e.getUserId());
        if (e.getLocation() != null) {
            dto.setLatitude(e.getLocation().getY());
            dto.setLongitude(e.getLocation().getX());
        }
        dto.setRecordedAt(e.getRecordedAt());
        dto.setTransportMode(e.getTransportMode());
        return dto;
    }
}
