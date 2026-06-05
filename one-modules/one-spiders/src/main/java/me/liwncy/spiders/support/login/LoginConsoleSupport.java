package me.liwncy.spiders.support.login;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 控制台交互支持。
 */
public class LoginConsoleSupport {

    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public boolean waitForEnter(String message) {
        try {
            System.out.println(message);
            return reader.readLine() != null;
        } catch (IOException e) {
            throw new IllegalStateException("read console input failed", e);
        }
    }

    public String prompt(String message) {
        try {
            System.out.print(message);
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException e) {
            throw new IllegalStateException("read console input failed", e);
        }
    }
}


