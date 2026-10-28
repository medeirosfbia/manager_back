package com.manager.configuration;

import com.manager.models.Role;
import com.manager.models.User;
import com.manager.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminSeeder seeder(String password) {
        return new AdminSeeder(userRepository, passwordEncoder, "admin", "admin@aprovia.com", password);
    }

    @Test
    void testCreatesAdminWithEncodedPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@aprovia.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha-forte")).thenReturn("HASH");

        seeder("senha-forte").run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();
        assertEquals("admin", admin.getUsername());
        assertEquals("admin@aprovia.com", admin.getEmail());
        assertEquals("HASH", admin.getPassword());
        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    void testSkipsWhenPasswordIsNotConfigured() {
        seeder("").run();

        verifyNoInteractions(userRepository);
    }

    @Test
    void testDoesNotPromoteExistingRegularUser() {
        User existing = new User();
        existing.setUsername("admin");
        existing.setRole(Role.USER);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        seeder("senha-forte").run();

        verify(userRepository, never()).save(any());
        assertEquals(Role.USER, existing.getRole());
    }

    @Test
    void testSkipsWhenEmailIsTaken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@aprovia.com")).thenReturn(Optional.of(new User()));

        seeder("senha-forte").run();

        verify(userRepository, never()).save(any());
    }
}
