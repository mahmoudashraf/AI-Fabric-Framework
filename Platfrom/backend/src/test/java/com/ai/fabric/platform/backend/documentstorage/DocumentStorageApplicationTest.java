package com.ai.fabric.platform.backend.documentstorage;

import com.ai.fabric.platform.backend.documentstorage.provider.AwsCustomerDocumentStorageProvider;
import com.ai.fabric.platform.backend.documentstorage.repository.*;
import com.ai.fabric.platform.backend.documentstorage.service.CustomerDocumentStorageService;
import com.ai.fabric.platform.backend.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"platform.document-storage.hosted.enabled=false","platform.bootstrap.sample-enabled=false","platform.auth.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentStorageApplicationTest {
    @Autowired MockMvc mvc;
    @Autowired CustomerDocumentStorageService storage;
    @Autowired AwsCustomerDocumentStorageProvider provider;
    @Autowired DocumentStorageConnectionRepository connections;
    @Autowired DocumentStorageUploadRepository uploads;
    @Autowired DocumentStorageSelectionRepository selections;
    final String base="/api/deployments/unknown-deployment/document-storage/connections";

    @Test void allMetadataRepositoriesAndProviderStartWithTheApplicationWithoutAwsClients() {
        assertThat(storage).isNotNull();assertThat(connections.count()).isZero();
        assertThat(uploads.count()).isZero();assertThat(selections.count()).isZero();
        assertThat(provider.enabled()).isFalse();assertThat(ReflectionTestUtils.getField(provider,"clients")).isNull();
    }

    @Test void anonymousAndPublicClientCannotReachTheNewControlPlaneRoutes() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        var client=caller("external-client",PlatformRole.PUBLIC_API_CLIENT);
        mvc.perform(get(base).with(authentication(client))).andExpect(status().isForbidden());
        mvc.perform(post(base+"/unknown/uploads?filename=file.txt&prefix=public%2F&operationKey=upload")
            .with(authentication(client)).contentType("application/octet-stream").content("file"))
            .andExpect(status().isForbidden());
        assertThat(ReflectionTestUtils.getField(provider,"clients")).isNull();
    }

    @Test void customerAndOperatorRoutesResolveActualDeploymentBeforeStorageSideEffects() throws Exception {
        for(var role:new PlatformRole[]{PlatformRole.CUSTOMER_ADMIN,PlatformRole.PLATFORM_OPERATOR,PlatformRole.PLATFORM_ADMIN}) {
            var caller=caller("owner",role);
            mvc.perform(get(base).with(authentication(caller))).andExpect(status().isNotFound());
            mvc.perform(post(base).with(authentication(caller)).contentType("application/json")
                .content("{\"name\":\"Documents\",\"operationKey\":\"create\"}"))
                .andExpect(status().isNotFound());
        }
        assertThat(connections.count()).isZero();assertThat(ReflectionTestUtils.getField(provider,"clients")).isNull();
    }

    private UsernamePasswordAuthenticationToken caller(String id,PlatformRole role) {
        return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(id,role,id,"TEST"),null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role.name())));
    }
}
