package com.example.marketsocial.repository;

import com.example.marketsocial.model.Message;
import com.example.marketsocial.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySender(User sender);
    List<Message> findByReceiver(User receiver);
    void deleteBySenderOrReceiver(User sender, User receiver);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    @Query("""
            select m
            from Message m
            where (m.sender = :userA and m.receiver = :userB)
               or (m.sender = :userB and m.receiver = :userA)
            order by m.timestamp asc
            """)
    List<Message> findConversation(User userA, User userB);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<Message> findByReceiverAndReadAtIsNullOrderByTimestampDesc(User receiver);

    @Modifying
    @Query("""
            update Message m
            set m.readAt = :readAt
            where m.receiver = :receiver
              and m.sender = :sender
              and m.readAt is null
            """)
    int markConversationAsRead(User receiver, User sender, LocalDateTime readAt);
}
