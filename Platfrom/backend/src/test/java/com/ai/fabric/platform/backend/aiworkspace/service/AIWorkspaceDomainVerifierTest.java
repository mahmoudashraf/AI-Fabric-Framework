package com.ai.fabric.platform.backend.aiworkspace.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class AIWorkspaceDomainVerifierTest {

    @Test
    void rejectsPrivateLoopbackLinkLocalAndDocumentationAddresses() throws Exception {
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("10.20.30.40"))).isFalse();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("100.64.1.1"))).isFalse();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("169.254.169.254"))).isFalse();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("192.0.2.10"))).isFalse();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("2001:db8::1"))).isFalse();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("fc00::1"))).isFalse();
    }

    @Test
    void acceptsPublicIpv4AndIpv6Addresses() throws Exception {
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("1.1.1.1"))).isTrue();
        assertThat(AIWorkspaceDomainVerifier.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }
}
