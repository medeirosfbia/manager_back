package com.manager.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.manager.models.Role;
import com.manager.models.User;
import com.manager.models.UserLogin;
import com.manager.repositories.UserRepository;
import com.manager.security.JwtAuthFilter;
import com.manager.security.JwtService;
import com.manager.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;


    private User user;

    // private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(1);
        user.setUsername("adminteste");
        user.setEmail("adminteste@email.com");
        user.setName("adminteste");
        user.setLastname("Admin");
        user.setPassword("1234");
        user.setBirthday(new Date());

        Mockito.when(userService.canManage(any())).thenReturn(true);
    }

    // A serialização omite a senha (WRITE_ONLY), então ela é adicionada como um cliente enviaria
    private String userJsonWithPassword() {
        return mapper.valueToTree(user)
                .<ObjectNode>deepCopy()
                .put("password", "1234")
                .toString();
    }

    // -------------------------- SIGN UP ------------------------------

    @Test
    void testSignupSuccess() throws Exception {
        Mockito.when(userService.create(any(User.class)))
                .thenReturn(Optional.of(user));

        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJsonWithPassword()))
                .andExpect(status().isOk());
    }

    @Test
    void testSignupIgnoresRoleFromRequestBody() throws Exception {
        Mockito.when(userService.create(any(User.class)))
                .thenReturn(Optional.of(user));

        String body = "{\"username\":\"hacker\",\"email\":\"h@email.com\",\"name\":\"H\","
                + "\"lastname\":\"H\",\"password\":\"1234\",\"birthday\":\"2000-01-01\","
                + "\"role\":\"ADMIN\"}";

        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userService).create(captor.capture());
        assertEquals(Role.USER, captor.getValue().getRole());
    }

    @Test
    void testSignupConflict() throws Exception {
        Mockito.when(userService.create(any(User.class)))
                .thenThrow(new Exception());

        mockMvc.perform(post("/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJsonWithPassword()))
                .andExpect(status().isConflict());
    }

    // -------------------------- LOGIN --------------------------------

    @Test
    void testLoginSuccess() throws Exception {
        UserLogin login = new UserLogin();
        login.setUsername("adminteste");
        login.setPassword("1234");

        Mockito.when(userService.login(any()))
                .thenReturn(Optional.of(login));

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk());
    }

    @Test
    void testLoginUnauthorized() throws Exception {
        Mockito.when(userService.login(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UserLogin())))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------ GET BY ID ------------------------------

    @Test
    void testGetById() throws Exception {
        Mockito.when(userService.getById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetByIdForbiddenForOtherUser() throws Exception {
        Mockito.when(userService.canManage(1)).thenReturn(false);

        mockMvc.perform(get("/user/1"))
                .andExpect(status().isForbidden());

        Mockito.verify(userService, Mockito.never()).getById(any());
    }

    @Test
    void testResponsesNeverContainPassword() throws Exception {
        user.setPassword("$2a$10$hash");
        Mockito.when(userService.getById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("adminteste"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // -------------------------- UPDATE ------------------------------

    @Test
    void testUpdateSuccess() throws Exception {
        Mockito.when(userService.update(any(User.class))).thenReturn(Optional.of(user));

        mockMvc.perform(put("/user/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJsonWithPassword()))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateNotFound() throws Exception {
        Mockito.when(userService.update(any(User.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/user/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJsonWithPassword()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateForbiddenForOtherUser() throws Exception {
        Mockito.when(userService.canManage(1)).thenReturn(false);

        mockMvc.perform(put("/user/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"username\":\"adminteste\",\"email\":\"a@email.com\","
                                + "\"name\":\"A\",\"lastname\":\"A\",\"password\":\"1234\",\"birthday\":\"2000-01-01\"}"))
                .andExpect(status().isForbidden());

        Mockito.verify(userService, Mockito.never()).update(any());
    }

//    // ----------------------- UPDATE PASSWORD --------------------------
//
//    @Test
//    void testUpdatePassword() throws Exception {
//        Mockito.when(userService.updatePassword(eq("email"), eq("senha")))
//                .thenReturn(Optional.of(user));
//
//        mockMvc.perform(patch("/user/updatePassword")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content("{\"email\":\"email\", \"password\":\"senha\"}"))
//                .andExpect(status().isOk());
//    }

    // ----------------------------- DELETE -----------------------------

    @Test
    void testDeleteSuccess() throws Exception {
        Mockito.when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        mockMvc.perform(delete("/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteForbiddenForOtherUser() throws Exception {
        Mockito.when(userService.canManage(1)).thenReturn(false);

        mockMvc.perform(delete("/user/1"))
                .andExpect(status().isForbidden());

        Mockito.verify(userRepository, Mockito.never()).deleteById(any());
    }

    @Test
    void testDeleteNotFound() throws Exception {
        Mockito.when(userRepository.findById(1))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/user/1"))
                .andExpect(status().isNotFound());
    }
}
