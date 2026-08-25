package com.portfolio.agent.common.observability;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 匿名来源哈希器：用 HmacSHA256 把访客来源地址转换为不可逆的十六进制摘要，
 * 供诊断/可观测性场景关联同一来源而不落地明文地址。
 *
 * <p>关键不变量：默认构造时用 {@link SecureRandom} 生成进程内随机密钥，摘要仅在同一进程生命周期内可比，
 * 重启后不可关联，也不可用于反查原始地址；密钥只在内部持有且构造时做防御性拷贝，不对外暴露。
 * 每次调用新建 Mac 实例，实例可安全地并发使用。</p>
 */
public final class AnonymousSourceHasher {
    private final byte[] secret;

    /**
     * 以进程内随机生成的 32 字节密钥构造哈希器；产生的摘要无法跨重启关联。
     */
    public AnonymousSourceHasher() {
        this.secret = new byte[32];
        new SecureRandom().nextBytes(this.secret);
    }

    /**
     * 以外部提供的密钥构造哈希器（用于测试或需要固定密钥的场景）。
     *
     * @param secret HMAC 密钥，不允许为 null 且至少 32 字节
     * @throws IllegalArgumentException 当 secret 为 null 或长度不足 32 字节时抛出
     */
    public AnonymousSourceHasher(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("source hash secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    /**
     * 计算来源地址的 HMAC-SHA256 十六进制摘要。
     *
     * <p>只返回摘要，永不返回或记录原始 address；输入地址本身不会进入日志。</p>
     *
     * @param address 原始来源地址（如客户端 IP），不会被留存
     * @return 64 个十六进制字符的不可逆摘要
     * @throws IllegalStateException 当运行环境缺少 HmacSHA256 算法或初始化失败时抛出
     */
    public String hash(String address) {
        try {
            // 每次调用新建 Mac：Mac 非线程安全，独立实例保证并发调用互不干扰。
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    address.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot hash anonymous request source", exception);
        }
    }
}
