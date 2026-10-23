package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class DefaultClinicalNotificationGateway implements ClinicalNotificationGateway {

    private Consumer<NotificationDTO> notificationConsumer;

    @Override
    public void setNotificationConsumer(Consumer<NotificationDTO> consumer) {
        this.notificationConsumer = consumer;
    }

    @Override
    public void sendNotification(NotificationDTO notification) {
        if (notificationConsumer != null) {
            notificationConsumer.accept(notification);
        }
    }
}
