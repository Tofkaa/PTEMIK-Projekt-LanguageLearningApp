package com.languageapp.backend.repository;

import com.languageapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    record VocabularyContext(UUID userId, UUID courseId, String language) { }
    @Query("select new com.languageapp.backend.repository.UserRepository$VocabularyContext(u.userId,c.courseId,c.languageCode) "
            + "from User u left join u.activeCourse c where u.email=:email and u.isActive=true and u.role<>com.languageapp.backend.enums.Role.DELETED")
    Optional<VocabularyContext> findVocabularyContext(@Param("email") String email);

    record AccessState(UUID userId,boolean active,boolean verified,com.languageapp.backend.enums.Role role) { }
    @Query("select new com.languageapp.backend.repository.UserRepository$AccessState(u.userId,u.isActive,u.isVerified,u.role) from User u where u.email=:email")
    Optional<AccessState> findAccessState(@Param("email") String email);

    /** Serialize rare privilege changes before reading the actor or locking the target row. */
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    @Query(value="select 1 from pg_advisory_xact_lock(hashtextextended('languageapp-admin-access',0))",nativeQuery=true)
    int lockAdminMutations();
    @Query("select count(u) from User u where u.role=com.languageapp.backend.enums.Role.ADMIN and u.isActive=true and u.isVerified=true")
    long countUsableAdmins();

    Optional<User> findByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    // A pre-existing provider+subject is evidence; email equality alone never is.
    @Query("select u.userId from User u where u.authProvider=:provider and u.providerId=:subject")
    List<UUID> findLegacyOAuthIds(@Param("provider") com.languageapp.backend.enums.AuthProvider provider,
            @Param("subject") String subject,org.springframework.data.domain.Pageable limit);

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Query("select u.userId from User u where u.email = :email and u.isActive = true")
    Optional<UUID> findActiveProfileUserId(@Param("email") String email);

    /** Never merge a stale User after a slow external upload; update only this field on an active account. */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.profilePictureUrl = :url where u.userId = :id and u.isActive = true")
    int updateActiveProfilePicture(@Param("id") UUID id, @Param("url") String url);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForAuthentication(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> findForSessionUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> findForLearningUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForAccountDeletion(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForInvitationUpdate(@Param("email") String email);

    boolean existsByEmail(String email);


    boolean existsByFriendCode(String friendCode);
    boolean existsByNameAndUserTag(String name, String userTag);

    List<User> findTop50ByOrderByXpDesc();
    List<User> findTop50ByOrderByStreakDesc();

    Optional<User> findByFriendCode(String friendCode);
    Optional<User> findByNameAndUserTag(String name, String userTag);


    @Query(value = "SELECT * FROM users ORDER BY created_at DESC", nativeQuery = true)
    List<User> findAllUsersIncludingDeleted();

    @Modifying
    @Query(value = "UPDATE users SET is_active = :status WHERE user_id = :userId", nativeQuery = true)
    void updateUserStatus(@Param("userId") java.util.UUID userId, @Param("status") boolean status);

    /**
     * Bulk update operation for the Scheduled Job.
     * Resets the streak to 0 for all users whose last activity was before yesterday.
     */
    @Modifying
    @Query("UPDATE User u SET u.streak = 0 WHERE u.streak > 0 AND (u.lastActivityDate IS NULL OR u.lastActivityDate < :yesterday)")
    int resetExpiredStreaks(@Param("yesterday") LocalDate yesterday);

}
