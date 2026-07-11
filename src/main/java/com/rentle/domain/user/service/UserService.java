package com.rentle.domain.user.service;

import com.rentle.domain.user.dto.PublicProfileResponse;
import com.rentle.domain.user.dto.UpdateProfileRequest;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.KycDetailRepository;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.storage.ImageValidator;
import com.rentle.shared.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class UserService {

    private static final long PROFILE_PHOTO_MAX_BYTES = 2L * 1024 * 1024;

    private final UserRepository userRepository;
    private final KycDetailRepository kycRepository;
    private final StorageService storageService;

    public UserService(UserRepository userRepository,
                       KycDetailRepository kycRepository,
                       StorageService storageService) {
        this.userRepository = userRepository;
        this.kycRepository = kycRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(UUID userId) {
        User user = getUser(userId);
        String kycStatus = kycRepository.findByUserId(userId)
                .map(k -> k.getStatus().name()).orElse(null);
        return UserProfileResponse.from(user, kycStatus);
    }

    public PublicProfileResponse getPublicProfile(UUID userId) {
        return PublicProfileResponse.from(getUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = getUser(userId);
        if (req.fullName() != null && !req.fullName().isBlank() && !req.fullName().equals(user.getFullName())) {
            // Once identity is verified the name is the authoritative KYC name.
            if (Boolean.TRUE.equals(user.getCitizenshipVerified())) {
                throw new RentleException("Your verified name cannot be changed");
            }
            user.setFullName(req.fullName());
        }
        if (req.email() != null && !req.email().isBlank() && !req.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new RentleException("Email already in use");
            }
            user.setEmail(req.email());
            user.setEmailVerified(false); // changed email must be re-verified
        }
        return UserProfileResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserProfileResponse uploadProfilePhoto(UUID userId, MultipartFile file) {
        ImageValidator.validate(file, PROFILE_PHOTO_MAX_BYTES);
        User user = getUser(userId);
        user.setProfilePhotoUrl(storageService.upload(file, "profiles"));
        return UserProfileResponse.from(userRepository.save(user));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
