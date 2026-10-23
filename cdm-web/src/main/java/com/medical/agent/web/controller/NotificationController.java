package com.medical.agent.web.controller;

import com.medical.agent.model.entity.Notification;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final AccessGuard accessGuard;

    public NotificationController(NotificationRepository notificationRepository,
                                  AccessGuard accessGuard) {
        this.notificationRepository = notificationRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Get all notifications (for doctor side)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<List<Notification>> getAll() {
        accessGuard.requireDoctorOrAdmin();
        List<Notification> list = notificationRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(list.size() > 200 ? list.subList(0, 200) : list);
    }

    /**
     * Get notifications for a specific patient
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Notification>> getByPatient(@PathVariable Long patientId) {
        accessGuard.requireNotificationAccess(patientId);
        return ResponseEntity.ok(notificationRepository.findByPatientIdOrderByCreatedAtDesc(patientId));
    }

    /**
     * Get unread count for a patient
     */
    @GetMapping("/patient/{patientId}/unread")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@PathVariable Long patientId) {
        accessGuard.requireNotificationAccess(patientId);
        List<Notification> unread = notificationRepository.findByPatientIdAndReadFalse(patientId);
        return ResponseEntity.ok(Map.of("count", unread.size()));
    }

    /**
     * Mark a notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        accessGuard.requireNotificationWriteAccess(id);
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    /**
     * Mark all notifications as read for doctor/global side
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Void> markAllRead() {
        accessGuard.requireDoctorOrAdmin();
        List<Notification> unread = notificationRepository.findByReadFalse();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark all notifications as read for a patient
     */
    @PutMapping("/patient/{patientId}/read-all")
    public ResponseEntity<Void> markAllRead(@PathVariable Long patientId) {
        accessGuard.requireNotificationAccess(patientId);
        List<Notification> unread = notificationRepository.findByPatientIdAndReadFalse(patientId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete a single notification
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accessGuard.requireNotificationWriteAccess(id);
        notificationRepository.findById(id).ifPresent(notificationRepository::delete);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete read notifications for doctor/global side
     */
    @DeleteMapping("/read")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<Map<String, Object>> deleteReadNotifications() {
        accessGuard.requireDoctorOrAdmin();
        long deleted = notificationRepository.deleteByReadTrue();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /**
     * Delete read notifications for a patient
     */
    @DeleteMapping("/patient/{patientId}/read")
    public ResponseEntity<Map<String, Object>> deleteReadNotifications(@PathVariable Long patientId) {
        accessGuard.requireNotificationAccess(patientId);
        long deleted = notificationRepository.deleteByPatientIdAndReadTrue(patientId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
