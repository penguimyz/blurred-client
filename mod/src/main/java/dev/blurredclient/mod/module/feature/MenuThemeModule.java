package dev.blurredclient.mod.module.feature;

import dev.blurredclient.mod.module.Category;
import dev.blurredclient.mod.module.Module;
import dev.blurredclient.mod.module.ModuleIds;
import dev.blurredclient.mod.setting.BoolSetting;
import dev.blurredclient.mod.social.Essential;

/**
 * Restyling Minecraft's own menus.
 *
 * <p>One switch on purpose: reskinning vanilla UI is the change most likely to
 * collide with another mod doing the same, so turning it off has to be one
 * obvious step rather than a hunt through sub-options.
 */
public final class MenuThemeModule extends Module {
    private final BoolSetting deferToEssential;
    private final BoolSetting customTitle;

    public MenuThemeModule() {
        super(ModuleIds.MENU_THEME, "Menu theme",
                "Restyle Minecraft's own menus. Container screens like the crafting table are always left alone.",
                Category.MENUS, true);
        this.deferToEssential = add(new BoolSetting("defer_to_essential", "Let Essential own the menus",
                "Essential restyles the main menu too. With this on, Blurred stands down so the two don't draw over each other.",
                true));
        deferToEssential.visibleIf(Essential::isPresent);
        this.customTitle = add(new BoolSetting("custom_title", "Blurred main menu",
                "Replace the title screen with Blurred's: big tiles on the left, account card, action bar.", true));
    }

    public boolean customTitle() {
        return customTitle.isOn();
    }

    public boolean deferToEssential() {
        return deferToEssential.isOn();
    }
}
