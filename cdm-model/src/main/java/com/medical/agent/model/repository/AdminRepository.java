package com.medical.agent.model.repository;

import com.medical.agent.model.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsername(String username);
    Optional<Admin> findByName(String name);

    @Query(value = "SELECT COALESCE(must_change_password, 1) FROM admin WHERE username = :username LIMIT 1", nativeQuery = true)
    Integer findMustChangePasswordFlagByUsername(@Param("username") String username);
}
