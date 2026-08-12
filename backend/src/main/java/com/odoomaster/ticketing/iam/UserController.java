package com.odoomaster.ticketing.iam;

import com.odoomaster.ticketing.iam.internal.User;
import com.odoomaster.ticketing.iam.AuthDtos.UserResponse;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.iam.internal.UserRepository;
import com.odoomaster.ticketing.shared.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the current user's profile under {@code /v1/users}.
 */
@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository users;
    private final CurrentUser current;

    public UserController(UserRepository users, CurrentUser current) {
        this.users = users;
        this.current = current;
    }

    @GetMapping("/me")
    public UserResponse me() {
        Long uid = current.require().userId();
        User u = users.findById(uid).orElseThrow(() ->
                new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRoleNames());
    }

    @PutMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        Long uid = current.require().userId();
        User u = users.findById(uid).orElseThrow(() ->
                new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        u.updateProfile(req.fullName(), req.phone());
        users.save(u);
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRoleNames());
    }

    public record UpdateProfileRequest(@Size(max = 255) String fullName, @Size(max = 50) String phone) {}
}
