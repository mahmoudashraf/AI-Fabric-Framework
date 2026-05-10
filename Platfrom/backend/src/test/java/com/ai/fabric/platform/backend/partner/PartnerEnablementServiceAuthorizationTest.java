package com.ai.fabric.platform.backend.partner;

import com.ai.fabric.platform.backend.partner.service.PartnerEnablementService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerEnablementServiceAuthorizationTest {

    @Test
    void publicServiceMethodsDeclareMethodSecurity() {
        List<String> unsecuredMethods = Arrays.stream(PartnerEnablementService.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .filter(method -> !method.isSynthetic())
            .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
            .map(Method::getName)
            .sorted()
            .toList();

        assertThat(unsecuredMethods).isEmpty();
    }
}
