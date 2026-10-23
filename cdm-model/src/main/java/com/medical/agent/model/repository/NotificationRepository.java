package com.medical.agent.model.repository;

import com.medical.agent.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<Notification> findByPatientIdIsNullOrderByCreatedAtDesc();

    List<Notification> findAllByOrderByCreatedAtDesc();

    List<Notification> findByReadFalse();

    List<Notification> findByPatientIdAndReadFalse(Long patientId);

    List<Notification> findTop50ByOrderByCreatedAtDesc();

    List<Notification> findTop50ByTypeInOrderByCreatedAtDesc(Collection<String> types);

    List<Notification> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    long deleteByReadTrue();

    long deleteByPatientIdAndReadTrue(Long patientId);
}
