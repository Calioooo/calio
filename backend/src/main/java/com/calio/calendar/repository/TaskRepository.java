package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Task;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOrderByTaskIdAsc();

    Page<Task> findByCompletedFalse(Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Task task where task.completed = true and task.completedAt < :cutoff")
    int deleteCompletedTasksOlderThan(@Param("cutoff") Instant cutoff);
}
