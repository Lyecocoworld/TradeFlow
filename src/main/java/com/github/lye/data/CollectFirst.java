package com.github.lye.data;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class CollectFirst implements Serializable {

    private static final long serialVersionUID = 4793403925265988249L;

    // The CollectFirst setting
    @Getter
    protected final CollectFirstSetting setting;
    // Whether the item has been found anywhere on the server
    @Getter
    @Setter
    protected boolean foundInServer;

    protected CollectFirst(String cfSetting) {
        cfSetting = cfSetting.toLowerCase();
        if (cfSetting.equalsIgnoreCase("player")) {
            this.setting = CollectFirstSetting.PLAYER;
        } else if (cfSetting.equalsIgnoreCase("server")) {
            this.setting = CollectFirstSetting.SERVER;
        } else {
            this.setting = CollectFirstSetting.NONE;
        }

        this.foundInServer = false;
    }

    public CollectFirst(CollectFirstSetting setting, boolean foundInServer) {
        this.setting = setting;
        this.foundInServer = foundInServer;
    }

    public enum CollectFirstSetting {
        PLAYER,
        SERVER,
        NONE
    }

}