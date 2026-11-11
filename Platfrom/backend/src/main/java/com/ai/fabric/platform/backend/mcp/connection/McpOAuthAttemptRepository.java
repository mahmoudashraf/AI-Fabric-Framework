package com.ai.fabric.platform.backend.mcp.connection;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface McpOAuthAttemptRepository extends JpaRepository<McpOAuthAttemptEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from McpOAuthAttemptEntity a where a.stateHash = :stateHash")
    Optional<McpOAuthAttemptEntity> findLocked(String stateHash);
    @Modifying
    @Query("update McpOAuthAttemptEntity a set a.encryptedVerifier = null, a.consumedAt = :now where a.expiresAt < :now and a.encryptedVerifier is not null")
    int clearExpired(Instant now);
    @Modifying
    @Query("update McpOAuthAttemptEntity a set a.encryptedVerifier = null, a.consumedAt = :now where a.connectionId = :connectionId and a.consumedAt is null")
    int cancelForConnection(String connectionId, Instant now);
}
