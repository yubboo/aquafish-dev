package com.aquafish.forum.permission;

import com.aquafish.user.auth.MemberAuthUser;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 把统一前台会员主体转换成论坛领域安全上下文。
 *
 * <p>用户 ID、用户组权限和 post/all 封禁均来自 user 模块的数据库会话认证。
 * 当前数据库还没有“用户组—指定板块发布/私有板块阅读”关系表，因此这两类范围
 * 必须保持空集合并默认拒绝，不能猜测 permission_value 文本格式后放行。</p>
 */
@Component
public class ForumMemberActorFactory {

    /**
     * 只接受已经通过 MemberAuthService 校验的主体。
     */
    public ForumMemberActor authenticated(MemberAuthUser user) {
        if (user == null) {
            throw new IllegalStateException("论坛操作缺少已认证会员主体。");
        }
        return new ForumMemberActor(
            user.id(),
            true,
            user.forumPostingBanned(),
            user.permissions(),
            Set.of(),
            Set.of()
        );
    }

    public ForumMemberActor anonymous() {
        return ForumMemberActor.anonymous();
    }
}
