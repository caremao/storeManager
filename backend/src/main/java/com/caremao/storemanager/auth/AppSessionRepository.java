package com.caremao.storemanager.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AppSessionRepository extends JpaRepository<AppSession, UUID> {

    Optional<AppSession> findByTokenHash(String tokenHash);

    @Transactional
    @Modifying
    @Query("delete from AppSession s where s.characterId = :characterId")
    int deleteByCharacterId(@Param("characterId") UUID characterId);

    @Transactional
    @Modifying
    @Query("delete from AppSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
