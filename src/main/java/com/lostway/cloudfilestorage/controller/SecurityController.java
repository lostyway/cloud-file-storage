package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.UserLoginAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.UserLoginDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationDTO;
import com.lostway.cloudfilestorage.exception.dto.ErrorResponseDTO;
import com.lostway.cloudfilestorage.service.SimpleUserDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@SecurityScheme(
        name = "sessionAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "JSESSIONID"
)
@Tag(name = "User Controller", description = "API для работы с пользователем (вход/регистрация/выход и т.п.).")
@RestController
@RequestMapping("${api.auth.url}")
@RequiredArgsConstructor
@Slf4j
public class SecurityController {
    private final AuthenticationManager authenticationManager;
    private final SimpleUserDetailsService simpleUserDetailsService;

    @Operation(
            summary = "Регистрация в сервисе.",
            description = "При регистрации происходит автоматическая авторизация в сервисе (последующий вход не требуется)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Успешная регистрация.",
                    content = @Content(schema = @Schema(implementation = UserRegistrationAnswerDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Ошибки валидации (пример - слишком короткий username).",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Логин пользователя занят.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @PostMapping("/sign-up")
    public ResponseEntity<UserRegistrationAnswerDTO> signUp(@RequestBody @Valid UserRegistrationDTO registrationDTO, HttpServletRequest request) {
        simpleUserDetailsService.register(registrationDTO);
        Authentication authentication = setAndGetAuthentication(registrationDTO.username(), registrationDTO.password(), request);
        return ResponseEntity.status(CREATED).body(new UserRegistrationAnswerDTO(authentication.getName()));
    }

    @Operation(
            summary = "Авторизация.",
            description = "Авторизация в сервисе."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешная авторизация.",
                    content = @Content(schema = @Schema(implementation = UserLoginAnswerDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Неверные данные (такого пользователя нет, или пароль неправильный).",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @PostMapping("/sign-in")
    public ResponseEntity<UserLoginAnswerDTO> signIn(@RequestBody @Valid UserLoginDTO loginDTO, HttpServletRequest request) {
        Authentication authentication = setAndGetAuthentication(loginDTO.username(), loginDTO.password(), request);

        String username = authentication.getName();

        return ResponseEntity.ok(new UserLoginAnswerDTO(username));
    }

    @Operation(
            summary = "Выход.",
            description = "Выход с авторизованного профиля."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Успешный выход (No content)."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Запрос исполняется неавторизованным пользователем.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
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

    @Operation(
            summary = "Текущий пользователь.",
            description = "Получение логина текущего авторизованного пользователя."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Вывод текущего пользователя.",
                    content = @Content(schema = @Schema(implementation = UserLoginAnswerDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
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
