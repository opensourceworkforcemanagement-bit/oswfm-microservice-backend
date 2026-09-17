package org.oswfm.gisservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.oswfm.gisservice.dto.UserGroupMembershipDTO;

/**
 * Feign client interface for interacting with administrationservice's user-group APIs.
 * Used to resolve which group(s) a user belongs to and which users belong to a group.
 */
@FeignClient(name = "administrationservice", path = "/api/v1/user-groups")
public interface UserGroupServiceClient {

    @GetMapping("/{groupId}/members")
    List<UserGroupMembershipDTO> getMembersByGroupId(@PathVariable Integer groupId);

    @GetMapping("/user/{userId}")
    List<UserGroupMembershipDTO> getGroupsByUserId(@PathVariable Integer userId);
}
