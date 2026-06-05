package me.liwncy.spiders.support.login;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录态管理器。
 */
@Slf4j
@RequiredArgsConstructor
public class LoginSessionManager {

	private final LoginSessionStore sessionStore;
	private final LoginSupport loginSupport;
	private final boolean forceRefresh;
	private final String browserUserDataDirOverride;

	public LoginSession ensureSession(AbstractLoginSpider spider) {
		LoginConfig config = normalizeConfig(spider);
		if (forceRefresh) {
			log.info("force refresh login session: {}", config.getSpiderName());
			sessionStore.delete(config.getSpiderName());
		} else {
			LoginSession cachedSession = sessionStore.load(config.getSpiderName())
				.filter(this::isReusable)
				.filter(session -> isStillValid(spider, session))
				.orElse(null);
			if (cachedSession != null) {
				log.info("reuse cached login session: {}", config.getSpiderName());
				return cachedSession;
			}
		}

		log.info("login session not found or expired, start manual login: {}", config.getSpiderName());
		LoginSession session = loginSupport.login(config);
		if (!isReusable(session)) {
			throw new IllegalStateException("login session cookies are empty: " + config.getSpiderName());
		}
		sessionStore.save(session);
		return session;
	}

	private boolean isReusable(LoginSession session) {
		return session != null
			&& !session.isExpired()
			&& session.hasAnyNonExpiredCookie();
	}

	private boolean isStillValid(AbstractLoginSpider spider, LoginSession session) {
		try {
			return spider.isSessionStillValid(session);
		} catch (RuntimeException e) {
			log.warn("check login session validity failed, spider={}", spider.name(), e);
			return false;
		}
	}

	private LoginConfig normalizeConfig(AbstractLoginSpider spider) {
		LoginConfig source = spider.getLoginConfig();
		if (source == null) {
			throw new IllegalStateException("login config can not be null: " + spider.name());
		}
		if (isBlank(source.getLoginUrl())) {
			throw new IllegalStateException("loginUrl can not be blank: " + spider.name());
		}
		String browserUserDataDir = isBlank(browserUserDataDirOverride)
			? source.getBrowserUserDataDir()
			: browserUserDataDirOverride;
		return LoginConfig.builder()
			.spiderName(isBlank(source.getSpiderName()) ? spider.name() : source.getSpiderName().trim())
			.baseUrl(defaultString(source.getBaseUrl()))
			.loginUrl(source.getLoginUrl().trim())
			.entryUrl(defaultString(source.getEntryUrl()))
			.credentials(source.getCredentials())
			.usernameSelector(source.getUsernameSelector())
			.passwordSelector(source.getPasswordSelector())
			.submitSelector(source.getSubmitSelector())
			.sessionTtlMinutes(source.getSessionTtlMinutes())
			.manualCaptcha(source.isManualCaptcha())
			.manualLoginTimeoutMillis(source.getManualLoginTimeoutMillis())
			.waitAfterSubmitMillis(source.getWaitAfterSubmitMillis())
			.browserUserDataDir(browserUserDataDir)
			.build();
	}

	private String defaultString(String value) {
		return value == null ? "" : value.trim();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}



