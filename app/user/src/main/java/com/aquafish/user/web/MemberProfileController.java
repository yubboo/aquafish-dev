package com.aquafish.user.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.user.auth.MemberAuthUser;
import com.aquafish.user.profile.MemberProfile;
import com.aquafish.user.profile.MemberProfileService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 当前登录用户个人中心接口。
 */
@RestController
public class MemberProfileController {

    private final MemberProfileService profileService;

    public MemberProfileController(MemberProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/api/member/profile")
    public Mono<ResponseEntity<ApiResult<MemberProfile>>> current(
        Authentication authentication
    ) {
        MemberAuthUser user = authenticatedUser(authentication);
        return profileService.current(user)
            .map(profile -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(profile, "个人中心资料获取成功")));
    }

    private MemberAuthUser authenticatedUser(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof MemberAuthUser user)) {
            throw new IllegalStateException("登录状态已失效，请重新登录。");
        }
        return user;
    }
}
