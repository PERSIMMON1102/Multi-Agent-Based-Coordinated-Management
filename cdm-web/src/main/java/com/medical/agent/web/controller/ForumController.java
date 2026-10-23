package com.medical.agent.web.controller;

import com.medical.agent.common.exception.BusinessException;
import com.medical.agent.model.entity.ForumComment;
import com.medical.agent.model.entity.ForumPost;
import com.medical.agent.model.entity.ForumReply;
import com.medical.agent.model.repository.AdminRepository;
import com.medical.agent.model.repository.DoctorRepository;
import com.medical.agent.model.repository.ForumCommentRepository;
import com.medical.agent.model.repository.ForumPostRepository;
import com.medical.agent.model.repository.ForumReplyRepository;
import com.medical.agent.model.repository.PatientRepository;
import com.medical.agent.web.security.AccessGuard;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Forum: posts, comments, and replies - persisted to MySQL
 */
@RestController
@RequestMapping("/api/forum")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
public class ForumController {

    private final ForumPostRepository postRepository;
    private final ForumCommentRepository commentRepository;
    private final ForumReplyRepository replyRepository;
    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AccessGuard accessGuard;
    private final CurrentUserService currentUserService;

    @Autowired
    public ForumController(ForumPostRepository postRepository,
                           ForumCommentRepository commentRepository,
                           ForumReplyRepository replyRepository,
                           AdminRepository adminRepository,
                           DoctorRepository doctorRepository,
                           PatientRepository patientRepository,
                           AccessGuard accessGuard,
                           CurrentUserService currentUserService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.replyRepository = replyRepository;
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.accessGuard = accessGuard;
        this.currentUserService = currentUserService;
    }

    ForumController(ForumPostRepository postRepository,
                    ForumCommentRepository commentRepository,
                    ForumReplyRepository replyRepository,
                    AdminRepository adminRepository,
                    DoctorRepository doctorRepository,
                    PatientRepository patientRepository) {
        this(postRepository, commentRepository, replyRepository, adminRepository, doctorRepository, patientRepository,
                new AccessGuard(null, null, null, null, null, null), null);
    }

    @GetMapping("/posts")
    public ResponseEntity<List<Map<String, Object>>> getPosts() {
        accessGuard.currentUser();
        List<ForumPost> posts = postRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ForumPost post : posts) {
            result.add(postToMap(post, false));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> getPost(@PathVariable Long id) {
        accessGuard.currentUser();
        return postRepository.findById(id)
                .map(post -> ResponseEntity.ok(postToMap(post, true)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/posts")
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Map<String, Object> request) {
        ForumActor actor = currentForumActor();
        ForumPost post = ForumPost.builder()
                .title((String) request.get("title"))
                .content((String) request.get("content"))
                .author(actor.displayName())
                .authorUsername(actor.username())
                .authorRole(actor.role())
                .tags(tagsToString(request.get("tags")))
                .build();
        postRepository.save(post);
        return ResponseEntity.ok(postToMap(post, false));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable Long postId,
                                                          @RequestBody Map<String, Object> request) {
        ForumActor actor = currentForumActor();
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));

        ForumComment comment = ForumComment.builder()
                .post(post)
                .content((String) request.get("content"))
                .author(actor.displayName())
                .authorUsername(actor.username())
                .authorRole(actor.role())
                .build();
        commentRepository.save(comment);
        return ResponseEntity.ok(commentToMap(comment));
    }

    @PostMapping("/comments/{commentId}/replies")
    public ResponseEntity<Map<String, Object>> addReply(@PathVariable Long commentId,
                                                        @RequestBody Map<String, Object> request) {
        ForumActor actor = currentForumActor();
        ForumComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));

        ForumReply reply = ForumReply.builder()
                .comment(comment)
                .content((String) request.get("content"))
                .author(actor.displayName())
                .authorUsername(actor.username())
                .authorRole(actor.role())
                .build();
        replyRepository.save(reply);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", reply.getId());
        map.put("content", reply.getContent());
        map.put("author", reply.getAuthor());
        map.put("authorRole", reply.getAuthorRole());
        map.put("createdAt", reply.getCreatedAt() != null ? reply.getCreatedAt().toString() : null);
        return ResponseEntity.ok(map);
    }

    @DeleteMapping("/posts/{postId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deletePost(@PathVariable Long postId) {
        ForumActor actor = currentForumActor();
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(404, "帖子不存在"));
        assertCanDelete(post.getAuthorUsername(), post.getAuthor(), post.getAuthorRole(),
                actor.username(), actor.displayName(), actor.role());
        postRepository.delete(post);
        return ResponseEntity.ok(Map.of("message", "帖子删除成功"));
    }

    @DeleteMapping("/comments/{commentId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteComment(@PathVariable Long commentId) {
        ForumActor actor = currentForumActor();
        ForumComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(404, "评论不存在"));
        assertCanDelete(comment.getAuthorUsername(), comment.getAuthor(), comment.getAuthorRole(),
                actor.username(), actor.displayName(), actor.role());
        commentRepository.delete(comment);
        return ResponseEntity.ok(Map.of("message", "评论删除成功"));
    }

    @DeleteMapping("/replies/{replyId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteReply(@PathVariable Long replyId) {
        ForumActor actor = currentForumActor();
        ForumReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(404, "回复不存在"));
        assertCanDelete(reply.getAuthorUsername(), reply.getAuthor(), reply.getAuthorRole(),
                actor.username(), actor.displayName(), actor.role());
        replyRepository.delete(reply);
        return ResponseEntity.ok(Map.of("message", "回复删除成功"));
    }

    private Map<String, Object> postToMap(ForumPost post, boolean includeComments) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", post.getId());
        map.put("title", post.getTitle());
        map.put("content", post.getContent());
        map.put("author", post.getAuthor());
        map.put("authorUsername", resolveKnownUsername(post.getAuthorUsername(), post.getAuthor(), post.getAuthorRole()));
        map.put("authorRole", post.getAuthorRole());
        map.put("tags", stringToTags(post.getTags()));
        map.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);

        if (includeComments) {
            List<Map<String, Object>> commentList = new ArrayList<>();
            if (post.getComments() != null) {
                for (ForumComment c : post.getComments()) {
                    commentList.add(commentToMap(c));
                }
            }
            map.put("comments", commentList);
        } else {
            map.put("commentCount", post.getComments() != null ? post.getComments().size() : 0);
            map.put("comments", List.of());
        }
        return map;
    }

    private Map<String, Object> commentToMap(ForumComment comment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", comment.getId());
        map.put("content", comment.getContent());
        map.put("author", comment.getAuthor());
        map.put("authorUsername", resolveKnownUsername(comment.getAuthorUsername(), comment.getAuthor(), comment.getAuthorRole()));
        map.put("authorRole", comment.getAuthorRole());
        map.put("createdAt", comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null);

        List<Map<String, Object>> replies = new ArrayList<>();
        if (comment.getReplies() != null) {
            for (ForumReply r : comment.getReplies()) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("id", r.getId());
                rm.put("content", r.getContent());
                rm.put("author", r.getAuthor());
                rm.put("authorUsername", resolveKnownUsername(r.getAuthorUsername(), r.getAuthor(), r.getAuthorRole()));
                rm.put("authorRole", r.getAuthorRole());
                rm.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                replies.add(rm);
            }
        }
        map.put("replies", replies);
        return map;
    }

    @SuppressWarnings("unchecked")
    private String tagsToString(Object tags) {
        if (tags == null) {
            return "";
        }
        if (tags instanceof List) {
            return String.join(",", (List<String>) tags);
        }
        return tags.toString();
    }

    private List<String> stringToTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.asList(tags.split(","));
    }

    private void assertCanDelete(String contentAuthorUsername,
                                 String contentAuthorDisplayName,
                                 String contentAuthorRole,
                                 String actorUsername,
                                 String actorDisplayName,
                                 String actorRole) {
        if (isBlank(actorRole) || (isBlank(actorUsername) && isBlank(actorDisplayName))) {
            throw new BusinessException(401, "当前登录信息缺失，无法执行删除");
        }
        if (!sameText(contentAuthorRole, actorRole)) {
            throw new BusinessException(403, "只能删除自己发布的内容");
        }
        String normalizedContentAuthorUsername = resolveKnownUsername(contentAuthorUsername, contentAuthorDisplayName, contentAuthorRole);
        String normalizedActorDisplayName = resolveKnownDisplayName("", actorUsername, actorRole);
        if (!isOwnedByActor(normalizedContentAuthorUsername, contentAuthorDisplayName, actorUsername, normalizedActorDisplayName)) {
            throw new BusinessException(403, "只能删除自己发布的内容");
        }
    }

    private boolean isOwnedByActor(String contentAuthorUsername,
                                   String contentAuthorDisplayName,
                                   String actorUsername,
                                   String actorDisplayName) {
        boolean usernameMatches = !isBlank(contentAuthorUsername)
                && !isBlank(actorUsername)
                && sameText(contentAuthorUsername, actorUsername);
        boolean displayNameMatches = !isBlank(contentAuthorDisplayName)
                && !isBlank(actorDisplayName)
                && sameText(contentAuthorDisplayName, actorDisplayName);
        boolean crossMatchesUsernameToDisplayName = !isBlank(contentAuthorUsername)
                && !isBlank(actorDisplayName)
                && sameText(contentAuthorUsername, actorDisplayName);
        boolean crossMatchesDisplayNameToUsername = !isBlank(contentAuthorDisplayName)
                && !isBlank(actorUsername)
                && sameText(contentAuthorDisplayName, actorUsername);
        return usernameMatches || displayNameMatches || crossMatchesUsernameToDisplayName || crossMatchesDisplayNameToUsername;
    }

    private ForumActor currentForumActor() {
        AuthenticatedUser user = currentUserService != null ? currentUserService.requireCurrentUser() : accessGuard.currentUser();
        return new ForumActor(
                user.username(),
                resolveKnownDisplayName("", user.username(), user.role()),
                user.role()
        );
    }

    private String resolveKnownUsername(String storedUsername, String displayName, String role) {
        String username = text(storedUsername);
        if (!isBlank(username)) {
            return username;
        }
        String normalizedRole = text(role).toLowerCase(Locale.ROOT);
        String normalizedDisplayName = text(displayName);
        if (isBlank(normalizedRole) || isBlank(normalizedDisplayName)) {
            return "";
        }
        return switch (normalizedRole) {
            case "admin" -> adminRepository.findByName(normalizedDisplayName).map(a -> text(a.getUsername())).orElse("");
            case "doctor" -> doctorRepository.findByName(normalizedDisplayName).map(d -> text(d.getUsername())).orElse("");
            case "patient" -> patientRepository.findByName(normalizedDisplayName).map(p -> text(p.getUsername())).orElse("");
            default -> "";
        };
    }

    private String resolveKnownDisplayName(String storedDisplayName, String username, String role) {
        String displayName = text(storedDisplayName);
        if (!isBlank(displayName)) {
            return displayName;
        }
        String normalizedRole = text(role).toLowerCase(Locale.ROOT);
        String normalizedUsername = text(username);
        if (isBlank(normalizedRole) || isBlank(normalizedUsername)) {
            return "";
        }
        return switch (normalizedRole) {
            case "admin" -> adminRepository.findByUsername(normalizedUsername).map(a -> text(a.getName())).orElse("");
            case "doctor" -> doctorRepository.findByUsername(normalizedUsername).map(d -> text(d.getName())).orElse("");
            case "patient" -> patientRepository.findByUsername(normalizedUsername).map(p -> text(p.getName())).orElse("");
            default -> "";
        };
    }

    private boolean sameText(String left, String right) {
        return text(left).equalsIgnoreCase(text(right));
    }

    private boolean isBlank(String value) {
        return text(value).isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private record ForumActor(String username, String displayName, String role) {
    }
}
