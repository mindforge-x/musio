package com.musio.model;

import java.time.Instant;
import java.util.Map;

public record QQMusicCredential(
        String openid,
        String refreshToken,
        String accessToken,
        Instant expiredAt,
        String musicid,
        String musickey,
        String unionid,
        String strMusicid,
        String refreshKey,
        String encryptUin,
        int loginType,
        Map<String, Object> extraFields
) {
}
