package com.example.marketsocial;

import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.MessageRepository;
import com.example.marketsocial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:marketsocial-test-messages",
        "spring.datasource.driverClassName=org.hsqldb.jdbc.JDBCDriver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.HSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.notifications.email.enabled=false"
})
class MessageNotificationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetData() {
        messageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unreadNotificationsAppearAndClearWhenConversationIsOpened() throws Exception {
        User sender = createUser("sender", "USER");
        User receiver = createUser("receiver", "USER");

        mockMvc.perform(post("/api/messages")
                        .with(user(sender.getUsername()).roles(sender.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toUsername": "receiver",
                                  "content": "hello there"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.read").value(false));

        mockMvc.perform(get("/api/messages/notifications")
                        .with(user(receiver.getUsername()).roles(receiver.getRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnreadCount").value(1))
                .andExpect(jsonPath("$.conversations[0].username").value("sender"))
                .andExpect(jsonPath("$.conversations[0].unreadCount").value(1));

        mockMvc.perform(get("/api/messages/sender")
                        .with(user(receiver.getUsername()).roles(receiver.getRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderUsername").value("sender"));

        mockMvc.perform(get("/api/messages/notifications")
                        .with(user(receiver.getUsername()).roles(receiver.getRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnreadCount").value(0));
    }

    private User createUser(String username, String role) {
        return userRepository.save(new User(
                null,
                username,
                passwordEncoder.encode("password123"),
                role,
                username,
                "",
                "",
                null,
                false
        ));
    }
}
