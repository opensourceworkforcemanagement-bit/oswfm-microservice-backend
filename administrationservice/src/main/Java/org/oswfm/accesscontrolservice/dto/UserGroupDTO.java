package org.oswfm.accesscontrolservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupDTO {

    private Integer groupId;

    @NotBlank(message = "Group name is required")
    private String groupName;

    private String description;

    private Integer parentGroupId;
    private String parentGroupName;

    @NotNull(message = "Group type is required")
    private Integer userGroupTypeId;
    private String userGroupTypeName;

    private OffsetDateTime createdAt;
}
