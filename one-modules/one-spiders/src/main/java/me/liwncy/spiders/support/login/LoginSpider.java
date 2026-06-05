package me.liwncy.spiders.support.login;

/**
 * 需要登录支持的 Spider。
 */
public interface LoginSpider {

    LoginConfig getLoginConfig();

    default boolean isSessionStillValid(LoginSession session) {
        return true;
    }
}


