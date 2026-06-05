package me.liwncy.spiders.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spider 相关路径工具。
 */
public final class SpiderPaths {

	private SpiderPaths() {
	}

	public static Path resolveRepositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		while (current != null) {
			if (Files.exists(current.resolve("pom.xml"))
				&& Files.isDirectory(current.resolve("one-modules"))
				&& Files.isDirectory(current.resolve("one-common"))) {
				return current;
			}
			current = current.getParent();
		}
		return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
	}

	public static Path resolveDataDirectory() {
		return resolveRepositoryRoot().resolve("data");
	}

	public static Path resolveAuthDirectory() {
		return resolveDataDirectory().resolve("auth");
	}
}

