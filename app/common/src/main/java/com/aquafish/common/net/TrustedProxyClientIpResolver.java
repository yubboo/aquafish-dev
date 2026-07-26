package com.aquafish.common.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 基于可信代理网段解析客户端真实 IP。
 *
 * <p>安全边界：</p>
 * <ol>
 *     <li>直连来源不在可信代理网段时，完全忽略转发请求头；</li>
 *     <li>只有直连来源可信时，才读取 X-Forwarded-For 和 X-Real-IP；</li>
 *     <li>代理链从右向左剥离可信代理，首个不可信地址才作为客户端地址；</li>
 *     <li>无法解析时回退到真实 TCP 连接来源，不根据任意请求头猜测。</li>
 * </ol>
 *
 * <p>本类是无 Spring 依赖的公共安全组件。可信代理 CIDR 由上层配置服务传入，
 * 后台登录、会员登录、注册、限流、审计和 IP 封禁应复用同一实例。</p>
 */
public final class TrustedProxyClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private final List<IpNetwork> trustedProxyNetworks;

    /**
     * 创建可信代理解析器。
     *
     * @param trustedProxyCidrs 可信代理 IP 或 CIDR，例如 127.0.0.1/32、::1/128、172.16.0.0/12
     * @throws IllegalArgumentException 配置中包含非法 IP 或 CIDR 时抛出，禁止静默放宽安全边界
     */
    public TrustedProxyClientIpResolver(List<String> trustedProxyCidrs) {
        List<String> source =
            trustedProxyCidrs == null
                ? List.of()
                : trustedProxyCidrs;

        this.trustedProxyNetworks = source.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(IpNetwork::parse)
            .toList();
    }

    /**
     * 解析请求的客户端地址。
     *
     * @param remoteAddress TCP 直连来源地址
     * @param xForwardedFor X-Forwarded-For 代理链
     * @param xRealIp X-Real-IP 代理头
     * @return 规范化后的 IPv4/IPv6；全部无效时返回空字符串
     */
    public String resolve(
        String remoteAddress,
        String xForwardedFor,
        String xRealIp
    ) {
        String remote = normalizeAddress(remoteAddress);

        if (remote.isBlank()) {
            return "";
        }

        /*
         * 直连来源不可信时，绝不读取任何客户端可自行伪造的代理头。
         */
        if (!isTrustedProxy(remote)) {
            return remote;
        }

        List<String> forwardedChain =
            forwardedAddresses(xForwardedFor);

        if (!forwardedChain.isEmpty()) {
            List<String> chain =
                new ArrayList<>(forwardedChain.size() + 1);

            chain.addAll(forwardedChain);
            chain.add(remote);

            /*
             * 从最靠近当前应用的代理开始向左检查。
             *
             * 只要仍然属于可信代理，就继续剥离；
             * 首个不可信地址才作为真实客户端地址。
             */
            for (
                int index = chain.size() - 1;
                index >= 0;
                index--
            ) {
                String address = chain.get(index);

                if (!isTrustedProxy(address)) {
                    return address;
                }
            }

            /*
             * 整条链都属于可信网段时，返回最左侧地址。
             */
            return chain.getFirst();
        }

        /*
         * 只有直连来源可信，才允许使用 X-Real-IP。
         */
        String realIp = normalizeAddress(xRealIp);

        return realIp.isBlank()
            ? remote
            : realIp;
    }

    /**
     * 判断当前请求是否应当被视为 HTTPS。
     *
     * <p>安全规则：</p>
     * <ol>
     *     <li>应用直接收到 HTTPS 时始终可信；</li>
     *     <li>应用直接收到 HTTP 时，只有 TCP 来源属于可信代理，
     *     才读取 X-Forwarded-Proto；</li>
     *     <li>协议链从右向左读取，使用最靠近当前应用的有效值；</li>
     *     <li>非可信来源伪造的代理协议头会被忽略。</li>
     * </ol>
     *
     * @param directScheme 应用直接观察到的请求协议
     * @param remoteAddress TCP 直连来源地址
     * @param xForwardedProto 代理转发协议链
     * @return 是否应为响应 Cookie 添加 Secure 属性
     */
    public boolean isSecureRequest(
        String directScheme,
        String remoteAddress,
        String xForwardedProto
    ) {
        if (
            directScheme != null &&
            "https".equalsIgnoreCase(
                directScheme.strip()
            )
        ) {
            return true;
        }

        String remote =
            normalizeAddress(remoteAddress);

        if (
            remote.isBlank() ||
            !isTrustedProxy(remote)
        ) {
            return false;
        }

        return "https".equalsIgnoreCase(
            nearestForwardedProtocol(
                xForwardedProto
            )
        );
    }

    /**
     * 从标准 Java 网络地址中提取规范化 IP。
     *
     * @param remoteAddress WebFlux 请求的 TCP 来源
     * @return IPv4、IPv6 或空字符串
     */
    public static String normalizeRemoteAddress(
        InetSocketAddress remoteAddress
    ) {
        if (remoteAddress == null) {
            return "";
        }

        String value =
            remoteAddress.getAddress() == null
                ? remoteAddress.getHostString()
                : remoteAddress
                    .getAddress()
                    .getHostAddress();

        return normalizeAddress(value);
    }

    /**
     * 获取最靠近当前应用的代理协议值。
     *
     * <p>代理链通常按照从客户端到服务端的顺序排列，
     * 因此最右侧值由离应用最近的可信代理产生。</p>
     */
    private static String nearestForwardedProtocol(
        String value
    ) {
        if (
            value == null ||
            value.isBlank()
        ) {
            return "";
        }

        String[] protocols =
            value.split(",");

        for (
            int index = protocols.length - 1;
            index >= 0;
            index--
        ) {
            String protocol =
                protocols[index]
                    .strip()
                    .replace(
                        "\"",
                        ""
                    );

            if (
                "http".equalsIgnoreCase(
                    protocol
                ) ||
                "https".equalsIgnoreCase(
                    protocol
                )
            ) {
                return protocol;
            }

            /*
             * 最靠近应用的非空值非法时直接拒绝，
             * 不继续向左寻找攻击者可能注入的 https。
             */
            if (!protocol.isBlank()) {
                return "";
            }
        }

        return "";
    }

    /**
     * 判断地址是否属于配置的可信代理网段。
     */
    public boolean isTrustedProxy(String address) {
        byte[] candidate =
            addressBytes(normalizeAddress(address));

        if (candidate == null) {
            return false;
        }

        return trustedProxyNetworks.stream()
            .anyMatch(network -> network.contains(candidate));
    }

    /**
     * 解析并过滤 X-Forwarded-For 中的地址。
     *
     * 非法地址和 unknown 占位值不会进入代理链。
     */
    private List<String> forwardedAddresses(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
            .map(TrustedProxyClientIpResolver::normalizeAddress)
            .filter(address -> !address.isBlank())
            .toList();
    }

    /**
     * 规范化 IPv4、IPv6 字面量。
     *
     * 同时拒绝域名，防止解析过程中触发 DNS 查询。
     */
    static String normalizeAddress(String value) {
        if (value == null) {
            return "";
        }

        String candidate = value.strip();

        if (
            candidate.isBlank() ||
            UNKNOWN.equalsIgnoreCase(candidate)
        ) {
            return "";
        }

        /*
         * 支持：
         * [::1]
         * [::1]:8080
         */
        if (
            candidate.startsWith("[") &&
            candidate.contains("]")
        ) {
            candidate = candidate.substring(
                1,
                candidate.indexOf(']')
            );
        } else if (
            candidate.indexOf(':') ==
                candidate.lastIndexOf(':') &&
            candidate.contains(".")
        ) {
            /*
             * 支持 IPv4 携带端口：
             * 127.0.0.1:8080
             */
            int portSeparator =
                candidate.lastIndexOf(':');

            if (
                portSeparator > 0 &&
                isDecimal(
                    candidate.substring(
                        portSeparator + 1
                    )
                )
            ) {
                candidate =
                    candidate.substring(
                        0,
                        portSeparator
                    );
            }
        }

        /*
         * 移除 IPv6 网卡作用域：
         * fe80::1%eth0
         */
        int zoneIndex = candidate.indexOf('%');

        if (zoneIndex > 0) {
            candidate =
                candidate.substring(0, zoneIndex);
        }

        String ipv4 = normalizeIpv4(candidate);

        if (!ipv4.isBlank()) {
            return ipv4;
        }

        if (!candidate.contains(":")) {
            return "";
        }

        /*
         * IPv6 只允许十六进制字符、冒号和点。
         * 因此不会把域名传给 InetAddress。
         */
        for (
            int index = 0;
            index < candidate.length();
            index++
        ) {
            char current =
                candidate.charAt(index);

            boolean allowed =
                current == ':' ||
                current == '.' ||
                Character.digit(current, 16) >= 0;

            if (!allowed) {
                return "";
            }
        }

        try {
            String normalized =
                InetAddress
                    .getByName(candidate)
                    .getHostAddress();

            int normalizedZoneIndex =
                normalized.indexOf('%');

            if (normalizedZoneIndex > 0) {
                normalized =
                    normalized.substring(
                        0,
                        normalizedZoneIndex
                    );
            }

            return "0:0:0:0:0:0:0:1"
                .equals(normalized)
                    ? "::1"
                    : normalized.toLowerCase(
                        Locale.ROOT
                    );
        } catch (UnknownHostException ignored) {
            return "";
        }
    }

    /**
     * 规范化 IPv4 地址。
     */
    private static String normalizeIpv4(
        String value
    ) {
        String[] parts =
            value.split("\\.", -1);

        if (parts.length != 4) {
            return "";
        }

        int[] numbers = new int[4];

        for (
            int index = 0;
            index < parts.length;
            index++
        ) {
            if (!isDecimal(parts[index])) {
                return "";
            }

            try {
                numbers[index] =
                    Integer.parseInt(parts[index]);
            } catch (
                NumberFormatException ignored
            ) {
                return "";
            }

            if (
                numbers[index] < 0 ||
                numbers[index] > 255
            ) {
                return "";
            }
        }

        return numbers[0] + "." +
            numbers[1] + "." +
            numbers[2] + "." +
            numbers[3];
    }

    /**
     * 判断字符串是否全部为十进制数字。
     */
    private static boolean isDecimal(
        String value
    ) {
        if (
            value == null ||
            value.isBlank()
        ) {
            return false;
        }

        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            if (
                !Character.isDigit(
                    value.charAt(index)
                )
            ) {
                return false;
            }
        }

        return true;
    }

    /**
     * 把已经经过字面量检查的地址转换为二进制。
     */
    private static byte[] addressBytes(
        String address
    ) {
        if (
            address == null ||
            address.isBlank()
        ) {
            return null;
        }

        try {
            return InetAddress
                .getByName(address)
                .getAddress();
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    /**
     * 预解析后的 CIDR 网段。
     *
     * 配置只在实例创建时解析一次，避免每次请求重复解析。
     */
    private record IpNetwork(
        byte[] networkAddress,
        int prefixLength
    ) {

        /**
         * 解析单个可信代理地址或 CIDR。
         */
        private static IpNetwork parse(
            String value
        ) {
            String candidate =
                value == null
                    ? ""
                    : value.strip();

            String[] parts =
                candidate.split("/", -1);

            if (parts.length > 2) {
                throw new IllegalArgumentException(
                    "非法可信代理 CIDR：" +
                    value
                );
            }

            String normalizedAddress =
                normalizeAddress(parts[0]);

            byte[] bytes =
                addressBytes(normalizedAddress);

            if (bytes == null) {
                throw new IllegalArgumentException(
                    "非法可信代理地址：" +
                    value
                );
            }

            int maximumPrefix =
                bytes.length * Byte.SIZE;

            int prefix =
                parts.length == 1 ||
                parts[1].isBlank()
                    ? maximumPrefix
                    : parsePrefix(
                        parts[1],
                        value
                    );

            if (
                prefix < 0 ||
                prefix > maximumPrefix
            ) {
                throw new IllegalArgumentException(
                    "可信代理 CIDR 前缀超出范围：" +
                    value
                );
            }

            byte[] network = bytes.clone();

            clearHostBits(
                network,
                prefix
            );

            return new IpNetwork(
                network,
                prefix
            );
        }

        /**
         * 判断候选地址是否位于当前 CIDR 内。
         */
        private boolean contains(
            byte[] candidate
        ) {
            if (
                candidate == null ||
                candidate.length !=
                    networkAddress.length
            ) {
                return false;
            }

            int wholeBytes =
                prefixLength / Byte.SIZE;

            int remainingBits =
                prefixLength % Byte.SIZE;

            for (
                int index = 0;
                index < wholeBytes;
                index++
            ) {
                if (
                    candidate[index] !=
                    networkAddress[index]
                ) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask =
                0xFF <<
                (Byte.SIZE - remainingBits);

            return (
                candidate[wholeBytes] & mask
            ) == (
                networkAddress[wholeBytes] & mask
            );
        }

        /**
         * 解析 CIDR 前缀长度。
         */
        private static int parsePrefix(
            String value,
            String original
        ) {
            try {
                return Integer.parseInt(value);
            } catch (
                NumberFormatException error
            ) {
                throw new IllegalArgumentException(
                    "非法可信代理 CIDR 前缀：" +
                    original,
                    error
                );
            }
        }

        /**
         * 清除地址中的主机位，得到标准网段地址。
         */
        private static void clearHostBits(
            byte[] bytes,
            int prefix
        ) {
            int wholeBytes =
                prefix / Byte.SIZE;

            int remainingBits =
                prefix % Byte.SIZE;

            if (
                remainingBits > 0 &&
                wholeBytes < bytes.length
            ) {
                int mask =
                    0xFF <<
                    (Byte.SIZE - remainingBits);

                bytes[wholeBytes] =
                    (byte) (
                        bytes[wholeBytes] &
                        mask
                    );

                wholeBytes++;
            }

            for (
                int index = wholeBytes;
                index < bytes.length;
                index++
            ) {
                bytes[index] = 0;
            }
        }
    }
}
