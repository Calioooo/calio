package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOrderByTaskIdAsc();
}
