package com.medical.agent.service;

import com.medical.agent.model.entity.Admin;
import com.medical.agent.model.repository.AdminRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Order(1)
@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordService passwordService;

    @Value("${bootstrap.admin.enabled:true}")
    private boolean enabled;

    @Value("${bootstrap.admin.username:}")
    private String username;

    @Value("${bootstrap.admin.password:}")
    private String password;

    @Value("${bootstrap.admin.name:系统管理员}")
    private String name;

    public InitialAdminBootstrap(AdminRepository adminRepository,
                                 PasswordService passwordService) {
        this.adminRepository = adminRepository;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[InitialAdminBootstrap] bootstrap disabled, skipping initial admin creation");
            return;
        }

        if (adminRepository.count() > 0) {
            return;
        }

        if (isBlank(username) || isBlank(password)) {
            throw new IllegalStateException(
                    "No admin account exists and bootstrap.admin.username / bootstrap.admin.password are not configured");
        }
        String normalizedPassword = passwordService.encodeRequiredStrongPassword(password, "bootstrap admin password is required");

        Admin admin = Admin.builder()
                .username(username.trim())
                .password(normalizedPassword)
                .name(isBlank(name) ? "系统管理员" : name.trim())
                .mustChangePassword(true)
                .build();
        adminRepository.save(admin);

        log.info("[InitialAdminBootstrap] initial admin created: username={}", admin.getUsername());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
