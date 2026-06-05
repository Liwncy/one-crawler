package me.liwncy.spiders.support.login;

import me.liwncy.common.crawler.core.AbstractSpider;
import me.liwncy.common.crawler.core.SpiderParseResult;

import java.util.Collections;
import java.util.Map;

/**
 * 需要登录态支持的 Spider 基类。
 */
public abstract class AbstractLoginSpider extends AbstractSpider implements LoginSpider {

    private LoginSessionManager loginSessionManager;
    private LoginSession currentSession;

    public void setLoginSessionManager(LoginSessionManager loginSessionManager) {
        this.loginSessionManager = loginSessionManager;
    }

    @Override
    public final void beforeStart() {
        if (loginSessionManager == null) {
            throw new IllegalStateException("loginSessionManager has not been configured: " + name());
        }
        currentSession = loginSessionManager.ensureSession(this);
        afterLoginReady(currentSession);
    }

    protected void afterLoginReady(LoginSession loginSession) {
    }

    protected LoginSession getCurrentSession() {
        return currentSession;
    }

    @Override
    public final SpiderParseResult parse(String htmlText, String url) {
        validateAuthenticatedPage(htmlText, url);
        return doParseAuthenticated(htmlText, url);
    }

    protected void validateAuthenticatedPage(String htmlText, String url) {
    }

    protected abstract SpiderParseResult doParseAuthenticated(String htmlText, String url);

    @Override
    public Map<String, String> getRequestCookies() {
        if (currentSession == null) {
            return Collections.emptyMap();
        }
        return currentSession.toCookieMap();
    }
}


