package me.liwncy.spiders.support.login;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 登录 Cookie 条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCookie {

    private String name;
    private String value;
    private String domain;
    private String path;
    private Long expiryEpochSecond;
    private boolean secure;
    private boolean httpOnly;

    public boolean hasExplicitExpiry() {
        return expiryEpochSecond != null;
    }

    public boolean isExpired() {
        return expiryEpochSecond != null && expiryEpochSecond <= Instant.now().getEpochSecond();
    }
}

