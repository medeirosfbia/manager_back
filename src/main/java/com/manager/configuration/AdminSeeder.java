package com.manager.configuration;

import com.manager.models.Role;
import com.manager.models.User;
import com.manager.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * Cria o admin padrão ao subir a aplicação, se ADMIN_PASSWORD estiver configurado.
 * Não altera usuários que já existem: um usuário comum chamado "admin" não é promovido.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${admin.default.username}") String username,
                       @Value("${admin.default.email}") String email,
                       @Value("${admin.default.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (password == null || password.isBlank()) {
            log.info("AdminSeeder: ADMIN_PASSWORD não configurado, admin padrão não será criado.");
            return;
        }

        Optional<User> existingUser = userRepository.findByUsername(username);
        if (existingUser.isPresent()) {
            if (existingUser.get().getRole() != Role.ADMIN) {
                log.warn("AdminSeeder: o usuário '{}' já existe e não é ADMIN; nada foi alterado.", username);
            }
            return;
        }

        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("AdminSeeder: o e-mail '{}' já está em uso; admin padrão não foi criado.", email);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setName("Administrador");
        admin.setLastname("AprovIA");
        admin.setPassword(passwordEncoder.encode(password));
        // birthday é obrigatório na entidade; data fixa só para a conta técnica
        admin.setBirthday(Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        admin.setRole(Role.ADMIN);

        // Uma falha aqui não deve impedir o serviço de login de subir
        try {
            userRepository.save(admin);
            log.info("AdminSeeder: admin padrão '{}' criado.", username);
        } catch (RuntimeException e) {
            log.error("AdminSeeder: não foi possível criar o admin padrão '{}'.", username, e);
        }
    }
}
