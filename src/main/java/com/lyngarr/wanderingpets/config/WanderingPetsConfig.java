package com.lyngarr.wanderingpets.config;

import com.lyngarr.wanderingpets.LyngarrWanderingPets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

public final class WanderingPetsConfig {

    private static final Logger LOGGER = LyngarrWanderingPets.LOGGER;
    private static final String FILE_NAME = "lyngarrwanderingpets.properties";
    private static final String PROPERTY_KEY = "wanderable_mobs";

    private static final Set<String> DEFAULT_WANDERABLE_MOBS = Set.of(
        "minecraft:cat",
        "minecraft:wolf",
        "minecraft:parrot"
    );

    private static boolean loaded = false;
    private static Set<String> wanderableMobs = new LinkedHashSet<>(DEFAULT_WANDERABLE_MOBS);
    private static Set<EntityType<?>> wanderableEntityTypes = Set.of();

    private WanderingPetsConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();

        ensureConfigDirectory(configFile);
        if (!Files.exists(configFile)) {
            save(configFile);
        }

        if (Files.exists(configFile)) {
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                LOGGER.warn("Failed to read {}, using default whitelist", FILE_NAME, exception);
            }
        }

        Set<String> parsedWhitelist = parseWhitelist(properties.getProperty(PROPERTY_KEY));
        wanderableMobs = Collections.unmodifiableSet(parsedWhitelist);
        wanderableEntityTypes = Collections.unmodifiableSet(resolveEntityTypes(parsedWhitelist));

        if (!Files.exists(configFile) || properties.getProperty(PROPERTY_KEY) == null) {
            save(configFile);
        }

        loaded = true;
        LOGGER.info("Loaded Wandering Pets config with {} whitelisted mob(s)", wanderableMobs.size());
    }

    public static boolean isAllowedToWander(Entity entity) {
        load();
        if (entity == null) {
            return false;
        }

        return isAllowedToWander(entity.getType());
    }

    public static boolean isAllowedToWander(EntityType<?> entityType) {
        load();
        if (entityType == null) {
            return false;
        }

        return wanderableEntityTypes.contains(entityType);
    }

    public static Set<String> getWanderableMobs() {
        load();
        return wanderableMobs;
    }

    private static Set<EntityType<?>> resolveEntityTypes(Set<String> mobIds) {
        Set<EntityType<?>> resolved = new HashSet<>();
        for (String mobId : mobIds) {
            Identifier identifier = Identifier.tryParse(mobId);
            if (identifier == null) {
                LOGGER.warn("Skipping invalid entity id '{}' in {}", mobId, FILE_NAME);
                continue;
            }

            BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).ifPresentOrElse(
                resolved::add,
                () -> LOGGER.warn("Skipping unknown entity id '{}' in {}", mobId, FILE_NAME)
            );
        }
        return resolved;
    }

    private static Set<String> parseWhitelist(String rawWhitelist) {
        if (rawWhitelist == null || rawWhitelist.isBlank()) {
            return new LinkedHashSet<>(DEFAULT_WANDERABLE_MOBS);
        }

        Set<String> parsed = Arrays.stream(rawWhitelist.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (parsed.isEmpty()) {
            parsed.addAll(DEFAULT_WANDERABLE_MOBS);
        }

        return parsed;
    }

    private static void ensureConfigDirectory(Path configFile) {
        Path parent = configFile.getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            LOGGER.warn("Failed to create config directory for {}", FILE_NAME, exception);
        }
    }

    private static void save(Path configFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("#Lyngarr's Wandering Pets config")
                .append(System.lineSeparator())
                .append("#You can safely remove any pet from the default ones, but adding any will most likely break things, do so at your own risk.")
                .append(System.lineSeparator())
                .append("wanderable_mobs=").append(String.join(",", wanderableMobs)).append(System.lineSeparator());

        ensureConfigDirectory(configFile);
        try {
            Files.writeString(configFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Failed to write {}, keeping in-memory defaults", FILE_NAME, exception);
        }
    }
}

