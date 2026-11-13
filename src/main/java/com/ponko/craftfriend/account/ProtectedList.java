package com.ponko.craftfriend.account;

import com.ponko.craftfriend.CraftFriendServer;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 保护名单：名单里的名字在线时不会被同名连接顶掉。 */
public final class ProtectedList {

    private static final Set<String> NAMES = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> ONLINE = new ConcurrentHashMap<>();

    private ProtectedList() {
    }

    public static void load(Collection<String> names) {
        NAMES.clear();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    NAMES.add(name.toLowerCase());
                }
            }
        }
    }

    public static boolean isProtected(String name) {
        if (name == null) {
            return false;
        }
        if (NAMES.contains(name.toLowerCase())) {
            return true;
        }
        // 名单里写的可能是账户名；用当前游戏名反查账户。
        AccountStore.Account account = CraftFriendServer.ACCOUNTS.findByMcName(name);
        return account != null && NAMES.contains(account.name.toLowerCase());
    }

    public static void markOnline(String name, boolean protectedPlayer) {
        ONLINE.put(name.toLowerCase(), protectedPlayer);
    }

    public static boolean isOnlineProtected(String name) {
        return ONLINE.getOrDefault(name.toLowerCase(), false);
    }

    public static void removeOnline(String name) {
        ONLINE.remove(name.toLowerCase());
    }
}
