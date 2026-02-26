package com.example.auth.service;

import com.example.auth.dto.response.UserExistsResponse;
import com.example.auth.dto.response.UserInfoResponse;
import com.example.auth.entity.User;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.UserRepository;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserInfoResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        return UserInfoResponse.from(user);
    }

    public UserExistsResponse checkUserExists(Long userId) {
        return userRepository.findById(userId)
                .map(user -> UserExistsResponse.found(user.getId()))
                .orElse(UserExistsResponse.notFound());
    }

    public UserInfoResponse getUserByKeycloakId(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        return UserInfoResponse.from(user);
    }
}
