package com.github.caiheyu.keybook.core

import java.net.InetAddress
import com.github.caiheyu.keybook.core.network.PublicNetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicNetworkPolicyTest {
    @Test
    fun rejectsPrivateLoopbackLinkLocalAndDocumentationAddresses() {
        listOf(
            "0.0.0.0",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "192.168.1.1",
            "192.0.2.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "::1",
            "fe80::1",
            "fc00::1",
            "2001:db8::1",
        ).forEach { value ->
            assertFalse(value, PublicNetworkPolicy.isPublicAddress(InetAddress.getByName(value)))
        }
    }

    @Test
    fun acceptsPublicIpv4AndIpv6Addresses() {
        assertTrue(PublicNetworkPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(PublicNetworkPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
