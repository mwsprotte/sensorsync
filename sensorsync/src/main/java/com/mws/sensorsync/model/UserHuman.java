package com.mws.sensorsync.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "user_human")
public class UserHuman implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 500)
    private String firstName;

    @Column(length = 254)
    private String lastName;

    @Column(length = 254, unique = true)
    private String login;

    @Column(length = 254)
    private String email;

    /** Hash BCrypt. Nunca e serializado em respostas da API. */
    @JsonIgnore
    @Column(length = 254)
    private String password;

    /** Nulo apenas em bancos antigos; a migracao preenche. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Role role;

    /** Nulo apenas em bancos antigos; tratado como true. */
    @Column
    private Boolean enabled;

    public UserHuman() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role == null ? Role.USER : role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled == null || enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @JsonIgnore
    public boolean isAdmin() { return getRole() == Role.ADMIN; }

    /** Usado pela migracao para saber se o registro veio de um banco antigo. */
    @JsonIgnore
    public Role getRawRole() { return role; }
}
