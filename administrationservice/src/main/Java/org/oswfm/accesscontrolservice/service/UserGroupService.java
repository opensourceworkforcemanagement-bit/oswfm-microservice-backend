package org.oswfm.accesscontrolservice.service;

import java.util.List;
import java.util.stream.Collectors;

import org.oswfm.ResourceNotFoundException;
import org.oswfm.accesscontrolservice.dto.UserGroupDTO;
import org.oswfm.accesscontrolservice.dto.UserGroupMembershipDTO;
import org.oswfm.accesscontrolservice.dto.UserGroupTypeDTO;
import org.oswfm.accesscontrolservice.model.entity.UserGroup;
import org.oswfm.accesscontrolservice.model.entity.UserGroupMembership;
import org.oswfm.accesscontrolservice.model.entity.UserGroupType;
import org.oswfm.accesscontrolservice.repository.ACUserRepository;
import org.oswfm.accesscontrolservice.repository.UserGroupMembershipRepository;
import org.oswfm.accesscontrolservice.repository.UserGroupRepository;
import org.oswfm.accesscontrolservice.repository.UserGroupTypeRepository;
import org.oswfm.commons.model.user.entity.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final UserGroupMembershipRepository userGroupMembershipRepository;
    private final UserGroupTypeRepository userGroupTypeRepository;
    private final ACUserRepository userRepository;

    // ========== Group CRUD ==========

    @Transactional(readOnly = true)
    public List<UserGroupDTO> getAllGroups() {
        return userGroupRepository.findAll().stream()
                .map(this::convertToGroupDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserGroupDTO getGroupById(Integer groupId) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", groupId));
        return convertToGroupDTO(group);
    }

    @Transactional
    public UserGroupDTO createGroup(UserGroupDTO dto) {
        UserGroup group = new UserGroup();
        group.setGroupName(dto.getGroupName());
        group.setDescription(dto.getDescription());
        group.setGroupType(resolveGroupType(dto.getUserGroupTypeId()));

        if (dto.getParentGroupId() != null) {
            UserGroup parentGroup = userGroupRepository.findById(dto.getParentGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", dto.getParentGroupId()));
            group.setParentGroup(parentGroup);
        }

        UserGroup saved = userGroupRepository.save(group);
        return convertToGroupDTO(saved);
    }

    @Transactional
    public UserGroupDTO updateGroup(Integer groupId, UserGroupDTO dto) {
        UserGroup existing = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", groupId));

        existing.setGroupName(dto.getGroupName());
        existing.setDescription(dto.getDescription());
        existing.setGroupType(resolveGroupType(dto.getUserGroupTypeId()));

        if (dto.getParentGroupId() != null) {
            UserGroup parentGroup = userGroupRepository.findById(dto.getParentGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", dto.getParentGroupId()));
            existing.setParentGroup(parentGroup);
        } else {
            existing.setParentGroup(null);
        }

        UserGroup saved = userGroupRepository.save(existing);
        return convertToGroupDTO(saved);
    }

    private UserGroupType resolveGroupType(Integer userGroupTypeId) {
        return userGroupTypeRepository.findById(userGroupTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroupType", "id", userGroupTypeId));
    }

    // ========== Group Type Lookup ==========

    @Transactional(readOnly = true)
    public List<UserGroupTypeDTO> getAllGroupTypes() {
        return userGroupTypeRepository.findAll().stream()
                .map(this::convertToGroupTypeDTO)
                .collect(Collectors.toList());
    }

    private UserGroupTypeDTO convertToGroupTypeDTO(UserGroupType type) {
        UserGroupTypeDTO dto = new UserGroupTypeDTO();
        dto.setUserGroupTypeId(type.getUserGroupTypeId());
        dto.setTypeName(type.getTypeName());
        dto.setDescription(type.getDescription());
        dto.setCreatedAt(type.getCreatedAt());
        return dto;
    }

    @Transactional
    public void deleteGroup(Integer groupId) {
        if (!userGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("UserGroup", "id", groupId);
        }
        userGroupRepository.deleteById(groupId);
    }

    // ========== Membership Management ==========

    @Transactional(readOnly = true)
    public List<UserGroupMembershipDTO> getMembersByGroupId(Integer groupId) {
        return userGroupMembershipRepository.findByGroup_GroupId(groupId).stream()
                .map(this::convertToMembershipDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserGroupMembershipDTO> getGroupsByUserId(Integer userId) {
        return userGroupMembershipRepository.findByUser_UserId(userId).stream()
                .map(this::convertToMembershipDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserGroupMembershipDTO addUserToGroup(UserGroupMembershipDTO dto) {
        UserEntity user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dto.getUserId()));
        UserGroup group = userGroupRepository.findById(dto.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", dto.getGroupId()));

        UserGroupMembership membership = new UserGroupMembership();
        membership.setUser(user);
        membership.setGroup(group);

        UserGroupMembership saved = userGroupMembershipRepository.save(membership);
        return convertToMembershipDTO(saved);
    }

    @Transactional
    public void removeUserFromGroup(Integer membershipId) {
        if (!userGroupMembershipRepository.existsById(membershipId)) {
            throw new ResourceNotFoundException("UserGroupMembership", "id", membershipId);
        }
        userGroupMembershipRepository.deleteById(membershipId);
    }

    // ========== DTO Converters ==========

    private UserGroupDTO convertToGroupDTO(UserGroup group) {
        UserGroupDTO dto = new UserGroupDTO();
        dto.setGroupId(group.getGroupId());
        dto.setGroupName(group.getGroupName());
        dto.setDescription(group.getDescription());
        if (group.getParentGroup() != null) {
            dto.setParentGroupId(group.getParentGroup().getGroupId());
            dto.setParentGroupName(group.getParentGroup().getGroupName());
        }
        if (group.getGroupType() != null) {
            dto.setUserGroupTypeId(group.getGroupType().getUserGroupTypeId());
            dto.setUserGroupTypeName(group.getGroupType().getTypeName());
        }
        dto.setCreatedAt(group.getCreatedAt());
        return dto;
    }

    private UserGroupMembershipDTO convertToMembershipDTO(UserGroupMembership membership) {
        UserGroupMembershipDTO dto = new UserGroupMembershipDTO();
        dto.setMembershipId(membership.getMembershipId());
        dto.setUserId(membership.getUser().getUserId());
        dto.setUsername(membership.getUser().getUserName());
        dto.setGroupId(membership.getGroup().getGroupId());
        dto.setGroupName(membership.getGroup().getGroupName());
        return dto;
    }
}
