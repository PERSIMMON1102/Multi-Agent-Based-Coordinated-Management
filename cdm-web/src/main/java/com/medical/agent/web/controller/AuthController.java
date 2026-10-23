package com.medical.agent.web.controller;

import com.medical.agent.service.AuthService;
import com.medical.agent.web.security.AuthenticatedUser;
import com.medical.agent.web.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    @Autowired
    public AuthController(AuthService authService, JwtTokenService jwtTokenService) {
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
    }

    AuthController(AuthService authService) {
        this(authService, null);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = authService.login(
                text(body.get("username")),
                text(body.get("password")),
                text(body.get("role")));
        AuthenticatedUser user = new AuthenticatedUser(
                userId(result),
                text(result.get("username")),
                text(result.get("role"))
        );
        if (jwtTokenService != null) {
            result.put("token", jwtTokenService.generateToken(user, Map.of()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(authService.registerPatient(
                text(body.get("username")),
                text(body.get("password")),
                text(body.get("name"))));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(authService.getAuthConfig());
    }

    @GetMapping("/doctors/list")
    public ResponseEntity<List<Map<String, Object>>> listDoctors() {
        return ResponseEntity.ok(authService.listDoctors());
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Long userId(Map<String, Object> payload) {
        Object role = payload.get("role");
        if ("admin".equals(role)) {
            return toLong(payload.get("adminId"));
        }
        if ("doctor".equals(role)) {
            return toLong(payload.get("doctorId"));
        }
        if ("patient".equals(role)) {
            return toLong(payload.get("patientId"));
        }
        throw new IllegalStateException("Unknown login role");
    }

    private Long toLong(Object value) {
        if (value == null) {
            throw new IllegalStateException("Missing user id for login payload");
        }
        return Long.valueOf(value.toString());
    }
}
