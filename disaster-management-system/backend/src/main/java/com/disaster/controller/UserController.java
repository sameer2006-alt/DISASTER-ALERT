package com.disaster.controller;

import com.disaster.dto.UserLocationResponse;
import com.disaster.dto.UserLocationUpdateRequest;
import com.disaster.model.User;
import com.disaster.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/me/location")
    public ResponseEntity<UserLocationResponse> updateMyLocation(
            Authentication authentication,
            @Valid @RequestBody UserLocationUpdateRequest req) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Full authentication is required");
        }

        String principalName = authentication.getName();
        User user = userRepository.findByUsername(principalName)
                .orElseGet(() -> userRepository.findByEmail(principalName)
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + principalName)));

        user.setLatitude(req.latitude());
        user.setLongitude(req.longitude());
        if (req.state() != null && !req.state().isBlank()) {
            user.setState(req.state().trim());
        }
        if (req.city() != null && !req.city().isBlank()) {
            user.setCity(req.city().trim());
        }
        if (req.location() != null && !req.location().isBlank()) {
            user.setLocation(req.location().trim());
        }
        user.syncGeo();
        userRepository.save(user);

        return ResponseEntity.ok(new UserLocationResponse(
                "Location updated successfully",
                user.getLatitude(),
                user.getLongitude(),
                user.getCity(),
                user.getState(),
                user.getLocation()
        ));
    }
}
