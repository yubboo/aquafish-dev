package com.aquafish.license;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 授权码中经过 Ed25519 签名的业务载荷。
 *
 * <p>实现结果：客户可以读取授权内容，但任何字段一旦被修改，数字签名就会失效。
 * 私钥不在 Aquafish 程序中，程序只能验证，不能伪造或延长授权。</p>
 *
 * @param schemaVersion 授权码结构版本，当前固定为 1
 * @param licenseId 授权记录唯一编号
 * @param product 产品标识，防止其他产品的授权码被误用
 * @param edition 授权版本，例如 professional、enterprise
 * @param customer 客户或授权主体显示名称
 * @param instanceId 绑定的 Aquafish 实例 ID
 * @param issuedAt 签发时间
 * @param notBefore 最早生效时间
 * @param expiresAt 到期时间；为空表示永久授权
 * @param features 本授权包含的功能项
 * @param entitlements 官方主题、插件等具体商品权益；与平台授权共同构成双授权
 * @param annotations 对标 Halo 的键值式灵活权益，例如 include-all-apps、username
 */
public record LicensePayload(
    int schemaVersion,
    String licenseId,
    String product,
    String edition,
    String customer,
    String instanceId,
    Instant issuedAt,
    Instant notBefore,
    Instant expiresAt,
    List<String> features,
    List<LicenseEntitlement> entitlements,
    Map<String, String> annotations
) {

    @JsonCreator
    public LicensePayload(
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("licenseId") String licenseId,
        @JsonProperty("product") String product,
        @JsonProperty("edition") String edition,
        @JsonProperty("customer") String customer,
        @JsonProperty("instanceId") String instanceId,
        @JsonProperty("issuedAt") Instant issuedAt,
        @JsonProperty("notBefore") Instant notBefore,
        @JsonProperty("expiresAt") Instant expiresAt,
        @JsonProperty("features") List<String> features,
        @JsonProperty("entitlements") List<LicenseEntitlement> entitlements,
        @JsonProperty("annotations") Map<String, String> annotations
    ) {
        this.schemaVersion = schemaVersion;
        this.licenseId = licenseId;
        this.product = product;
        this.edition = edition;
        this.customer = customer;
        this.instanceId = instanceId;
        this.issuedAt = issuedAt;
        this.notBefore = notBefore;
        this.expiresAt = expiresAt;
        this.features = features == null ? List.of() : List.copyOf(features);
        this.entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
        this.annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }

    /** 兼容现有测试与旧签发工具；旧 AQF1 没有资源权益时按空列表处理。 */
    public LicensePayload(
        int schemaVersion,
        String licenseId,
        String product,
        String edition,
        String customer,
        String instanceId,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt,
        List<String> features
    ) {
        this(
            schemaVersion, licenseId, product, edition, customer, instanceId,
            issuedAt, notBefore, expiresAt, features, List.of(), Map.of()
        );
    }

    /** 兼容现有测试；旧 AQF1 没有 annotations 时按空映射处理。 */
    public LicensePayload(
        int schemaVersion,
        String licenseId,
        String product,
        String edition,
        String customer,
        String instanceId,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt,
        List<String> features,
        List<LicenseEntitlement> entitlements
    ) {
        this(
            schemaVersion, licenseId, product, edition, customer, instanceId,
            issuedAt, notBefore, expiresAt, features, entitlements, Map.of()
        );
    }
}
