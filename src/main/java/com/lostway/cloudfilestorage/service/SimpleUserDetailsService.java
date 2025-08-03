package com.lostway.cloudfilestorage.service;

import com.lostway.cloudfilestorage.controller.dto.UserRegistrationDTO;
import com.lostway.cloudfilestorage.exception.UserAlreadyExistsException;
import com.lostway.cloudfilestorage.repository.UserRepository;
import com.lostway.cloudfilestorage.repository.entity.UserEntity;
import com.lostway.cloudfilestorage.repository.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(userEntity -> User.builder()
                        .username(userEntity.getUsername())
                        .password(userEntity.getPassword())
                        .authorities(List.of(new SimpleGrantedAuthority(userEntity.getRole())))
                        .build()
                )
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь по такому логину не был найден"));
    }

    public void register(UserRegistrationDTO registrationDTO) {
        var userByUsername = userRepository.findByUsername(registrationDTO.username());

        if (userByUsername.isPresent()) {
            throw new UserAlreadyExistsException("Пользователь с таким логином уже существует!");
        }

        UserEntity userEntity = UserEntity.builder()
                .id(null)
                .username(registrationDTO.username())
                .password(passwordEncoder.encode(registrationDTO.password()))
                .role(UserRole.USER.getAuthority())
                .build();

        userRepository.save(userEntity);

        String username = userEntity.getUsername();
        log.info("Был сохранен новый пользователь: {} ", username);
    }
}
