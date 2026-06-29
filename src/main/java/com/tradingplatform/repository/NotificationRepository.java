package com.tradingplatform.repository;

import com.tradingplatform.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByBrokerAccountIdOrderByCreatedAtDesc(Long brokerAccountId);
    long countByBrokerAccountIdAndIsReadFalse(Long brokerAccountId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.brokerAccount.id = :accountId")
    void markAllReadByAccountId(Long accountId);
}