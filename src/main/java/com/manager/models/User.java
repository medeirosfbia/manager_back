package com.manager.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "tb_user")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @NotNull
    private String username;
    @NotNull
    private String email;
    @NotNull
    private String name;
    @NotNull
    private String lastname;
    @NotNull
    private String password;
    @NotNull
    @JsonFormat(pattern="yyyy-MM-dd")
    private Date birthday;
    private String picture;

    // READ_ONLY: o papel aparece nas respostas, mas é ignorado no JSON recebido
    // (signup/update), para ninguém conseguir se cadastrar como ADMIN.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'USER'")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Role role = Role.USER;
}
