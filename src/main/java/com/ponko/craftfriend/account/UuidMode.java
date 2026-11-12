package com.ponko.craftfriend.account;

/** 登录 UUID 策略开关，供登录 mixin 读取。 */
public final class UuidMode {

    /** true：按名字固定 UUID（YSM 缓存命中，但同名不能共存）。 */
    public static volatile boolean stable;

    /** true：同名顶号时按正版优先规则处理（依赖 stable）。 */
    public static volatile boolean priority;

    private UuidMode() {
    }
}
