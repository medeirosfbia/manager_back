package com.manager.services;

import com.manager.models.Role;
import com.manager.models.User;
import com.manager.models.UserLogin;
import com.manager.repositories.UserRepository;
import com.manager.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    public Optional<User> create(User user) throws Exception {
        Optional<User> existingUsername = userRepository.findByUsername(user.getUsername());
        Optional<User> existingEmail = userRepository.findByEmail(user.getEmail());

        if(existingUsername.isPresent() || existingEmail.isPresent()){
            throw new Exception();
        }else {
            user.setPassword(criptPassword(user.getPassword()));
            // Cadastro público sempre cria usuário comum; admin só pelo AdminSeeder ou banco
            user.setRole(Role.USER);
            return Optional.of(userRepository.save(user));
        }
    }

    public Optional<User> update(User user) {
        Optional<User> existingUser = userRepository.findById(user.getId());
        if (existingUser.isPresent()) {
            Optional<User> searchUsername = userRepository.findByUsername(user.getUsername());
//            Optional<User> searchEmail = userRepository.findByEmail(user.getEmail());
            if ((searchUsername.isPresent()) && (searchUsername.get().getId() != user.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Existented User!", null);
            }
            user.setPassword(criptPassword(user.getPassword()));
            // O papel não vem no JSON; mantém o que está no banco
            user.setRole(existingUser.get().getRole());
            // A foto também não vem no JSON; só muda por /user/{id}/picture
            user.setPicture(existingUser.get().getPicture());

            return Optional.ofNullable(userRepository.save(user));

        }
        return Optional.empty();
    }

    public Optional<User> updatePassword(String email, String password) {
        Optional<User> searchUser = userRepository.findByEmail(email);

        if (searchUser.isPresent()) {
            System.out.println("Email encontrado!");

            if (searchUser.get().getPassword() != criptPassword(password)) {
                searchUser.get().setPassword(criptPassword(password));
                return Optional.ofNullable(userRepository.save(searchUser.get()));
            }

        }
        return Optional.empty();
    }


    public Optional<UserLogin> login(Optional<UserLogin> userLogin) {

        var crendencials = new UsernamePasswordAuthenticationToken(userLogin.get().getUsername(), userLogin.get().getPassword());

        Authentication authentication = authenticationManager.authenticate(crendencials);

        if (authentication.isAuthenticated()){

            Optional<User> user = userRepository.findByUsername(userLogin.get().getUsername());

            if (user.isPresent()){
                userLogin.get().setId(user.get().getId());
                userLogin.get().setName(user.get().getName());
                userLogin.get().setPicture(user.get().getPicture());
                userLogin.get().setToken(createToken(user.get().getUsername(), user.get().getRole()));
                userLogin.get().setRole(user.get().getRole());
                userLogin.get().setEmail(user.get().getEmail());
                userLogin.get().setLastname(user.get().getLastname());
                userLogin.get().setBirthday(user.get().getBirthday());
                userLogin.get().setPassword("");


                return userLogin;
            }
        }
        return Optional.empty();
    }

    private String criptPassword(String password) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode(password);
    }

    private String createToken(String username, Role role) {
        return "Bearer " + jwtService.generateToken(username, role);
    }

    public Optional<User> getById(Integer id) {
        return userRepository.findById(id);
    }

    /** O usuário autenticado pode gerenciar a conta se for ela mesma ou se for ADMIN. */
    public boolean canManage(Integer userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (userId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            return true;
        }

        return userRepository.findByUsername(authentication.getName())
                .map(currentUser -> userId.equals(currentUser.getId()))
                .orElse(false);
    }
}
