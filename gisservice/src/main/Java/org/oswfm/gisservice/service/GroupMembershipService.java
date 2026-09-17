package org.oswfm.gisservice.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import org.oswfm.gisservice.client.UserGroupServiceClient;
import org.oswfm.gisservice.config.GroupLocationBufferProperties;
import org.oswfm.gisservice.dto.UserGroupMembershipDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves user/group membership via {@link UserGroupServiceClient}, backed by a short-TTL
 * cache so every position update doesn't trigger a call to administrationservice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMembershipService {

    private final UserGroupServiceClient userGroupServiceClient;
    private final GroupLocationBufferProperties properties;

    private final Map<Integer, CacheEntry<Set<Integer>>> groupIdsByUser = new ConcurrentHashMap<>();
    private final Map<Integer, CacheEntry<Set<Integer>>> memberIdsByGroup = new ConcurrentHashMap<>();

    /** Returns the set of group IDs the given user belongs to. */
    public Set<Integer> getGroupIdsForUser(Integer userId) {
        CacheEntry<Set<Integer>> cached = groupIdsByUser.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        List<UserGroupMembershipDTO> memberships = userGroupServiceClient.getGroupsByUserId(userId);
        Set<Integer> groupIds = memberships.stream()
                .map(UserGroupMembershipDTO::getGroupId)
                .collect(Collectors.toUnmodifiableSet());

        groupIdsByUser.put(userId, new CacheEntry<>(groupIds, System.currentTimeMillis() + properties.getMembershipCacheTtlMs()));
        return groupIds;
    }

    /** Returns the set of user IDs that belong to the given group. */
    public Set<Integer> getMemberIdsForGroup(Integer groupId) {
        CacheEntry<Set<Integer>> cached = memberIdsByGroup.get(groupId);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        List<UserGroupMembershipDTO> members = userGroupServiceClient.getMembersByGroupId(groupId);
        Set<Integer> memberIds = members.stream()
                .map(UserGroupMembershipDTO::getUserId)
                .collect(Collectors.toUnmodifiableSet());

        memberIdsByGroup.put(groupId, new CacheEntry<>(memberIds, System.currentTimeMillis() + properties.getMembershipCacheTtlMs()));
        return memberIds;
    }

    private record CacheEntry<T>(T value, long expiresAtMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }
}
