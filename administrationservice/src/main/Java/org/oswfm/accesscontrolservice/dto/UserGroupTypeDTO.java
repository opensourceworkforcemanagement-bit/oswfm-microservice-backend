package org.oswfm.accesscontrolservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupTypeDTO {

    private Integer userGroupTypeId;

    private String typeName;

    private String description;

    private OffsetDateTime createdAt;
}
