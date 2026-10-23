package com.medical.agent.web.controller;

import com.medical.agent.model.entity.ChatMessage;
import com.medical.agent.model.repository.ChatMessageRepository;
import com.medical.agent.web.security.AccessGuard;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.*;

/**
 * Doctor-Patient real-time messaging via WebSocket + REST - persisted to MySQL
 */
@Controller
@RequestMapping("/api/messaging")
@PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
public class MessagingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final AccessGuard accessGuard;

    public MessagingController(SimpMessagingTemplate messagingTemplate,
                               ChatMessageRepository chatMessageRepository,
                               AccessGuard accessGuard) {
        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.accessGuard = accessGuard;
    }

    @MessageMapping("/chat.send")
    public void handleMessage(@Payload Map<String, Object> message) {
        accessGuard.requireWebSocketChatSend(str(message.get("fromId")), str(message.get("toId")));
        ChatMessage saved = saveMessage(message);
        Map<String, Object> response = toMap(saved);

        messagingTemplate.convertAndSend("/topic/chat/" + saved.getToId(), response);
        messagingTemplate.convertAndSend("/topic/chat/" + saved.getFromId(), response);
    }

    @PostMapping("/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> message) {
        String fromId = accessGuard.requireCurrentMessagingId();
        message.put("fromId", fromId);
        accessGuard.requireMessagingPeerAccess(str(message.get("toId")));
        ChatMessage saved = saveMessage(message);
        Map<String, Object> response = toMap(saved);

        messagingTemplate.convertAndSend("/topic/chat/" + saved.getToId(), response);
        messagingTemplate.convertAndSend("/topic/chat/" + saved.getFromId(), response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversation/{userId1}/{userId2}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getConversation(
            @PathVariable String userId1, @PathVariable String userId2) {
        accessGuard.requireConversationAccess(userId1, userId2);
        List<ChatMessage> msgs = chatMessageRepository.findConversation(userId1, userId2);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage m : msgs) {
            result.add(toMap(m));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/contacts/{userId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getContacts(@PathVariable String userId) {
        if (!accessGuard.requireCurrentMessagingId().equals(userId)) {
            throw new com.medical.agent.common.exception.BusinessException(403, "无权访问他人联系人");
        }
        List<String> contactIds = chatMessageRepository.findContactIds(userId);
        List<Map<String, Object>> contacts = new ArrayList<>();
        for (String contactId : contactIds) {
            Map<String, Object> contact = new LinkedHashMap<>();
            contact.put("id", contactId);
            ChatMessage latest = chatMessageRepository.findLatestMessage(userId, contactId);
            if (latest != null) {
                contact.put("lastMessage", latest.getContent());
                contact.put("lastTime", latest.getCreatedAt() != null ? latest.getCreatedAt().toString() : null);
                contact.put("fromName", latest.getFromName());
            }
            contacts.add(contact);
        }
        return ResponseEntity.ok(contacts);
    }

    private ChatMessage saveMessage(Map<String, Object> message) {
        ChatMessage entity = ChatMessage.builder()
                .fromId(str(message.get("fromId")))
                .fromName(str(message.get("fromName")))
                .toId(str(message.get("toId")))
                .toName(str(message.get("toName")))
                .content(str(message.get("content")))
                .build();
        return chatMessageRepository.save(entity);
    }

    private Map<String, Object> toMap(ChatMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("fromId", m.getFromId());
        map.put("fromName", m.getFromName());
        map.put("toId", m.getToId());
        map.put("toName", m.getToName());
        map.put("content", m.getContent());
        map.put("timestamp", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return map;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
