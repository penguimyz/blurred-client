package dev.blurredclient.mod.module;

/** The sections of the mod menu's left rail, in display order. */
public enum Category {
    HUD("HUD", true),
    VISUAL("Visual", true),
    EFFECTS("Effects", true),
    COMBAT("Combat", true),
    PERFORMANCE("Performance", false),
    MENUS("Menus", false),
    UTILITY("Utility", true),
    CREW("Crew", false),
    COSMETIC("Cosmetics", false);

    private final String label;
    private final boolean perProfile;

    Category(String label, boolean perProfile) {
        this.label = label;
        this.perProfile = perProfile;
    }

    public String label() {
        return label;
    }

    /**
     * Whether modules in this category belong to a profile by default.
     *
     * <p>HUD layouts and visuals are what you'd want different for PvP and for
     * building. Crew sharing, menus and capes are about you, not the activity,
     * so they stay the same whichever profile is active.
     */
    public boolean perProfile() {
        return perProfile;
    }
}
