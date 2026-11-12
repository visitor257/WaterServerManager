package com.ponko.craftfriend.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ponko.craftfriend.CraftFriend;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 内部账号：账户名、当前绑定的游戏名、密码哈希、邀请码、角色。
 *
 * <p>身份以账户名为准；游戏名只是"当前绑定"，改名后可以重新绑定。</p>
 */
public final class AccountStore {

    public static final class Account {
        public String id;
        /** 账户名，注册时指定，不可随意改。 */
        public String name;
        /** 当前绑定的 Minecraft 游戏名。 */
        public String mcName;
        public String salt;
        public String hash;
        /** 当前有效的登录令牌；密码登录会清空。 */
        public String token;
        public String role = "player";
        public long createdAt;
    }

    public static final class Invite {
        public String code;
        public String role = "player";
        public long expiresAt;
        public boolean used;
    }

    public static final class Data {
        public Map<String, Account> accounts = new LinkedHashMap<>();
        public Map<String, Invite> invites = new LinkedHashMap<>();
    }

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir()
            .resolve("craftfriend")
            .resolve("accounts.json");
    private final SecureRandom random = new SecureRandom();
    private Data data = new Data();

    public synchronized void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Data loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (loaded != null) {
                data = loaded;
                if (data.accounts == null) data.accounts = new LinkedHashMap<>();
                if (data.invites == null) data.invites = new LinkedHashMap<>();
                for (Account account : data.accounts.values()) {
                    if (account.id == null || account.id.isBlank()) {
                        account.id = UUID.randomUUID().toString();
                    }
                    if (account.mcName == null || account.mcName.isBlank()) {
                        account.mcName = account.name;
                    }
                }
            }
            CraftFriend.LOGGER.info("[Account] 已载入 {} 个内部账号", data.accounts.size());
        } catch (IOException | RuntimeException e) {
            CraftFriend.LOGGER.warn("[Account] 读取账号文件失败", e);
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftFriend.LOGGER.warn("[Account] 保存账号文件失败", e);
        }
    }

    public synchronized boolean exists(String accountName) {
        return data.accounts.containsKey(key(accountName));
    }

    public synchronized Account get(String accountName) {
        return data.accounts.get(key(accountName));
    }

    public synchronized Account findByMcName(String mcName) {
        for (Account account : data.accounts.values()) {
            if (account.mcName != null && account.mcName.equalsIgnoreCase(mcName)) {
                return account;
            }
        }
        return null;
    }

    /** 账户名或当前游戏名都能解析到账号。 */
    public synchronized Account resolve(String name) {
        Account account = data.accounts.get(key(name));
        return account != null ? account : findByMcName(name);
    }

    public synchronized boolean create(String accountName, String password, String role, String mcName) {
        if (data.accounts.containsKey(key(accountName)) || findByMcName(mcName) != null) {
            return false;
        }
        Account account = new Account();
        account.id = UUID.randomUUID().toString();
        account.name = accountName;
        account.mcName = mcName;
        account.role = role;
        account.createdAt = System.currentTimeMillis();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        account.salt = Base64.getEncoder().encodeToString(salt);
        account.hash = hash(password, salt);
        data.accounts.put(key(accountName), account);
        save();
        return true;
    }

    public synchronized boolean verify(String accountName, String password) {
        Account account = data.accounts.get(key(accountName));
        if (account == null) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(account.salt);
        String computed = hash(password, salt);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                account.hash.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized boolean bind(String accountName, String mcName) {
        Account account = data.accounts.get(key(accountName));
        if (account == null) {
            return false;
        }
        Account other = findByMcName(mcName);
        if (other != null && !other.id.equals(account.id)) {
            return false;
        }
        account.mcName = mcName;
        save();
        return true;
    }

    public synchronized boolean delete(String name) {
        Account account = resolve(name);
        if (account == null) {
            return false;
        }
        data.accounts.remove(key(account.name));
        save();
        return true;
    }

    public synchronized boolean changePassword(String name, String oldPassword, String newPassword) {
        Account account = resolve(name);
        if (account == null || !verify(account.name, oldPassword)) {
            return false;
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        account.salt = Base64.getEncoder().encodeToString(salt);
        account.hash = hash(newPassword, salt);
        account.token = null;
        save();
        return true;
    }

    public synchronized String roleOf(String name) {
        Account account = resolve(name);
        return account == null ? null : account.role;
    }

    public synchronized boolean setRole(String name, String role) {
        Account account = resolve(name);
        if (account == null) {
            return false;
        }
        account.role = role;
        save();
        return true;
    }

    public synchronized String generateToken(String name) {
        Account account = resolve(name);
        if (account == null) {
            return null;
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        account.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        save();
        return account.token;
    }

    public synchronized void clearToken(String name) {
        Account account = resolve(name);
        if (account != null && account.token != null) {
            account.token = null;
            save();
        }
    }

    public synchronized Account findByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        for (Account account : data.accounts.values()) {
            if (token.equals(account.token)) {
                return account;
            }
        }
        return null;
    }

    public synchronized String idOf(String name) {
        Account account = resolve(name);
        return account == null ? null : account.id;
    }

    public synchronized String createInvite(String role, long ttlMs) {
        Invite invite = new Invite();
        invite.code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        invite.role = role;
        invite.expiresAt = System.currentTimeMillis() + ttlMs;
        data.invites.put(invite.code, invite);
        save();
        return invite.code;
    }

    public synchronized Invite consumeInvite(String code) {
        Invite invite = data.invites.get(code == null ? "" : code.trim().toUpperCase());
        if (invite == null || invite.used || invite.expiresAt < System.currentTimeMillis()) {
            return null;
        }
        invite.used = true;
        save();
        return invite;
    }

    public synchronized int size() {
        return data.accounts.size();
    }

    public synchronized java.util.Collection<Account> all() {
        return java.util.List.copyOf(data.accounts.values());
    }

    private static String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase();
    }
}
