package com.example.marketsocial.controller;

import com.example.marketsocial.model.Message;
import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.MessageRepository;
import com.example.marketsocial.repository.UserRepository;
import com.example.marketsocial.service.MessageEmailNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MessageEmailNotificationService messageEmailNotificationService;

    public MessageController(
            UserRepository userRepository,
            MessageRepository messageRepository,
            MessageEmailNotificationService messageEmailNotificationService
    ) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.messageEmailNotificationService = messageEmailNotificationService;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserSummary> users(Authentication authentication) {
        User currentUser = requireUser(authentication);
        List<UnreadConversationSummary> unreadConversations = unreadSummariesFor(currentUser);
        return userRepository.findAll().stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .map(user -> UserSummary.from(user, unreadConversations))
                .toList();
    }

    @GetMapping("/messages/{username}")
    @Transactional
    public List<MessageResponse> conversation(
            @PathVariable String username,
            Authentication authentication
    ) {
        User currentUser = requireUser(authentication);
        User otherUser = userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Message> messages = messageRepository.findConversation(currentUser, otherUser);
        messageRepository.markConversationAsRead(currentUser, otherUser, LocalDateTime.now());

        return messages.stream()
                .map(MessageResponse::from)
                .toList();
    }

    @GetMapping("/messages/notifications")
    @Transactional(readOnly = true)
    public InboxNotificationResponse notifications(Authentication authentication) {
        User currentUser = requireUser(authentication);
        List<UnreadConversationSummary> conversations = unreadSummariesFor(currentUser);
        int totalUnreadCount = conversations.stream()
                .mapToInt(UnreadConversationSummary::unreadCount)
                .sum();
        return new InboxNotificationResponse(totalUnreadCount, conversations);
    }

    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        User sender = requireUser(authentication);
        User receiver = userRepository.findByUsername(normalize(request.toUsername()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));

        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }

        if (sender.getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot message yourself");
        }

        Message message = new Message(null, sender, receiver, content, LocalDateTime.now(), null);
        Message saved = messageRepository.save(message);
        messageEmailNotificationService.sendNewMessageNotification(receiver, sender, saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(saved));
    }

    private List<UnreadConversationSummary> unreadSummariesFor(User currentUser) {
        return messageRepository.findByReceiverAndReadAtIsNullOrderByTimestampDesc(currentUser).stream()
                .collect(java.util.stream.Collectors.toMap(
                        unread -> unread.getSender().getUsername(),
                        unread -> new UnreadConversationSummary(
                                unread.getSender().getUsername(),
                                unread.getSender().getDisplayName(),
                                unread.getId(),
                                preview(unread.getContent()),
                                unread.getTimestamp(),
                                1
                        ),
                        (existing, ignored) -> new UnreadConversationSummary(
                                existing.username(),
                                existing.displayName(),
                                existing.latestMessageId(),
                                existing.preview(),
                                existing.timestamp(),
                                existing.unreadCount() + 1
                        ),
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String normalize(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient username is required");
        }
        return value.trim().toLowerCase();
    }

    public record SendMessageRequest(String toUsername, String content) {
    }

    public record UserSummary(
            Long id,
            String username,
            String role,
            String displayName,
            String city,
            int unreadCount
    ) {
        public static UserSummary from(User user, List<UnreadConversationSummary> unreadConversations) {
            int unreadCount = unreadConversations.stream()
                    .filter(unread -> unread.username().equals(user.getUsername()))
                    .mapToInt(UnreadConversationSummary::unreadCount)
                    .findFirst()
                    .orElse(0);
            return new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.getDisplayName(), user.getCity(), unreadCount);
        }
    }

    public record MessageResponse(
            Long id,
            String senderUsername,
            String receiverUsername,
            String content,
            LocalDateTime timestamp,
            boolean read
    ) {
        public static MessageResponse from(Message message) {
            return new MessageResponse(
                    message.getId(),
                    message.getSender().getUsername(),
                    message.getReceiver().getUsername(),
                    message.getContent(),
                    message.getTimestamp(),
                    message.getReadAt() != null
            );
        }
    }

    public record UnreadConversationSummary(
            String username,
            String displayName,
            Long latestMessageId,
            String preview,
            LocalDateTime timestamp,
            int unreadCount
    ) {
    }

    public record InboxNotificationResponse(
            int totalUnreadCount,
            List<UnreadConversationSummary> conversations
    ) {
    }
}
