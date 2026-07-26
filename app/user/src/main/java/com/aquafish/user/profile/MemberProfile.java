package com.aquafish.user.profile;

/**
 * 当前登录用户的个人中心数据。
 *
 * <p>{@code uid} 是可复用的正整数用户编号，只在本人个人中心与后台管理中展示；
 * {@code publicId} 是面向公开页面的稳定编号。</p>
 */
public record MemberProfile(
    long uid,
    String publicId,
    String username,
    String email,
    String displayName,
    String avatar,
    String groupKey,
    String groupName,
    String bio,
    String signature,
    long points,
    long threadsCount,
    long postsCount,
    long commentsCount,
    long followersCount,
    long followingCount,
    long friendsCount,
    String createdAt,
    String lastLoginAt,
    boolean adminAccess
) {
}
