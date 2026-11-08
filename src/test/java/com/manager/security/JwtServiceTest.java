package com.manager.security;

import com.manager.models.Role;
import com.manager.models.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            Base64.getEncoder().encodeToString("chave-de-teste-com-pelo-menos-32-bytes!!".getBytes()));

    @Test
    void testTokenCarriesUsernameAndRole() {
        String token = jwtService.generateToken("adminteste", Role.ADMIN);

        assertEquals("adminteste", jwtService.extractUsename(token));
        assertEquals("ADMIN", jwtService.extractClaim(token, (Claims claims) -> claims.get("role", String.class)));
    }

    @Test
    void testUserDetailsHasRoleAuthority() {
        User user = new User();
        user.setUsername("adminteste");
        user.setRole(Role.ADMIN);

        UserDetailsImpl details = new UserDetailsImpl(user);

        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
    }
}
