package com.nevinsight.common;

import lombok.Getter;

@Getter
public enum PlatformEnum {

    XHS("xhs", "小红书"),
    DY("dy", "抖音"),
    BILI("bili", "B站"),
    WB("wb", "微博"),
    KS("ks", "快手"),
    TIEBA("tieba", "贴吧"),
    ZHIHU("zhihu", "知乎");

    private final String code;
    private final String name;

    PlatformEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static PlatformEnum fromCode(String code) {
        for (PlatformEnum p : values()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        return null;
    }
}
