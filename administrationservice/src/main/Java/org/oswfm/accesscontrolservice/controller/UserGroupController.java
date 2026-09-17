package org.oswfm.accesscontrolservice.controller;

import org.oswfm.accesscontrolservice.dto.UserGroupDTO;
import org.oswfm.accesscontrolservice.dto.UserGroupMembershipDTO;
import org.oswfm.accesscontrolservice.dto.UserGroupTypeDTO;
import org.oswfm.accesscontrolservice.service.UserGroupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-groups")
@RequiredArgsConstructor
@Tag(name = "User Groups", description = "User group and membership management APIs")
public class UserGroupController {

    private final UserGroupService userGroupService;

    // ========== Group CRUD ==========

    @GetMapping
    @Operation(summary = "Get all user groups")
    public ResponseEntity<List<UserGroupDTO>> getAllGroups() {
        return ResponseEntity.ok(userGroupService.getAllGroups());
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get a user group by ID")
    public ResponseEntity<UserGroupDTO> getGroupById(@PathVariable Integer groupId) {
        return ResponseEntity.ok(userGroupService.getGroupById(groupId));
    }

    @PostMapping
    @Operation(summary = "Create a new user group")
    public ResponseEntity<UserGroupDTO> createGroup(@Valid @RequestBody UserGroupDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userGroupService.createGroup(dto));
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Update an existing user group")
    public ResponseEntity<UserGroupDTO> updateGroup(@PathVariable Integer groupId, @Valid @RequestBody UserGroupDTO dto) {
        return ResponseEntity.ok(userGroupService.updateGroup(groupId, dto));
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete a user group")
    public ResponseEntity<Void> deleteGroup(@PathVariable Integer groupId) {
        userGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    // ========== Membership Management ==========

    @GetMapping("/{groupId}/members")
    @Operation(summary = "Get all members of a group")
    public ResponseEntity<List<UserGroupMembershipDTO>> getMembersByGroupId(@PathVariable Integer groupId) {
        return ResponseEntity.ok(userGroupService.getMembersByGroupId(groupId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all groups a user belongs to")
    public ResponseEntity<List<UserGroupMembershipDTO>> getGroupsByUserId(@PathVariable Integer userId) {
        return ResponseEntity.ok(userGroupService.getGroupsByUserId(userId));
    }

    @PostMapping("/members")
    @Operation(summary = "Add a user to a group")
    public ResponseEntity<UserGroupMembershipDTO> addUserToGroup(@Valid @RequestBody UserGroupMembershipDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userGroupService.addUserToGroup(dto));
    }

    @DeleteMapping("/members/{membershipId}")
    @Operation(summary = "Remove a user from a group")
    public ResponseEntity<Void> removeUserFromGroup(@PathVariable Integer membershipId) {
        userGroupService.removeUserFromGroup(membershipId);
        return ResponseEntity.noContent().build();
    }

    // ========== Group Type Lookup ==========

    @GetMapping("/types")
    @Operation(summary = "Get all user group types")
    public ResponseEntity<List<UserGroupTypeDTO>> getAllGroupTypes() {
        return ResponseEntity.ok(userGroupService.getAllGroupTypes());
    }
}
