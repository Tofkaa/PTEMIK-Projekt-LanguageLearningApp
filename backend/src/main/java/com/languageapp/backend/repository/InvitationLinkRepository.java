package com.languageapp.backend.repository;

import com.languageapp.backend.entity.InvitationLink;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface InvitationLinkRepository extends JpaRepository<InvitationLink, UUID> {
    Optional<InvitationLink> findByToken(String token);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitationLink i where i.token=:token")
    Optional<InvitationLink> findByTokenForUpdate(@Param("token") String token);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitationLink i where i.scopeKey=:scope")
    Optional<InvitationLink> findScopeForUpdate(@Param("scope") String scope);
    List<InvitationLink> findByOwnerUserId(UUID userId);
    @Modifying
    @Query("update InvitationLink i set i.token=null, i.revokedAt=:now where i.owner.userId=:userId")
    void revokeAllForOwner(@Param("userId") UUID userId, @Param("now") java.time.Instant now);
}
