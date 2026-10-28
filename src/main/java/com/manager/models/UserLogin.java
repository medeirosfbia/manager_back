package com.manager.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter @Setter @NoArgsConstructor
public class UserLogin {

    private Integer id;
    private String username;
    private String password;
    private String token;
    private String name;
    private String picture;
    private String lastname;
    private String email;
    @JsonFormat(pattern="yyyy-MM-dd")
    private Date birthday;
    private Role role;

}
