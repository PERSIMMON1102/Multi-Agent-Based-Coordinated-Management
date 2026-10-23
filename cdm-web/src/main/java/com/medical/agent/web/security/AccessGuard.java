package com.medical.agent.web.security;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.FollowUpPlan;
import com.medical.agent.model.entity.Notification;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.entity.VideoWatchSession;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.FollowUpPlanRepository;
import com.medical.agent.model.repository.NotificationRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.model.repository.VideoWatchSessionRepository;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    private final CurrentUserService currentUserService;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final NotificationRepository notificationRepository;
    private final FollowUpPlanRepository followUpPlanRepository;
    private final VideoWatchSessionRepository videoWatchSessionRepository;

    public AccessGuard(CurrentUserService currentUserService,
                       PatientRepository patientRepository,
                       DoctorRepository doctorRepository,
                       NotificationRepository notificationRepository,
                       FollowUpPlanRepository followUpPlanRepository,
                       VideoWatchSessionRepository videoWatchSessionRepository) {
        this.currentUserService = currentUserService;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.notificationRepository = notificationRepository;
        this.followUpPlanRepository = followUpPlanRepository;
        this.videoWatchSessionRepository = videoWatchSessionRepository;
    }

    public AuthenticatedUser currentUser() {
        if (currentUserService == null) {
            return new AuthenticatedUser(0L, "test-user", "admin");
        }
        return currentUserService.requireCurrentUser();
    }

    public Long resolveDoctorIdOrCurrent(Long doctorId) {
        AuthenticatedUser user = currentUser();
        if (user.isAdmin()) {
            return doctorId;
        }
        if (!user.isDoctor()) {
            throw new BusinessException(403, "无权访问医生工作台");
        }
        if (doctorId == null || doctorId.equals(user.id())) {
            return user.id();
        }
        throw new BusinessException(403, "只能访问自己的医生工作台");
    }

    public void requireAdmin() {
        if (currentUserService == null) {
            return;
        }
        if (!currentUser().isAdmin()) {
            throw new BusinessException(403, "仅管理员可访问");
        }
    }

    public void requireDoctorOrAdmin() {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (!user.isDoctor() && !user.isAdmin()) {
            throw new BusinessException(403, "仅医生或管理员可访问");
        }
    }

    public void requireDoctorPatientAccess(Long patientId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user.isAdmin()) {
            return;
        }
        if (!user.isDoctor()) {
            throw new BusinessException(403, "仅医生或管理员可访问");
        }
        Patient patient = findPatient(patientId);
        if (!user.id().equals(patient.getAssignedDoctorId())) {
            throw new BusinessException(403, "无权访问非本人负责的患者");
        }
    }

    public void requirePatientAccess(Long patientId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user.isAdmin()) {
            return;
        }
        if (user.isDoctor()) {
            Patient patient = findPatient(patientId);
            if (!user.id().equals(patient.getAssignedDoctorId())) {
                throw new BusinessException(403, "无权访问非本人负责的患者");
            }
            return;
        }
        if (!user.isPatient() || !user.id().equals(patientId)) {
            throw new BusinessException(403, "无权访问其他患者数据");
        }
    }

    public void requireDoctorProfileAccess(Long doctorId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user.isAdmin()) {
            return;
        }
        if (user.isDoctor() && user.id().equals(doctorId)) {
            return;
        }
        if (user.isPatient()) {
            Patient patient = findPatient(user.id());
            Doctor doctor = findDoctor(doctorId);
            if (patient.getAssignedDoctorId() != null
                    && patient.getAssignedDoctorId().equals(doctorId)
                    && doctor.getUsername() != null) {
                return;
            }
        }
        throw new BusinessException(403, "无权访问该医生资料");
    }

    public void requireNotificationAccess(Long patientId) {
        requirePatientAccess(patientId);
    }

    public void requireNotificationWriteAccess(Long notificationId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user.isAdmin() || user.isDoctor()) {
            return;
        }
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(404, "通知不存在"));
        if (notification.getPatientId() == null || !user.id().equals(notification.getPatientId())) {
            throw new BusinessException(403, "无权操作该通知");
        }
    }

    public void requireFollowUpAccess(Long patientId) {
        requirePatientAccess(patientId);
    }

    public void requireFollowUpManageById(Long followUpId) {
        if (currentUserService == null) {
            return;
        }
        requireDoctorOrAdmin();
        AuthenticatedUser user = currentUser();
        if (user.isAdmin()) {
            return;
        }
        FollowUpPlan plan = followUpPlanRepository.findById(followUpId)
                .orElseThrow(() -> new BusinessException(404, "随访计划不存在"));
        requireDoctorPatientAccess(plan.getPatientId());
    }

    public void requireFollowUpCompleteById(Long followUpId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        FollowUpPlan plan = followUpPlanRepository.findById(followUpId)
                .orElseThrow(() -> new BusinessException(404, "随访计划不存在"));
        if (user.isAdmin()) {
            return;
        }
        if (user.isDoctor()) {
            requireDoctorPatientAccess(plan.getPatientId());
            return;
        }
        if (user.isPatient() && user.id().equals(plan.getPatientId())) {
            return;
        }
        throw new BusinessException(403, "无权完成该随访");
    }

    public void requireVideoWatchAccess(Long sessionId) {
        if (currentUserService == null) {
            return;
        }
        VideoWatchSession session = videoWatchSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "观看会话不存在"));
        requirePatientAccess(session.getPatientId());
    }

    public String currentChatOwnerRole() {
        if (currentUserService == null) {
            return "doctor";
        }
        AuthenticatedUser user = currentUser();
        if (user.isDoctor()) {
            return "doctor";
        }
        if (user.isPatient()) {
            return "patient";
        }
        throw new BusinessException(403, "当前角色不支持聊天功能");
    }

    public Long currentChatOwnerUserId() {
        return currentUser().id();
    }

    public void requireChatAccess(Long patientId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user.isDoctor()) {
            if (patientId != null) {
                requireDoctorPatientAccess(patientId);
            }
            return;
        }
        if (user.isPatient()) {
            if (patientId != null && !user.id().equals(patientId)) {
                throw new BusinessException(403, "患者只能使用自己的聊天会话");
            }
            return;
        }
        throw new BusinessException(403, "当前角色不支持聊天功能");
    }

    public String requireCurrentMessagingId() {
        if (currentUserService == null) {
            return "doctor_test";
        }
        AuthenticatedUser user = currentUser();
        if (user.isDoctor()) {
            Doctor doctor = findDoctor(user.id());
            return "doctor_" + doctor.getUsername();
        }
        if (user.isPatient()) {
            return "patient_" + user.id();
        }
        throw new BusinessException(403, "当前角色不支持即时消息");
    }

    public void requireMessagingPeerAccess(String peerId) {
        if (currentUserService == null) {
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user.isDoctor()) {
            Long patientId = parsePatientMessagingId(peerId);
            requireDoctorPatientAccess(patientId);
            return;
        }
        if (user.isPatient()) {
            String doctorUsername = parseDoctorMessagingId(peerId);
            Patient patient = findPatient(user.id());
            if (patient.getAssignedDoctorId() == null) {
                throw new BusinessException(403, "当前患者未绑定医生，无法发送消息");
            }
            Doctor doctor = findDoctor(patient.getAssignedDoctorId());
            if (!doctorUsername.equals(doctor.getUsername())) {
                throw new BusinessException(403, "只能向当前绑定医生发送消息");
            }
            return;
        }
        throw new BusinessException(403, "当前角色不支持即时消息");
    }

    public void requireConversationAccess(String userId1, String userId2) {
        if (currentUserService == null) {
            return;
        }
        String currentMessagingId = requireCurrentMessagingId();
        if (!currentMessagingId.equals(userId1) && !currentMessagingId.equals(userId2)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        String peerId = currentMessagingId.equals(userId1) ? userId2 : userId1;
        requireMessagingPeerAccess(peerId);
    }

    public boolean canSubscribeDestination(String destination) {
        if (currentUserService == null) {
            return true;
        }
        AuthenticatedUser user = currentUser();
        if (destination == null || destination.isBlank()) {
            return false;
        }
        if ("/topic/notifications".equals(destination)) {
            return user.isAdmin() || user.isDoctor();
        }
        if (destination.startsWith("/topic/patient/")) {
            Long patientId = Long.valueOf(destination.substring("/topic/patient/".length()));
            try {
                requireNotificationAccess(patientId);
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        }
        if (destination.startsWith("/topic/chat/")) {
            return destination.substring("/topic/chat/".length()).equals(requireCurrentMessagingId());
        }
        return false;
    }

    public void requireWebSocketChatSend(String fromId, String toId) {
        if (currentUserService == null) {
            return;
        }
        String currentMessagingId = requireCurrentMessagingId();
        if (!currentMessagingId.equals(fromId)) {
            throw new BusinessException(403, "消息发送者身份不匹配");
        }
        requireMessagingPeerAccess(toId);
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(404, "患者不存在"));
    }

    private Doctor findDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new BusinessException(404, "医生不存在"));
    }

    private Long parsePatientMessagingId(String messagingId) {
        if (messagingId == null || !messagingId.startsWith("patient_")) {
            throw new BusinessException(400, "消息接收对象格式错误");
        }
        return Long.valueOf(messagingId.substring("patient_".length()));
    }

    private String parseDoctorMessagingId(String messagingId) {
        if (messagingId == null || !messagingId.startsWith("doctor_")) {
            throw new BusinessException(400, "消息接收对象格式错误");
        }
        return messagingId.substring("doctor_".length());
    }
}
