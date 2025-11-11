package unprotesting.com.github.data;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * The class that represents a CollectFirst.
 */
public class CollectFirst implements Serializable {

    private static final long serialVersionUID = 4793403925265988249L;

    // The CollectFirst setting
    @Getter
    protected final CollectFirstSetting setting;
    // Whether the item has been found anywhere on the server
    @Getter
    @Setter
    protected boolean foundInServer;

    /**
     * Constructor for the collect first class.
     *
     * @param cfSetting The collect first setting for this shop.
     */
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

    /**
     * Whether the collect first setting is for players, servers, or neither.
     */
    public enum CollectFirstSetting {
        PLAYER,
        SERVER,
        NONE
    }

}