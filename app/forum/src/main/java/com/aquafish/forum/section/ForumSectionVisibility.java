package com.aquafish.forum.section;

/**
 * 板块可见范围。
 *
 * <p>这里只定义领域状态，具体用户是否可见仍要结合
 * 用户组权限和板块个性化授权计算。</p>
 */
public enum ForumSectionVisibility {
    /** 游客和已登录用户均可见。 */
    PUBLIC,
    /** 只有已登录会员可见。 */
    MEMBERS,
    /** 只有被明确授权的用户或用户组可见。 */
    PRIVATE
}
