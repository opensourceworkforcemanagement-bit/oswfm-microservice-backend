package org.oswfm.gisservice.buffer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.oswfm.gisservice.config.GroupLocationBufferProperties;
import org.oswfm.gisservice.dto.UserCurrentPositionDTO;
import org.oswfm.gisservice.kafka.UserGroupLocationsKafkaService;
import org.oswfm.gisservice.service.GroupMembershipService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Buffers each group's latest member positions until every member has reported at least once
 * in the current round, then publishes the complete set to {@link UserGroupLocationsKafkaService}.
 *
 * <p>Behavior is controlled by {@link GroupLocationBufferProperties}:
 * <ul>
 *   <li>{@code flushOnTimeout} — flush a group's partial buffer once it has been open longer
 *       than {@code flushTimeoutMs}, instead of waiting indefinitely for every member.</li>
 *   <li>{@code resetAfterPublish} — clear the group's buffer after a publish (fresh round
 *       required next time) vs. retain last-known positions and republish the merged set on
 *       every subsequent update.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupLocationBufferService {

    private final GroupMembershipService groupMembershipService;
    private final UserGroupLocationsKafkaService kafkaService;
    private final GroupLocationBufferProperties properties;

    /** groupId -> buffer state */
    private final Map<Integer, GroupBuffer> buffers = new ConcurrentHashMap<>();

    /**
     * Records a user's latest position into the buffer of every group they belong to,
     * publishing any group whose buffer becomes complete as a result.
     */
    public void record(UserCurrentPositionDTO position) {
        Set<Integer> groupIds = groupMembershipService.getGroupIdsForUser(position.getUserId());
        for (Integer groupId : groupIds) {
            recordForGroup(groupId, position);
        }
    }

    private void recordForGroup(Integer groupId, UserCurrentPositionDTO position) {
        Set<Integer> memberIds = groupMembershipService.getMemberIdsForGroup(groupId);
        if (memberIds.isEmpty()) {
            return;
        }

        GroupBuffer buffer = buffers.computeIfAbsent(groupId, id -> new GroupBuffer());
        boolean complete;
        synchronized (buffer) {
            buffer.positions.put(position.getUserId(), position);
            if (buffer.openedAtMs == 0) {
                buffer.openedAtMs = System.currentTimeMillis();
            }
            complete = buffer.positions.keySet().containsAll(memberIds);
        }

        if (complete) {
            publishAndAdvance(groupId, buffer, memberIds);
        }
    }

    /** Periodically flushes groups whose buffer has been open too long without completing. */
    @Scheduled(fixedDelayString = "#{@gisGroupLocationBufferProperties.sweepIntervalMs}")
    public void sweepStaleBuffers() {
        if (!properties.isFlushOnTimeout()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, GroupBuffer> entry : buffers.entrySet()) {
            Integer groupId = entry.getKey();
            GroupBuffer buffer = entry.getValue();

            boolean shouldFlush;
            synchronized (buffer) {
                shouldFlush = buffer.openedAtMs != 0
                        && !buffer.positions.isEmpty()
                        && (now - buffer.openedAtMs) >= properties.getFlushTimeoutMs();
            }

            if (shouldFlush) {
                Set<Integer> memberIds = groupMembershipService.getMemberIdsForGroup(groupId);
                log.info("[GroupLocationBuffer] Timeout flush for groupId={} ({}/{} member(s) reported)",
                        groupId, buffer.positions.size(), memberIds.size());
                publishAndAdvance(groupId, buffer, memberIds);
            }
        }
    }

    private void publishAndAdvance(Integer groupId, GroupBuffer buffer, Set<Integer> memberIds) {
        List<UserCurrentPositionDTO> snapshot;
        List<String> targetUserIds;
        synchronized (buffer) {
            if (buffer.positions.isEmpty()) {
                return;
            }
            snapshot = List.copyOf(buffer.positions.values());
            targetUserIds = memberIds.stream().map(String::valueOf).toList();

            if (properties.isResetAfterPublish()) {
                buffer.positions.clear();
                buffer.openedAtMs = 0;
            } else {
                // Retain last-known positions; start a new timing window for the next flush.
                buffer.openedAtMs = System.currentTimeMillis();
            }
        }

        kafkaService.publish(groupId, snapshot, targetUserIds);
    }

    private static final class GroupBuffer {
        final Map<Integer, UserCurrentPositionDTO> positions = new ConcurrentHashMap<>();
        volatile long openedAtMs = 0;
    }
}
