package com.ponko.craftfriend.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ponko.craftfriend.CraftFriend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 启发式正版判定：客户端登录包里的 UUID 与 Mojang 档案一致才算正版。
 *
 * <p>离线客户端可以伪造 UUID，所以这不是安全边界，只用于顶号优先级。</p>
 */
public final class PremiumVerifier {

    private record Cache(boolean premium, long at) {
    }

    private static final Map<String, Cache> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ONLINE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private PremiumVerifier() {
    }

    public static boolean isPremium(String name, UUID clientUuid) {
        if (name == null || clientUuid == null) {
            return false;
        }
        UUID offline = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (clientUuid.equals(offline)) {
            return false;
        }

        String key = name.toLowerCase();
        Cache cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - cached.at()
                < (cached.premium() ? 3_600_000L : 60_000L)) {
            return cached.premium();
        }
        boolean premium = lookup(name, clientUuid);
        CACHE.put(key, new Cache(premium, System.currentTimeMillis()));
        return premium;
    }

    public static void markOnline(String name, boolean premium) {
        ONLINE.put(name.toLowerCase(), premium);
    }

    public static boolean isOnlinePremium(String name) {
        return ONLINE.getOrDefault(name.toLowerCase(), false);
    }

    public static void removeOnline(String name) {
        ONLINE.remove(name.toLowerCase());
    }

    private static boolean lookup(String name, UUID clientUuid) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.mojang.com/users/profiles/minecraft/" + name))
                    .timeout(Duration.ofSeconds(3))
                    .header("user-agent", "WaterServerManager/0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return false;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("id")) {
                return false;
            }
            String mojangId = json.get("id").getAsString().replace("-", "");
            String clientId = clientUuid.toString().replace("-", "");
            return mojangId.equalsIgnoreCase(clientId);
        } catch (Exception e) {
            CraftFriend.LOGGER.debug("[Premium] 查询 {} 失败: {}", name, e.getMessage());
            return false;
        }
    }
}
