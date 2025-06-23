package com.utochkin.orderservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "users")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subId", unique = true)
    @NotEmpty
    private String subId;

    @Column(name = "username", unique = true)
    @NotEmpty
    private String username;

    @Column(name = "firstName")
    @NotEmpty
    private String firstName;

    @Column(name = "lastName")
    @NotEmpty
    private String lastName;

    @Column(name = "email", unique = true)
    @Email
    private String email;

    @Enumerated(value = EnumType.STRING)
    private Role role;

    @Builder.Default
    @OneToMany(mappedBy = "user")
    private List<Order> orders = new LinkedList<>();
}
