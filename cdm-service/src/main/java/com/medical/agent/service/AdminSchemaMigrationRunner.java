package com.medical.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Order(0)
@Component
public class AdminSchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public AdminSchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'admin' AND column_name = 'must_change_password'",
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE admin ADD COLUMN must_change_password TINYINT(1) NULL DEFAULT 1");
            log.info("[AdminSchemaMigrationRunner] added admin.must_change_password column");
        } else {
            String columnType = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'admin' AND column_name = 'must_change_password'",
                    String.class
            );
            if (columnType != null && !"tinyint".equalsIgnoreCase(columnType)) {
                jdbcTemplate.execute("ALTER TABLE admin MODIFY COLUMN must_change_password TINYINT(1) NULL DEFAULT 1");
                log.info("[AdminSchemaMigrationRunner] normalized admin.must_change_password column type to TINYINT(1)");
            }
        }

        jdbcTemplate.update("UPDATE admin SET must_change_password = 0 WHERE must_change_password IS NULL");
    }
}
