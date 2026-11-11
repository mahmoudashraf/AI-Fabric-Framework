package com.ai.fabric.platform.backend.mcp.connection;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface McpConnectionRepository extends JpaRepository<McpConnectionEntity, String> {
    List<McpConnectionEntity> findByDeploymentIdOrderByCreatedAtAsc(String deploymentId);
    long countByDeploymentId(String deploymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from McpConnectionEntity c where c.id = :id")
    Optional<McpConnectionEntity> findLocked(String id);
}
