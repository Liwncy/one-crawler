package me.liwncy.spiders.support.login;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录账号信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCredentials {

    private String username;
    private String password;
}

