package com.tradingplatform.api;

import com.tradingplatform.domain.Notification;
import com.tradingplatform.repository.NotificationRepository;
import com.tradingplatform.repository.UserRepository;
import com.tradingplatform.repository.BrokerAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BrokerAccountRepository brokerAccountRepository;

    public NotificationController(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   BrokerAccountRepository brokerAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAll(Authentication auth) {
        if (auth == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long accountId = getAccountId(auth);
        if (accountId == null) return ResponseEntity.notFound().build();
        List<Notification> notifications = notificationRepository
                .findByBrokerAccountIdOrderByCreatedAtDesc(accountId);
        return ResponseEntity.ok(notifications.stream().map(n -> Map.of(
                "id", n.getId(),
                "title", n.getTitle(),
                "message", n.getMessage(),
                "type", n.getType(),
                "isRead", n.isRead(),
                "createdAt", n.getCreatedAt().toString()
        )).toList());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(Authentication auth) {
        if (auth == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long accountId = getAccountId(auth);
        if (accountId == null) return ResponseEntity.ok(Map.of("count", 0));
        long count = notificationRepository.countByBrokerAccountIdAndIsReadFalse(accountId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<?> markAllRead(Authentication auth) {
        if (auth == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long accountId = getAccountId(auth);
        if (accountId != null) notificationRepository.markAllReadByAccountId(accountId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        if (auth == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        notificationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Notification deleted"));
    }

    private Long getAccountId(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .flatMap(u -> brokerAccountRepository.findByUserIdAndBrokerName(u.getId(), "ANGEL_ONE"))
                .map(a -> a.getId())
                .orElse(null);
    }
}