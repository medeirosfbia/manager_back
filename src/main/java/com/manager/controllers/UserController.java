package com.manager.controllers;

import com.manager.models.User;
import com.manager.models.UserLogin;
import com.manager.repositories.UserRepository;
import com.manager.services.ProfilePictureService;
import com.manager.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfilePictureService profilePictureService;

    @PostMapping("/signup")
    public ResponseEntity<Optional<User>> post(@RequestBody User user){
        try {
            return ResponseEntity.status(HttpStatus.OK).body(userService.create(user));
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

    }

    @PostMapping("/login")
    public ResponseEntity<UserLogin> auth(@RequestBody Optional<UserLogin> user){
        return userService.login(user)
                .map(userAutenticanted -> ResponseEntity.status(HttpStatus.OK).body(userAutenticanted))
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<User>>getUserById(@PathVariable Integer id){
        if (!userService.canManage(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/update")
    public  ResponseEntity<User> update(@Valid @RequestBody User user){
            if (!userService.canManage(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return userService.update(user)
                    .map(updateUser -> ResponseEntity.status(HttpStatus.OK)
                            .body(updateUser))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

    }

//    @PatchMapping("/updatePassword")
//    public  ResponseEntity<Optional<User>> updatePassword(@RequestBody Map<String, String> user){
//        return ResponseEntity.status(HttpStatus.OK).body(userService.updatePassword(user.get("email"), user.get("password")));
//    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id){
        // Checado antes do 404 para não revelar quais ids existem
        if (!userService.canManage(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Optional<User> user = userRepository.findById(id);
        if (user.isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        userRepository.deleteById(id);
        profilePictureService.deleteStoredPicture(id, user.get().getPicture());
    }

}