package org.oswfm.accesscontrolservice.repository;

import org.oswfm.accesscontrolservice.model.entity.UserGroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGroupTypeRepository extends JpaRepository<UserGroupType, Integer> {
}
