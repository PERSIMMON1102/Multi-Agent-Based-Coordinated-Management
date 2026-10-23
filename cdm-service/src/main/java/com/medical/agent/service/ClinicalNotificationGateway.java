package com.medical.agent.service;

import com.medical.agent.common.dto.NotificationDTO;

public interface ClinicalNotificationGateway {

    void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer);

    void sendNotification(NotificationDTO notification);
}
