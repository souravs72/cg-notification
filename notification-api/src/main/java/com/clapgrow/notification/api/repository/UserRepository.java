package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndIsDeletedFalse(String email);
    boolean existsByEmailAndIsDeletedFalse(String email);
    Optional<User> findByIdAndIsDeletedFalse(UUID id);
    
    /**
     * Atomically update sessionsUsed for a user.
     * Uses database-level update to prevent race conditions.
     * 
     * @param id User ID
     * @param sessionsUsed New sessions used count
     * @return Number of rows updated (should be 1 if user exists and is not deleted)
     */
    @Modifying
    @Query("""
        UPDATE User u 
        SET u.sessionsUsed = :sessionsUsed, u.updatedBy = :updatedBy
        WHERE u.id = :id AND u.isDeleted = false
        """)
    int updateSessionsUsed(@Param("id") UUID id, @Param("sessionsUsed") int sessionsUsed, @Param("updatedBy") String updatedBy);
}

