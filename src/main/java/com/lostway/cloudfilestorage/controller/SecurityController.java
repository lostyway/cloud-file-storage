package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.UserLoginAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.UserLoginDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.auth.url}")
public class SecurityController {

    /**
     * При регистрации юзеру сразу создаётся сессия и выставляется кука.
     *
     * Коды ошибок:
     *
     *     400 - ошибки валидации (пример - слишком короткий username)
     *     409 - username занят
     *     500 - неизвестная ошибка
     * @param registrationDTO
     * @return
     */
    @PostMapping("/sign-up")
    public ResponseEntity<UserRegistrationAnswerDTO> signUp(@RequestBody UserRegistrationDTO registrationDTO) {
        String username = securityService.register(registrationDTO);
        return ResponseEntity.status(CREATED).body(new UserRegistrationAnswerDTO(username));
    }

    /**
     * Коды ошибок:
     *
     *     400 - ошибки валидации (пример - слишком короткий username)
     *     401 - неверные данные (такого пользователя нет, или пароль неправильный)
     *     500 - неизвестная ошибка
     * @param loginDTO
     * @return
     */
    @PostMapping("/sign-in")
    public ResponseEntity<UserLoginAnswerDTO> signIn(@RequestBody UserLoginDTO loginDTO) {
        String username = securityService.login(loginDTO);
        return ResponseEntity.status(OK).body(new UserLoginAnswerDTO(username));
    }

    /**
     * Коды ошибок:
     *
     *     401 - запрос исполняется неавторизованным юзером
     *     500 - неизвестная ошибка
     * @return
     */
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Текущий пользователь.
     * Коды ошибок:
     *
     *     401 - пользователь не авторизован
     *     500 - неизвестная ошибка
     * @return username текущего пользователя
     */
    @GetMapping("/user/me")
    public ResponseEntity<UserLoginAnswerDTO> me() {
        return ResponseEntity.status(OK).body(new UserLoginAnswerDTO(null));
    }
}
