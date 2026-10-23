package com.medical.agent.web.controller;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.Admin;
import com.medical.agent.model.entity.ForumComment;
import com.medical.agent.model.entity.ForumPost;
import com.medical.agent.model.entity.ForumReply;
import com.medical.agent.model.entity.Doctor;
import com.medical.agent.model.entity.Patient;
import com.medical.agent.model.repository.AdminRepository;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.ForumCommentRepository;
import com.medical.agent.model.repository.ForumPostRepository;
import com.medical.agent.model.repository.ForumReplyRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.web.security.AccessGuard;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForumControllerTest {

    @Test
    void createPostShouldPersistActorFromCurrentUser() {
        ForumPostRepository postRepository = mock(ForumPostRepository.class);
        ForumCommentRepository commentRepository = mock(ForumCommentRepository.class);
        ForumReplyRepository replyRepository = mock(ForumReplyRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUser(1L, "patient_001", "patient"));
        PatientRepository patientRepository = mock(PatientRepository.class);
        when(patientRepository.findByUsername("patient_001"))
                .thenReturn(Optional.of(Patient.builder().id(1L).username("patient_001").name("张三").build()));
        ForumController controller = newController(postRepository, commentRepository, replyRepository, mock(DoctorRepository.class),
                mock(AdminRepository.class), patientRepository, currentUserService);

        when(postRepository.save(any(ForumPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.createPost(Map.of("title", "血压波动", "content", "最近一周偏高"));

        ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
        verify(postRepository).save(captor.capture());

        ForumPost saved = captor.getValue();
        assertEquals("张三", saved.getAuthor());
        assertEquals("patient_001", saved.getAuthorUsername());
        assertEquals("patient", saved.getAuthorRole());
    }

    @Test
    void deletePostShouldAllowOwnerAndCascadeFromAggregate() {
        ForumPostRepository postRepository = mock(ForumPostRepository.class);
        ForumCommentRepository commentRepository = mock(ForumCommentRepository.class);
        ForumReplyRepository replyRepository = mock(ForumReplyRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUser(2L, "patient_li", "patient"));
        PatientRepository patientRepository = mock(PatientRepository.class);
        when(patientRepository.findByUsername("patient_li"))
                .thenReturn(Optional.of(Patient.builder().id(2L).username("patient_li").name("李四").build()));
        ForumController controller = newController(postRepository, commentRepository, replyRepository, mock(DoctorRepository.class),
                mock(AdminRepository.class), patientRepository, currentUserService);

        ForumPost post = ForumPost.builder()
                .id(10L)
                .title("饮食记录")
                .content("控制盐分")
                .author("李四")
                .authorUsername("patient_li")
                .authorRole("patient")
                .build();
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));

        controller.deletePost(10L);

        verify(postRepository).delete(post);
    }

    @Test
    void deleteCommentShouldRejectNonOwner() {
        ForumPostRepository postRepository = mock(ForumPostRepository.class);
        ForumCommentRepository commentRepository = mock(ForumCommentRepository.class);
        ForumReplyRepository replyRepository = mock(ForumReplyRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUser(1L, "patient_001", "patient"));
        PatientRepository patientRepository = mock(PatientRepository.class);
        when(patientRepository.findByUsername("patient_001"))
                .thenReturn(Optional.of(Patient.builder().id(1L).username("patient_001").name("张三").build()));
        ForumController controller = newController(postRepository, commentRepository, replyRepository, mock(DoctorRepository.class),
                mock(AdminRepository.class), patientRepository, currentUserService);

        ForumComment comment = ForumComment.builder()
                .id(5L)
                .content("我也有类似问题")
                .author("王医生")
                .authorUsername("doctor_wang")
                .authorRole("doctor")
                .build();
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.deleteComment(5L));

        assertEquals(403, exception.getCode());
        assertEquals("只能删除自己发布的内容", exception.getMessage());
    }

    @Test
    void deleteReplyShouldResolveLegacyDisplayNameFromActorUsername() {
        ForumPostRepository postRepository = mock(ForumPostRepository.class);
        ForumCommentRepository commentRepository = mock(ForumCommentRepository.class);
        ForumReplyRepository replyRepository = mock(ForumReplyRepository.class);
        DoctorRepository doctorRepository = mock(DoctorRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUser(8L, "doctor_zhang", "doctor"));
        when(doctorRepository.findByUsername("doctor_zhang"))
                .thenReturn(Optional.of(Doctor.builder().username("doctor_zhang").name("张医生").build()));
        ForumController controller = newController(postRepository, commentRepository, replyRepository, doctorRepository,
                mock(AdminRepository.class), mock(PatientRepository.class), currentUserService);

        ForumReply reply = ForumReply.builder()
                .id(3L)
                .content("继续观察")
                .author("张医生")
                .authorRole("doctor")
                .build();
        when(replyRepository.findById(3L)).thenReturn(Optional.of(reply));

        controller.deleteReply(3L);

        verify(replyRepository).delete(reply);
    }

    private ForumController newController(ForumPostRepository postRepository,
                                          ForumCommentRepository commentRepository,
                                          ForumReplyRepository replyRepository) {
        return newController(postRepository, commentRepository, replyRepository, mock(DoctorRepository.class),
                mock(AdminRepository.class), mock(PatientRepository.class), mock(CurrentUserService.class));
    }

    private ForumController newController(ForumPostRepository postRepository,
                                          ForumCommentRepository commentRepository,
                                          ForumReplyRepository replyRepository,
                                          DoctorRepository doctorRepository,
                                          AdminRepository adminRepository,
                                          PatientRepository patientRepository,
                                          CurrentUserService currentUserService) {
        return new ForumController(
                postRepository,
                commentRepository,
                replyRepository,
                adminRepository,
                doctorRepository,
                patientRepository,
                new AccessGuard(null, null, null, null, null, null),
                currentUserService);
    }
}
