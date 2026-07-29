package com.devosphere.draugr.world;

import com.devosphere.draugr.world.domain.WorldEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorldEventRepository extends JpaRepository<WorldEvent, UUID> { }
