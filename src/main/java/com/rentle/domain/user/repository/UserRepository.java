package com.rentle.domain.user.repository;

import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumberOrEmail(String phoneNumber, String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    Page<User> findByStatus(UserStatus status, Pageable pageable);
}
