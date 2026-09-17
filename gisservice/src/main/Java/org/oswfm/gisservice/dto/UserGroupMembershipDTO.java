package org.oswfm.gisservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local mirror of administrationservice's UserGroupMembershipDTO, used only for
 * deserializing responses from {@link org.oswfm.gisservice.client.UserGroupServiceClient}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupMembershipDTO {

    private Integer membershipId;
    private Integer userId;
    private String username;
    private Integer groupId;
    private String groupName;
}
