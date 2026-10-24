package com.languageapp.backend.repository;

import com.languageapp.backend.entity.AccountDeletionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountDeletionTokenRepository extends JpaRepository<AccountDeletionToken, UUID> {}
