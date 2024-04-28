package com.mws.sensorsync.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

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

    @Column(length = 254)
    private String login;
    
    @Column(length = 254)
    private String email;

    @Column(length = 254)
    private String password;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserHuman userHuman = (UserHuman) o;
        return id == userHuman.id && Objects.equals(firstName, userHuman.firstName) && Objects.equals(lastName, userHuman.lastName) && Objects.equals(login, userHuman.login) && Objects.equals(email, userHuman.email) && Objects.equals(password, userHuman.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, login, email, password);
    }
}
