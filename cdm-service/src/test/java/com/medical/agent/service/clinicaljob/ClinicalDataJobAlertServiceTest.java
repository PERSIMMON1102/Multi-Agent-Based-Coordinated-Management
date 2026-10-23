package com.medical.agent.service.clinicaljob;

import com.medical.agent.common.dto.NotificationDTO;
import com.medical.agent.model.entity.ClinicalDataJob;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.service.ClinicalNotificationGateway;
import com.medical.agent.service.testsupport.RepositoryProxyFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClinicalDataJobAlertServiceTest {

    @Test
    void shouldSendDoctorGlobalDeadLetterNotification() {
        List<NotificationDTO> notifications = new ArrayList<>();
        ClinicalNotificationGateway notificationGateway = new CapturingClinicalNotificationGateway(notifications);
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(7L).name("张三").build())
        ));
        ClinicalDataJobAlertService service = new ClinicalDataJobAlertService(notificationGateway, patientRepository);

        service.notifyDeadLetter(ClinicalDataJob.builder()
                .id(99L)
                .jobType(ClinicalDataJobService.JOB_TYPE_PAPER_CASE)
                .patientId(7L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_PAPER_CASE_RECORD)
                .sourceRefId(12L)
                .correlationId("paper-case-7-001")
                .idempotencyKey("idem-paper-case-7-001")
                .lastStage("medplum_sync_failed")
                .lastError("connect timeout while posting bundle to medplum server")
                .retryCount(3)
                .maxRetries(3)
                .build());

        assertEquals(1, notifications.size());
        NotificationDTO notification = notifications.get(0);
        assertEquals("CLINICAL_JOB_DEAD_LETTER", notification.getType());
        assertNull(notification.getPatientId());
        assertEquals("临床数据作业进入死信队列", notification.getTitle());
        assertTrue(notification.getMessage().contains("张三"));
        assertTrue(notification.getMessage().contains("paper_case_ingest"));
        assertNotNull(notification.getPayload());
    }

    @Test
    void shouldSendDoctorGlobalRunningTimeoutNotification() {
        List<NotificationDTO> notifications = new ArrayList<>();
        ClinicalNotificationGateway notificationGateway = new CapturingClinicalNotificationGateway(notifications);
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(8L).name("李四").build())
        ));
        ClinicalDataJobAlertService service = new ClinicalDataJobAlertService(notificationGateway, patientRepository);

        service.notifyRunningTimeout(ClinicalDataJob.builder()
                .id(108L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .patientId(8L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT)
                .sourceRefId(21L)
                .correlationId("iot-8-001")
                .idempotencyKey("idem-iot-8-001")
                .lastStage("running_timeout")
                .lastError("clinical data job execution timed out after 1800000 ms")
                .retryCount(1)
                .maxRetries(3)
                .build());

        assertEquals(1, notifications.size());
        NotificationDTO notification = notifications.get(0);
        assertEquals("CLINICAL_JOB_RUNNING_TIMEOUT", notification.getType());
        assertNull(notification.getPatientId());
        assertEquals("临床数据作业执行超时", notification.getTitle());
        assertTrue(notification.getMessage().contains("李四"));
        assertTrue(notification.getMessage().contains("huawei_iot_import"));
        assertNotNull(notification.getPayload());
    }

    @Test
    void shouldSuppressDuplicateRunningTimeoutAlertWithinDedupeWindow() {
        List<NotificationDTO> notifications = new ArrayList<>();
        ClinicalNotificationGateway notificationGateway = new CapturingClinicalNotificationGateway(notifications);
        PatientRepository patientRepository = RepositoryProxyFactory.create(PatientRepository.class, Map.of(
                "findById", args -> Optional.of(Patient.builder().id(9L).name("王五").build())
        ));
        AtomicLong now = new AtomicLong(1_000L);
        ClinicalDataJobAlertService service = new ClinicalDataJobAlertService(
                notificationGateway,
                patientRepository,
                null,
                now::get,
                60_000L
        );
        ClinicalDataJob job = ClinicalDataJob.builder()
                .id(109L)
                .jobType(ClinicalDataJobService.JOB_TYPE_HUAWEI_IMPORT)
                .patientId(9L)
                .sourceKind(ClinicalDataJobService.SOURCE_KIND_DEVICE_IMPORT)
                .sourceRefId(22L)
                .correlationId("iot-9-001")
                .idempotencyKey("idem-iot-9-001")
                .lastStage("running_timeout")
                .lastError("clinical data job execution timed out after 1800000 ms")
                .retryCount(1)
                .maxRetries(3)
                .build();

        service.notifyRunningTimeout(job);
        service.notifyRunningTimeout(job);
        now.addAndGet(60_001L);
        service.notifyRunningTimeout(job);

        assertEquals(2, notifications.size());
        assertEquals(ClinicalDataJobAlertService.ALERT_TYPE_RUNNING_TIMEOUT, notifications.get(0).getType());
        assertEquals(ClinicalDataJobAlertService.ALERT_TYPE_RUNNING_TIMEOUT, notifications.get(1).getType());
    }

    private static class CapturingClinicalNotificationGateway implements ClinicalNotificationGateway {

        private final List<NotificationDTO> notifications;

        private CapturingClinicalNotificationGateway(List<NotificationDTO> notifications) {
            this.notifications = notifications;
        }

        @Override
        public void setNotificationConsumer(java.util.function.Consumer<NotificationDTO> consumer) {
        }

        @Override
        public void sendNotification(NotificationDTO notification) {
            notifications.add(notification);
        }
    }
}
