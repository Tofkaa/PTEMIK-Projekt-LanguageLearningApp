package com.languageapp.backend.repository;

import com.languageapp.backend.entity.OAuthIdentity;
import com.languageapp.backend.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity,UUID> {
    @Query("select i.user.userId from OAuthIdentity i where i.provider=:provider and i.subject=:subject")
    Optional<UUID> findUserId(@Param("provider") AuthProvider provider,@Param("subject") String subject);
    @Query(value="select 1 from pg_advisory_xact_lock(hashtextextended(:key,0))",nativeQuery=true)
    Integer lockIdentity(@Param("key") String key);
    List<OAuthIdentity> findByUserUserId(UUID userId);
    void deleteByUserUserId(UUID userId);
}
