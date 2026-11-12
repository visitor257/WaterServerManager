package com.ponko.craftfriend.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ponko.craftfriend.CraftFriend;
import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 离线服的正版皮肤补丁。
 *
 * <p>online-mode=false 时服务端不会去 Mojang 取 textures，所有人都是 Steve/Alex。
 * 这里在玩家进服后按名字查 Mojang 档案，把签名过的 textures 属性注入回去。</p>
 */
public final class SkinService {

    private record Skin(String value, String signature, long fetchedAt) {
    }

    private static final long TTL_MS = 6L * 60L * 60L * 1000L;
    private static final Map<String, Skin> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "craftfriend-skin");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private SkinService() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (server.isOnlineMode() || hasTextures(player)) {
                return;
            }
            String name = player.getGameProfile().getName();
            Skin cached = CACHE.get(name);
            if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < TTL_MS) {
                apply(server, player, cached);
                return;
            }
            POOL.submit(() -> {
                Skin skin = fetch(name);
                if (skin != null) {
                    CACHE.put(name, skin);
                    server.execute(() -> apply(server, player, skin));
                }
            });
        });
    }

    private static boolean hasTextures(ServerPlayerEntity player) {
        return !player.getGameProfile().getProperties().get("textures").isEmpty();
    }

    private static void apply(MinecraftServer server, ServerPlayerEntity player, Skin skin) {
        if (player.isRemoved()) {
            return;
        }
        player.getGameProfile().getProperties().removeAll("textures");
        player.getGameProfile().getProperties().put("textures",
                new Property("textures", skin.value(), skin.signature()));
        server.getPlayerManager().sendToAll(new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER), List.of(player)));
        CraftFriend.LOGGER.info("[Skin] 已为 {} 恢复正版皮肤", player.getGameProfile().getName());
    }

    private static Skin fetch(String name) {
        try {
            JsonObject user = getJson("https://api.mojang.com/users/profiles/minecraft/"
                    + name.toLowerCase());
            if (user == null || !user.has("id")) {
                return null;
            }
            String id = user.get("id").getAsString();
            JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
            if (profile == null) {
                return null;
            }
            JsonArray properties = profile.getAsJsonArray("properties");
            if (properties == null) {
                return null;
            }
            for (var element : properties) {
                JsonObject property = element.getAsJsonObject();
                if ("textures".equals(property.get("name").getAsString())) {
                    String value = property.get("value").getAsString();
                    String signature = property.has("signature")
                            ? property.get("signature").getAsString() : "";
                    return new Skin(value, signature, System.currentTimeMillis());
                }
            }
        } catch (Exception e) {
            CraftFriend.LOGGER.debug("[Skin] 查询 {} 失败: {}", name, e.getMessage());
        }
        return null;
    }

    private static JsonObject getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("user-agent", "CraftFriend/0.1")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
