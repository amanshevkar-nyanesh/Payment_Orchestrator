package com.payment.orchestrator.security;

import com.payment.orchestrator.domain.UserRole;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InMemoryUserDetailsService implements UserDetailsService {

    private final Map<String, UserPrincipal> users;
    private final PasswordEncoder passwordEncoder;

    public InMemoryUserDetailsService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.users = Map.of(
                "merchant_m123", UserPrincipal.builder()
                        .username("merchant_m123")
                        .password(passwordEncoder.encode("password"))
                        .role(UserRole.MERCHANT)
                        .merchantId("M123")
                        .build(),
                "merchant_m456", UserPrincipal.builder()
                        .username("merchant_m456")
                        .password(passwordEncoder.encode("password"))
                        .role(UserRole.MERCHANT)
                        .merchantId("M456")
                        .build(),
                "admin", UserPrincipal.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .role(UserRole.ADMIN)
                        .merchantId(null)
                        .build()
        );
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserPrincipal user = users.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return user;
    }
}
