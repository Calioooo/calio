package com.calio.calendar.groupspace.repository;

import com.calio.calendar.groupspace.domain.GroupSpace;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupSpaceRepository extends JpaRepository<GroupSpace, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select groupSpace from GroupSpace groupSpace where groupSpace.id = :groupSpaceId")
    Optional<GroupSpace> findByIdForUpdate(@Param("groupSpaceId") Long groupSpaceId);
}
