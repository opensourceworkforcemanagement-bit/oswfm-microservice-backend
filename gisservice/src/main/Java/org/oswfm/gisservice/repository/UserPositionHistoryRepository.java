package org.oswfm.gisservice.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.oswfm.gisservice.model.entity.UserPositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * Generic base repository shared by all history position tables.
 * Spring Data generates one concrete proxy per declared subinterface.
 */
@NoRepositoryBean
public interface UserPositionHistoryRepository<T extends UserPositionHistory>
        extends JpaRepository<T, Integer> {

    List<T> findByUserId(Integer userId);

    List<T> findByUserIdOrderByRecordedAtDesc(Integer userId);

    List<T> findByUserIdOrderByRecordedAtAsc(Integer userId);

    List<T> findByRecordedAtAfterOrderByRecordedAtDesc(OffsetDateTime since);

    List<T> findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Integer userId, OffsetDateTime from, OffsetDateTime to);

    @Modifying
    @Query("DELETE FROM #{#entityName} e WHERE e.recordedAt < :cutoff")
    void deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
