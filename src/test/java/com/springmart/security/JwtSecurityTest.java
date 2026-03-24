package com.springmart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmart.entity.User;
import com.springmart.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class JwtSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String validAdminToken;
    private String validUserToken;

    @BeforeEach
    void setUp() {
        // Clear repository to prevent duplicate key issues during tests if any
        userRepository.deleteAll();

        // Create Admin User
        User admin = new User();
        admin.setUserName("adminUser");
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setRole("ROLE_ADMIN");
        userRepository.save(admin);

        this.validAdminToken = jwtTokenProvider.generateToken(admin.getUserName(), admin.getRole());

        User normalUser = new User();
        normalUser.setUserName("generalUser");
        normalUser.setPassword(passwordEncoder.encode("password"));
        normalUser.setRole("ROLE_USER");
        userRepository.save(normalUser);

        this.validUserToken = jwtTokenProvider.generateToken(normalUser.getUserName(), normalUser.getRole());
    }

    @Test
    void testAccessWithValidUserToken() throws Exception {
        // Normal user should be able to access GET /api/products
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + validUserToken))
                .andExpect(status().isOk());
    }

    @Test
    void testAccessWithValidAdminToken() throws Exception {
        // Admin user should be able to access GET /api/products
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + validAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testRoleBasedAccessRestriction_UserAccessingAdminEndpoint() throws Exception {
        String newProductJson = "{\"name\":\"Sample Product\",\"price\":1000,\"stockQuantity\":10,\"description\":\"Test\"}";

        // Normal user trying to access POST /api/products (Requires ROLE_ADMIN) -> Expected 403 Forbidden
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + validUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(newProductJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("権限エラー"));
    }

    @Test
    void testInvalidTokenErrorHandling() throws Exception {
        String invalidToken = "invalid.token.string.here";

        // Accessing with invalid token -> Expected 401 Unauthorized
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("認証エラー"));
    }

    @Test
    void testExpiredTokenErrorHandling() throws Exception {
        // Intentionally create an expired token
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .claim("role", "ROLE_USER")
                .issuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)) // Issued 1 day ago
                .expiration(new Date(System.currentTimeMillis() - 1000 * 60)) // Expired 1 min ago
                .signWith(key)
                .compact();

        // Accessing with expired token -> Expected 401 Unauthorized
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("認証エラー"))
                .andExpect(jsonPath("$.message").value("有効期限が切れています。再度ログインしてください。"));
    }
}
