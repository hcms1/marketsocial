package com.example.marketsocial;

import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.MessageRepository;
import com.example.marketsocial.repository.PostRepository;
import com.example.marketsocial.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:hsqldb:mem:marketsocial-test",
        "spring.datasource.driverClassName=org.hsqldb.jdbc.JDBCDriver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.HSQLDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountManagementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void resetData() {
        messageRepository.deleteAll();
        postRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void firstRegistrationBootstrapsAdminAccount() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "founder",
                                  "password": "password123",
                                  "accountType": "USER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("founder"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void userCanChangeOwnPassword() throws Exception {
        User member = createUser("member", "USER");

        mockMvc.perform(post("/api/profiles/me/password")
                        .with(user(member.getUsername()).roles(member.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "newpassword123"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("member"))
                .get()
                .extracting(User::getPassword)
                .satisfies(encoded -> assertThat(passwordEncoder.matches("newpassword123", (String) encoded)).isTrue());
    }

    @Test
    void adminCanUpdateResetPasswordAndDeleteManagedUser() throws Exception {
        User admin = createUser("admin", "ADMIN");
        User managed = createUser("managed", "USER");

        mockMvc.perform(put("/api/users/{id}", managed.getId())
                        .with(user(admin.getUsername()).roles(admin.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Managed Seller",
                                  "role": "SELLER",
                                  "city": "Leeds",
                                  "bio": "Upgraded by admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Managed Seller"))
                .andExpect(jsonPath("$.role").value("SELLER"));

        mockMvc.perform(post("/api/users/{id}/password", managed.getId())
                        .with(user(admin.getUsername()).roles(admin.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "managedpass456"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("managed"))
                .get()
                .satisfies(user -> {
                    assertThat(user.getRole()).isEqualTo("SELLER");
                    assertThat(user.getCity()).isEqualTo("Leeds");
                    assertThat(passwordEncoder.matches("managedpass456", user.getPassword())).isTrue();
                });

        mockMvc.perform(delete("/api/users/{id}", managed.getId())
                        .with(user(admin.getUsername()).roles(admin.getRole())))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("managed")).isEmpty();
    }

    @Test
    void lastAdminCannotDeleteOwnAccount() throws Exception {
        User admin = createUser("root", "ADMIN");

        mockMvc.perform(delete("/api/profiles/me")
                        .with(user(admin.getUsername()).roles(admin.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByUsername("root")).isPresent();
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
