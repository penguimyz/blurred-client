package dev.blurredclient.mod.module;

/**
 * Every module id, in one place.
 *
 * <p>Ids are keys in {@code blurred.json} and in the launcher's copy of the
 * schema, so they are a file format, not code names: once shipped, an id
 * never changes. Kept as constants so the migrator, the modules and the tests
 * can't disagree about a spelling.
 */
public final class ModuleIds {
    private ModuleIds() {}

    // HUD
    public static final String INFO_BAR = "info_bar";
    public static final String FPS = "fps";
    public static final String PING = "ping";
    public static final String COORDS = "coords";
    public static final String DIRECTION = "direction";
    public static final String CPS = "cps";
    public static final String CLOCK = "clock";
    public static final String SPEED = "speed";
    public static final String MEMORY = "memory";
    public static final String SERVER = "server";
    public static final String DAY = "day";
    public static final String MODULE_LIST = "module_list";
    public static final String NOTIFICATIONS = "notifications";
    public static final String WATERMARK = "watermark";
    public static final String KEYSTROKES = "keystrokes";
    public static final String ARMOR = "armor";
    public static final String POTIONS = "potions";

    // Visual
    public static final String NUMERIC_PING = "numeric_ping";
    public static final String CLIENT_BADGE = "client_badge";
    public static final String VIEW_MODEL = "view_model";
    public static final String LOW_FIRE = "low_fire";
    public static final String ZOOM = "zoom";
    public static final String NO_HURT_CAM = "no_hurt_cam";

    // Menus
    public static final String MENU_THEME = "menu_theme";
    public static final String APPEARANCE = "appearance";

    // Crew
    public static final String CREW = "crew";

    // Cosmetic
    public static final String CAPES = "capes";
}
