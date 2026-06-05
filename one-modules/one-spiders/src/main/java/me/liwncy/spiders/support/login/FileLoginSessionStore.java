package me.liwncy.spiders.support.login;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import me.liwncy.spiders.support.SpiderPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 基于本地文件的登录态存储。
 */
@Slf4j
public class FileLoginSessionStore implements LoginSessionStore {

    @Override
    public Optional<LoginSession> load(String spiderName) {
        Path filePath = resolveSessionFile(spiderName);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            return Optional.of(JSONUtil.toBean(json, LoginSession.class));
        } catch (IOException e) {
            throw new IllegalStateException("read login session failed: " + spiderName, e);
        }
    }

    @Override
    public void save(LoginSession session) {
        Path filePath = resolveSessionFile(session.getSpiderName());
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(
                filePath,
                JSONUtil.toJsonPrettyStr(session),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new IllegalStateException("save login session failed: " + session.getSpiderName(), e);
        }
    }

    @Override
    public void delete(String spiderName) {
        Path filePath = resolveSessionFile(spiderName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("delete login session failed: {}", spiderName, e);
        }
    }

    private Path resolveSessionFile(String spiderName) {
        return SpiderPaths.resolveAuthDirectory().resolve(spiderName + "-session.json");
    }
}


