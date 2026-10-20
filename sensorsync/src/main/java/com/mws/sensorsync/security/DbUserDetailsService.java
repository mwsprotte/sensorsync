package com.mws.sensorsync.security;

import com.mws.sensorsync.model.UserHuman;
import com.mws.sensorsync.repositories.UserHumanRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserHumanRepository users;

    public DbUserDetailsService(UserHumanRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        UserHuman u = users.findFirstByLoginOrderByIdDesc(login)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado"));
        return User.withUsername(u.getLogin())
                .password(u.getPassword() == null ? "" : u.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))
                .disabled(!u.isEnabled())
                .build();
    }
}
