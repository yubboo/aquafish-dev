package com.aquafish.core.network;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 不依赖 DNS 的 IPv4、IPv6 与 CIDR 规则。
 *
 * <p>后台保存 IP 封禁时和前台请求匹配时必须使用同一解析规则。输入只允许 IP 字面量
 * 和可选 CIDR 前缀，禁止主机名，避免管理请求触发 DNS 查询。解析结果不可变，可安全
 * 用于并发请求。</p>
 */
public final class IpAddressRule {

    private static final Pattern ADDRESS_LITERAL =
        Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final String source;
    private final byte[] network;
    private final int prefixLength;
    private final int version;

    private IpAddressRule(
        String source,
        byte[] network,
        int prefixLength,
        int version
    ) {
        this.source = source;
        this.network = network;
        this.prefixLength = prefixLength;
        this.version = version;
    }

    /** 解析单个 IP 或 CIDR；格式非法时返回带中文原因的业务异常。 */
    public static IpAddressRule parse(String value) {
        String source = value == null ? "" : value.trim();
        if (source.isBlank() || source.length() > 120) {
            throw new IllegalArgumentException("IP 或 CIDR 不能为空且不能超过 120 个字符。");
        }

        String[] parts = source.split("/", -1);
        if (parts.length > 2 || !ADDRESS_LITERAL.matcher(parts[0]).matches()) {
            throw new IllegalArgumentException("只支持 IPv4、IPv6 或 CIDR，不支持主机名。");
        }

        InetAddress address = literal(parts[0]);
        int bits = address.getAddress().length * Byte.SIZE;
        int prefix = parts.length == 1 ? bits : prefix(parts[1], bits);
        byte[] network = masked(address.getAddress(), prefix);
        int version = address instanceof Inet4Address ? 4 : 6;

        return new IpAddressRule(source, network, prefix, version);
    }

    /**
     * 判断请求地址是否落在规则内；无效请求地址直接返回 false，不影响正常请求链。
     */
    public boolean matches(String candidate) {
        try {
            String value = candidate == null ? "" : candidate.trim();
            if (!ADDRESS_LITERAL.matcher(value).matches()) {
                return false;
            }
            InetAddress address = literal(value);
            if ((version == 4 && !(address instanceof Inet4Address))
                || (version == 6 && !(address instanceof Inet6Address))) {
                return false;
            }
            return Arrays.equals(network, masked(address.getAddress(), prefixLength));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public String source() {
        return source;
    }

    public int prefixLength() {
        return prefixLength;
    }

    public int version() {
        return version;
    }

    private static InetAddress literal(String value) {
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) {
                throw new IllegalArgumentException("无法识别 IP 地址。");
            }
            return address;
        } catch (UnknownHostException error) {
            throw new IllegalArgumentException("IP 地址格式不正确。", error);
        }
    }

    private static int prefix(String value, int maximum) {
        try {
            int prefix = Integer.parseInt(value);
            if (prefix < 0 || prefix > maximum) {
                throw new IllegalArgumentException(
                    "CIDR 前缀必须在 0 到 " + maximum + " 之间。"
                );
            }
            return prefix;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("CIDR 前缀必须是整数。", error);
        }
    }

    private static byte[] masked(byte[] address, int prefixLength) {
        byte[] result = Arrays.copyOf(address, address.length);
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;

        if (remainingBits > 0 && fullBytes < result.length) {
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            result[fullBytes] = (byte) (result[fullBytes] & mask);
            fullBytes++;
        }
        Arrays.fill(result, fullBytes, result.length, (byte) 0);
        return result;
    }
}
