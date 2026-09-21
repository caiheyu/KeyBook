package com.github.caiheyu.keybook.core.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

object PublicNetworkPolicy {
    fun requirePublicHost(hostname: String, addresses: List<InetAddress>) {
        val host = hostname.trim().trimEnd('.').lowercase()
        require(host.isNotEmpty() && host != "localhost" && !host.endsWith(".localhost") &&
            !host.endsWith(".local") && !host.endsWith(".internal")) {
            "不允许访问本机或内部网络地址"
        }
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
            "官网解析到了私网、回环或保留地址"
        }
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        return when (address) {
            is Inet4Address -> isPublicIpv4(address.address)
            is Inet6Address -> isPublicIpv6(address.address)
            else -> false
        }
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        return when {
            a == 0 || a == 10 || a == 127 || a >= 224 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 && c == 0 -> false
            a == 192 && b == 0 && c == 2 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 192 && b == 168 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            else -> true
        }
    }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        if (first !in 0x20..0x3f) return false
        if (first == 0x20 && second == 0x01 &&
            (bytes[2].toInt() and 0xff) == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8
        ) return false
        return true
    }
}

class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            delegate.lookup(hostname)
        } catch (error: UnknownHostException) {
            throw error
        }
        try {
            PublicNetworkPolicy.requirePublicHost(hostname, addresses)
        } catch (error: IllegalArgumentException) {
            throw UnknownHostException(error.message).apply { initCause(error) }
        }
        return addresses
    }
}
