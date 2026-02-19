package com.languageapp.backend.repository;

import com.languageapp.backend.entity.AdminLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminLogRepository extends JpaRepository<AdminLog, UUID> {
    List<AdminLog> findByAdminUserId(UUID adminId);
}