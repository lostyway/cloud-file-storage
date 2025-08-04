package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.UserLoginAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.UserLoginDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationDTO;
import com.lostway.cloudfilestorage.service.SimpleUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("${api.auth.url}")
@RequiredArgsConstructor
@Slf4j
public class SecurityController {
    private final AuthenticationManager authenticationManager;
    private final SimpleUserDetailsService simpleUserDetailsService;

    /**
     * При регистрации юзеру сразу создаётся сессия и выставляется кука.
     * <p>
     * Коды ошибок:
     * <p>
     * 400 - ошибки валидации (пример - слишком короткий username)
     * 409 - username занят
     * 500 - неизвестная ошибка
     */
    @PostMapping("/sign-up")
    public ResponseEntity<UserRegistrationAnswerDTO> signUp(@RequestBody @Valid UserRegistrationDTO registrationDTO, HttpServletRequest request) {
        simpleUserDetailsService.register(registrationDTO);
        Authentication authentication = setAndGetAuthentication(registrationDTO.username(), registrationDTO.password(), request);
        return ResponseEntity.status(CREATED).body(new UserRegistrationAnswerDTO(authentication.getName()));
    }

    /**
     * Коды ошибок:
     * <p>
     * 400 - ошибки валидации (пример - слишком короткий username)
     * 401 - неверные данные (такого пользователя нет, или пароль неправильный)
     * 500 - неизвестная ошибка
     */
    @PostMapping("/sign-in")
    public ResponseEntity<UserLoginAnswerDTO> signIn(@RequestBody @Valid UserLoginDTO loginDTO, HttpServletRequest request) {
        Authentication authentication = setAndGetAuthentication(loginDTO.username(), loginDTO.password(), request);

        String username = authentication.getName();

        return ResponseEntity.ok(new UserLoginAnswerDTO(username));
    }

    /**
     * Коды ошибок:
     * <p>
     * 401 - запрос исполняется неавторизованным юзером
     * 500 - неизвестная ошибка
     */
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(HttpServletRequest request) {
        var session = request.getSession(false);

        if (session != null) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
    }

    /**
     * Текущий пользователь.
     * Коды ошибок:
     * <p>
     * 401 - пользователь не авторизован
     * 500 - неизвестная ошибка
     *
     * @return username текущего пользователя
     */
    @GetMapping("/user/me")
    public ResponseEntity<UserLoginAnswerDTO> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return ResponseEntity.status(OK).body(new UserLoginAnswerDTO(username));
    }

    private Authentication setAndGetAuthentication(String login, String password, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(login, password));

        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return authentication;
    }
}
