package com.medical.agent.web.websocket;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.model.entity.Notification;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.service.ClinicalNotificationGateway;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket notification pusher - persists to DB and pushes to frontend in real-time
 */
@Component
public class NotificationPusher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPusher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ClinicalNotificationGateway clinicalNotificationGateway;
    private final NotificationRepository notificationRepository;

    public NotificationPusher(SimpMessagingTemplate messagingTemplate,
                              ClinicalNotificationGateway clinicalNotificationGateway,
                              NotificationRepository notificationRepository) {
        this.messagingTemplate = messagingTemplate;
        this.clinicalNotificationGateway = clinicalNotificationGateway;
        this.notificationRepository = notificationRepository;
    }

    @PostConstruct
    public void init() {
        clinicalNotificationGateway.setNotificationConsumer(this::pushNotification);
        log.info("WebSocket notification pusher initialized");
    }

    public void pushNotification(NotificationDTO notification) {
        // Persist to database
        try {
            Notification entity = Notification.builder()
                    .type(notification.getType())
                    .patientId(notification.getPatientId())
                    .title(notification.getTitle())
                    .message(notification.getMessage())
                    .read(false)
                    .build();
            notificationRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist notification: {}", e.getMessage());
        }

        // Push to global notification channel (doctor side)
        messagingTemplate.convertAndSend("/topic/notifications", notification);

        // Push to patient's personal channel
        if (notification.getPatientId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/patient/" + notification.getPatientId(), notification);
        }

        log.info("Notification pushed: type={}, patient={}", notification.getType(), notification.getPatientId());
    }
}
