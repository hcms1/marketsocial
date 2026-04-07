package com.example.marketsocial.controller;

import com.example.marketsocial.model.Post;
import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.PostRepository;
import com.example.marketsocial.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostController(PostRepository postRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PostResponse> allPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PostResponse::from)
                .toList();
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<PostResponse> myPosts(Authentication authentication) {
        User user = requireUser(authentication);
        return postRepository.findByAuthorOrderByCreatedAtDesc(user).stream()
                .map(PostResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@RequestBody CreatePostRequest request, Authentication authentication) {
        User author = requireUser(authentication);
        ensureSeller(author);
        String content = normalizeText(request.content(), 1200);
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post content is required");
        }

        Post post = new Post(
                null,
                author,
                content,
                normalizeText(request.imageUrl(), 500),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(postRepository.save(post)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id, Authentication authentication) {
        User author = requireUser(authentication);
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own posts");
        }

        postRepository.delete(post);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String normalizeText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private void ensureSeller(User user) {
        if (!user.canSell()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Switch your account to seller before publishing posts");
        }
    }

    public record CreatePostRequest(String content, String imageUrl) {
    }

    public record PostResponse(
            Long id,
            String content,
            String imageUrl,
            String authorUsername,
            String authorDisplayName,
            String authorCity,
            LocalDateTime createdAt
    ) {
        public static PostResponse from(Post post) {
            return new PostResponse(
                    post.getId(),
                    post.getContent(),
                    post.getImageUrl(),
                    post.getAuthor().getUsername(),
                    post.getAuthor().getDisplayName(),
                    post.getAuthor().getCity(),
                    post.getCreatedAt()
            );
        }
    }
}
