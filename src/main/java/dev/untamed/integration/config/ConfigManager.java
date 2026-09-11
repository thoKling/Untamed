package dev.untamed.integration.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.untamed.Untamed;
import dev.untamed.domain.config.SurvivalConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and writes the gameplay config file.
 *
 * <p>The config records themselves are plain domain types with no knowledge of
 * files or JSON. This class is the only thing that knows where the file lives.
 */
public final class ConfigManager {

    private static final String FILE_NAME = "untamed.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /**
     * Reads the config, writing a default file if none exists.
     *
     * <p>A malformed file is never fatal and is never overwritten. The defaults
     * are used for that session so a typo cannot stop a server from booting or
     * silently destroy a hand-tuned file.
     */
    public static SurvivalConfig loadOrCreate() {
        Path path = configPath();

        if (!Files.exists(path)) {
            SurvivalConfig defaults = SurvivalConfig.defaults();
            save(defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            SurvivalConfig parsed = GSON.fromJson(reader, SurvivalConfig.class);
            if (parsed == null) {
                Untamed.LOGGER.warn("Config file {} was empty; using defaults", path);
                return SurvivalConfig.defaults();
            }
            return parsed.sanitised();
        } catch (IOException | JsonSyntaxException e) {
            Untamed.LOGGER.error("Could not read config file {}; using defaults", path, e);
            return SurvivalConfig.defaults();
        }
    }

    public static void save(SurvivalConfig config) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Untamed.LOGGER.error("Could not write config file {}", path, e);
        }
    }
}
