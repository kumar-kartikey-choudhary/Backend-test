package com.pratikdairy.user.repository;

import com.pratikdairy.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User , String> {

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    /** Accounts that still exist - deleted ones stay in the table only as anonymised tombstones. */
    List<User> findByDeletedFalse();

    List<User> findByRoleIgnoreCaseAndDeletedFalse(String role);

    long countByRoleIgnoreCaseAndDeletedFalse(String role);
}
