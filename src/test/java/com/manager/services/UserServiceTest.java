package com.manager.services;

import com.manager.models.Role;
import com.manager.models.User;
import com.manager.models.UserLogin;
import com.manager.repositories.UserRepository;
import com.manager.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId(1);
        user.setUsername("adminteste");
        user.setEmail("adminteste@email.com");
        user.setName("adminteste");
        user.setLastname("Deyvison");
        user.setPassword("1234");
        user.setBirthday(new Date());
    }

    // ---------------------------- CREATE ----------------------------------

    @Test
    void testCreateSuccess() throws Exception {
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("adminteste@email.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);

        Optional<User> created = userService.create(user);

        assertTrue(created.isPresent());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void testCreateUserAlreadyExists() {
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.of(user));

        assertThrows(Exception.class, () -> userService.create(user));
    }

    @Test
    void testCreateAlwaysCreatesRegularUser() throws Exception {
        user.setRole(Role.ADMIN);
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("adminteste@email.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> created = userService.create(user);

        assertEquals(Role.USER, created.get().getRole());
    }

    // ---------------------------- UPDATE ----------------------------------

    @Test
    void testUpdateSuccess() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        Optional<User> updated = userService.update(user);

        assertTrue(updated.isPresent());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testUpdateUsernameAlreadyExists() {
        User other = new User();
        other.setId(2);
        other.setUsername("adminteste");

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.of(other));

        assertThrows(ResponseStatusException.class, () -> userService.update(user));
    }

    @Test
    void testUpdateKeepsRoleFromDatabase() {
        User stored = new User();
        stored.setId(1);
        stored.setUsername("adminteste");
        stored.setRole(Role.ADMIN);

        // Simula o JSON recebido: role é ignorado e fica com o padrão USER
        user.setRole(Role.USER);
        when(userRepository.findById(1)).thenReturn(Optional.of(stored));
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.of(stored));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> updated = userService.update(user);

        assertEquals(Role.ADMIN, updated.get().getRole());
    }

    // ----------------------- UPDATE PASSWORD -------------------------------

    @Test
    void testUpdatePasswordSuccess() {
        when(userRepository.findByEmail("adminteste@email.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        Optional<User> result = userService.updatePassword(user.getEmail(), "novaSenha");

        assertTrue(result.isPresent());
        verify(userRepository).save(any());
    }

    @Test
    void testUpdatePasswordEmailNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Optional<User> result = userService.updatePassword("nao@existe.com", "1234");

        assertFalse(result.isPresent());
    }

    // ---------------------------- LOGIN ------------------------------------

    @Test
    void testLoginSuccess() {
        UserLogin login = new UserLogin();
        login.setUsername("adminteste");
        login.setPassword("1234");

        Authentication mockAuth = mock(Authentication.class);

        when(mockAuth.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("adminteste", Role.USER)).thenReturn("TOKEN123");

        Optional<UserLogin> result = userService.login(Optional.of(login));

        assertTrue(result.isPresent());
        assertEquals("Bearer TOKEN123", result.get().getToken());
        assertEquals(Role.USER, result.get().getRole());
    }

    @Test
    void testLoginAdminPutsRoleInToken() {
        user.setRole(Role.ADMIN);
        UserLogin login = new UserLogin();
        login.setUsername("adminteste");
        login.setPassword("1234");

        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(userRepository.findByUsername("adminteste")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("adminteste", Role.ADMIN)).thenReturn("TOKEN_ADMIN");

        Optional<UserLogin> result = userService.login(Optional.of(login));

        assertEquals("Bearer TOKEN_ADMIN", result.get().getToken());
        assertEquals(Role.ADMIN, result.get().getRole());
    }

    @Test
    void testLoginInvalidCredentials() {
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.isAuthenticated()).thenReturn(false);

        when(authenticationManager.authenticate(any())).thenReturn(mockAuth);

        Optional<UserLogin> result = userService.login(Optional.of(new UserLogin()));

        assertFalse(result.isPresent());
    }
}
