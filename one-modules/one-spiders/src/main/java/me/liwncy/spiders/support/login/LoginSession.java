package me.liwncy.spiders.support.login;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录态信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginSession {

	private String spiderName;

	@Builder.Default
	private String baseUrl = "";

	@Builder.Default
	private List<LoginCookie> cookies = new ArrayList<>();

	@Builder.Default
	private Map<String, String> headers = new LinkedHashMap<>();

	private LocalDateTime createdAt;
	private LocalDateTime expiresAt;

	public boolean isExpired() {
		if (hasAnyPersistentCookie() && hasAnyNonExpiredCookie()) {
			return false;
		}
		return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
	}

	public boolean hasAnyPersistentCookie() {
		if (cookies == null || cookies.isEmpty()) {
			return false;
		}
		for (LoginCookie cookie : cookies) {
			if (cookie != null && cookie.hasExplicitExpiry()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasAnyNonExpiredCookie() {
		if (cookies == null || cookies.isEmpty()) {
			return false;
		}
		for (LoginCookie cookie : cookies) {
			if (cookie != null && !cookie.isExpired()) {
				return true;
			}
		}
		return false;
	}

	public Map<String, String> toCookieMap() {
		Map<String, String> cookieMap = new LinkedHashMap<>();
		if (cookies == null) {
			return cookieMap;
		}
		for (LoginCookie cookie : cookies) {
			if (cookie == null || cookie.isExpired() || cookie.getName() == null || cookie.getValue() == null) {
				continue;
			}
			cookieMap.put(cookie.getName(), cookie.getValue());
		}
		return cookieMap;
	}
}


