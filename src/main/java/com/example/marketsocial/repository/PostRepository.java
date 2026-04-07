package com.example.marketsocial.repository;

import com.example.marketsocial.model.Post;
import com.example.marketsocial.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findByAuthorOrderByCreatedAtDesc(User author);
    void deleteByAuthor(User author);
}
