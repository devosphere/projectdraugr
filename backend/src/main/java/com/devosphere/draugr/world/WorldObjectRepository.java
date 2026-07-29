package com.devosphere.draugr.world;

import com.devosphere.draugr.world.domain.WorldObject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorldObjectRepository extends JpaRepository<WorldObject, UUID> { }
