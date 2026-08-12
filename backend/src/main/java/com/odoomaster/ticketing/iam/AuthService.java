package com.odoomaster.ticketing.iam;

import com.odoomaster.ticketing.iam.internal.Role;
import com.odoomaster.ticketing.iam.internal.User;
import com.odoomaster.ticketing.iam.internal.UserStatus;
import com.odoomaster.ticketing.iam.AuthDtos.*;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.iam.internal.RoleRepository;
import com.odoomaster.ticketing.iam.internal.UserRepository;
import com.odoomaster.ticketing.iam.internal.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.time.Instant;

/**
 * Authentication service: registers users (BCrypt-hashed credentials) and logs them in,
 * issuing a signed JWT via {@link JwtService}.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = User.normaliseEmail(req.email());
        if (users.existsByEmail(email)) {
            throw new AppException("EMAIL_ALREADY_REGISTERED",
                    "Email đã được đăng ký.", HttpStatus.CONFLICT);
        }
        Role userRole = roles.findByName("USER")
                .orElseThrow(() -> new AppException("ROLE_NOT_SEEDED", "USER role missing.", HttpStatus.INTERNAL_SERVER_ERROR));
        Set<Role> roleSet = new HashSet<>();
        roleSet.add(userRole);

        User u = User.register(email, encoder.encode(req.password()),
                req.fullName(), req.phone(), roleSet, Instant.now());
        users.save(u);
        return issue(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User u = users.findByEmail(email)
                .orElseThrow(() -> new AppException("INVALID_CREDENTIALS",
                        "Email hoặc mật khẩu không đúng.", HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new AppException("INVALID_CREDENTIALS",
                    "Email hoặc mật khẩu không đúng.", HttpStatus.UNAUTHORIZED);
        }
        if (u.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("ACCOUNT_INACTIVE", "Account is not active.", HttpStatus.FORBIDDEN);
        }
        return issue(u);
    }

    private AuthResponse issue(User u) {
        Set<String> roleNames = u.getRoleNames();
        String token = jwt.issue(u.getId(), u.getEmail(), roleNames);
        UserResponse ur = new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), roleNames);
        return new AuthResponse(token, jwt.getTtlMinutes(), ur);
    }
}
