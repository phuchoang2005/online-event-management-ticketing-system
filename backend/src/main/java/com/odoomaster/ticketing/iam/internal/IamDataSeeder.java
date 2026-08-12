package com.odoomaster.ticketing.iam.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.time.Instant;

/**
 * Seeds the demo users (and their roles) for the {@code iam} module. Runs first
 * so downstream
 * seeders (e.g. notifications) can resolve the demo user.
 */
@Component
@Order(1)
public class IamDataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(IamDataSeeder.class);

  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder encoder;

  public IamDataSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    ensureUser("demo@dede.test", "demo1234", "Người Dùng", "0900000000", "USER");
    ensureUser("admin@dede.test", "admin1234", "Quản Trị Viên", "0900000001", "ADMIN");
    ensureUser("organizer@dede.test", "org12345", "Ban Tổ Chức", "0900000002", "ORGANIZER");
  }

  private User ensureUser(String email, String password, String fullName, String phone, String role) {
    return users.findByEmail(email).orElseGet(() -> {
      Role r = roles.findByName(role).orElseGet(() -> roles.save(Role.named(role)));
      Set<Role> roleSet = new HashSet<>();
      roleSet.add(r);
      User u = users.save(User.register(email, encoder.encode(password), fullName, phone,
          roleSet, Instant.now()));
      log.info("Seeded {} user {} / {}", role, email, password);
      return u;
    });
  }
}
