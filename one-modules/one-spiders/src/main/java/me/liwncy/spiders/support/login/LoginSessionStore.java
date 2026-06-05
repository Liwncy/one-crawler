package me.liwncy.spiders.support.login;

import java.util.Optional;

/**
 * 登录态存储。
 */
public interface LoginSessionStore {

    Optional<LoginSession> load(String spiderName);

    void save(LoginSession session);

    void delete(String spiderName);
}


