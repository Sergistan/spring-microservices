package com.utochkin.authservice.config;

import com.utochkin.authservice.keycloak.ServiceTokenClient;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
public class ServiceTokenRequestInterceptorTest {

    @Mock
    private ServiceTokenClient serviceTokenClient;

    @InjectMocks
    private ServiceTokenRequestInterceptor interceptor;

    @Test
    @DisplayName("Подставляет Authorization: Bearer <сервисный токен>")
    void appliesBearerToken() {
        given(serviceTokenClient.getServiceToken()).willReturn("token-1");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer token-1");
    }

    @Test
    @DisplayName("Токен недоступен → вызов уходит без Authorization (не вредит остальным заголовкам)")
    void tokenUnavailable_noAuthorizationHeader() {
        willThrow(new IllegalStateException("нет регистрации")).given(serviceTokenClient).getServiceToken();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }
}
