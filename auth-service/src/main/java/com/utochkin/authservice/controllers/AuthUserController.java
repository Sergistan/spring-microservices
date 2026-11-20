package com.utochkin.authservice.controllers;

import com.utochkin.authservice.dto.ErrorResponse;
import com.utochkin.authservice.services.AuthUserService;
import com.utochkin.authservice.web.CurrentUserInfo;
import com.utochkin.authservice.web.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Входная точка auth-service (маршрутизируется гейтвеем /auth/api/v1/** → 8185).
 * POST /users — анонимная саморегистрация (покупатель/курьер);
 * DELETE /users/me — удаление собственного аккаунта (sub приходит доверенным
 * заголовком X-User-SubId от JwtTokenFilter гейтвея, извне подделать нельзя).
 */
@RestController
@RequestMapping("/auth/api/v1")
@RequiredArgsConstructor
@Tag(name = "Auth endpoints", description = "Регистрация и управление собственным аккаунтом")
public class AuthUserController {

    private final AuthUserService authUserService;

    /**
     * Саморегистрация: создаёт пользователя в Keycloak с ролью USER или COURIER.
     * Анонимный (permitAll в Config), доступен через gateway с rate-limiter.
     */
    @PostMapping("/users")
    @Operation(summary = "Регистрация нового пользователя (покупатель или курьер)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Пользователь создан (можно логиниться)"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации полей", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username или email уже занят", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> register(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = RegisterRequest.class)),
            description = "Данные регистрации; role — USER или COURIER", required = true) @Valid RegisterRequest request) {
        authUserService.register(request);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * GET /users/me — «кто я»: данные текущего залогиненного пользователя из доверенных
     * заголовков X-User-* гейтвея (sub = id в Keycloak). Позволяет перед необратимым
     * DELETE /users/me убедиться, чей именно аккаунт будет удалён.
     * ФИО приходят URL-encoded (кириллица) — декодируем, как в OrderController order-service.
     */
    @GetMapping("/users/me")
    @Operation(summary = "Данные текущего пользователя (кто удалится через DELETE /users/me)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Данные текущего пользователя",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserInfo.class))),
            @ApiResponse(responseCode = "401", description = "Требуется вход (Authorize)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CurrentUserInfo> currentUser(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-SubId", required = false) String subId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-UserName", required = false) String username,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-FirstName", required = false) String firstName,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-LastName", required = false) String lastName,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Email", required = false) String email,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Role", required = false) String role) {
        CurrentUserInfo info = new CurrentUserInfo(
                subId,
                username,
                firstName != null ? URLDecoder.decode(firstName, StandardCharsets.UTF_8) : null,
                lastName != null ? URLDecoder.decode(lastName, StandardCharsets.UTF_8) : null,
                email,
                role);
        return new ResponseEntity<>(info, HttpStatus.OK);
    }

    /**
     * Удаление собственного аккаунта: проверяет активные заказы/доставки в order-service
     * (есть — 400), затем удаляет пользователя из Keycloak. sub идентичности берём из
     * доверенного заголовка гейтвея X-User-SubId.
     */
    @DeleteMapping("/users/me")
    @Operation(summary = "Удаление собственного аккаунта")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Аккаунт удалён"),
            @ApiResponse(responseCode = "400", description = "Есть незавершённые заказы или доставки", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден в Keycloak", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Не удалось проверить заказы (order-service недоступен)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteOwnAccount(@Parameter(hidden = true)
                                                 @RequestHeader(value = "X-User-SubId", required = false) String subId) {
        authUserService.deleteOwnAccount(subId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
