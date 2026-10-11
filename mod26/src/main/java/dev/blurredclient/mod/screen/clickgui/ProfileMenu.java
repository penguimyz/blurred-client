package dev.blurredclient.mod.screen.clickgui;

import dev.blurredclient.mod.config.BlurredConfig;
import dev.blurredclient.mod.module.Modules;
import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Skin;
import dev.blurredclient.mod.ui.kit.Ui;
import dev.blurredclient.mod.ui.kit.widget.Button;
import dev.blurredclient.mod.ui.kit.widget.TextField;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The profile picker that opens above the Profile button: every profile
 * (click to switch, × to delete), and a name box to make a new profile
 * (a copy of the current one) or rename the current one.
 */
final class ProfileMenu extends KitComponent {
    static final int WIDTH = 150;
    private static final int ROW = 15;
    private static final int PAD = 5;

    private final Runnable changed;
    private final TextField name;

    ProfileMenu(Runnable changed) {
        this.changed = changed;
        this.name = new TextField("", 24, s -> {}).placeholder("Profile name…");
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        List<String> names = Modules.REGISTRY.profileNames();
        String active = Modules.REGISTRY.activeProfile();
        for (String n : names) {
            Button pick = new Button((n.equals(active) ? "● " : "") + n, () -> {
                Modules.REGISTRY.switchProfile(n);
                done();
            });
            if (n.equals(active)) {
                pick.accent();
            }
            add(pick);
            Button delete = new Button("×", () -> {
                Modules.REGISTRY.deleteProfile(n);
                done();
            });
            delete.visible = names.size() > 1;
            delete.tooltip("Delete " + n);
            add(delete);
        }
        add(name);
        add(new Button("New", () -> {
            String n = name.text().isBlank() ? freeName() : name.text().trim();
            if (Modules.REGISTRY.duplicateProfile(n)) {
                name.setText("");
                done();
            }
        }).tooltip("A new profile, starting as a copy of this one."));
        add(new Button("Rename", () -> {
            if (!name.text().isBlank() && Modules.REGISTRY.renameProfile(Modules.REGISTRY.activeProfile(), name.text().trim())) {
                name.setText("");
                done();
            }
        }).tooltip("Rename the current profile to the name typed."));
    }

    private static String freeName() {
        List<String> names = Modules.REGISTRY.profileNames();
        for (int i = 2; ; i++) {
            String n = "Profile " + i;
            if (!names.contains(n)) {
                return n;
            }
        }
    }

    private void done() {
        BlurredConfig.save();
        changed.run();
        rebuild();
        // Grow or shrink upwards, staying anchored above the button.
        int nh = preferredHeight(w);
        setBounds(x, y + h - nh, w, nh);
    }

    @Override
    public int preferredHeight(int width) {
        return PAD * 2 + Modules.REGISTRY.profileNames().size() * (ROW + 2) + 4 + ROW + 2 + ROW;
    }

    @Override
    protected void layout() {
        int cy = y + PAD;
        List<KitComponent> kids = children();
        int i = 0;
        int profiles = Modules.REGISTRY.profileNames().size();
        for (int p = 0; p < profiles && i + 1 < kids.size(); p++) {
            kids.get(i++).setBounds(x + PAD, cy, w - PAD * 2 - ROW - 2, ROW);
            kids.get(i++).setBounds(x + w - PAD - ROW, cy, ROW, ROW);
            cy += ROW + 2;
        }
        cy += 4;
        kids.get(i++).setBounds(x + PAD, cy, w - PAD * 2, ROW);
        cy += ROW + 2;
        int half = (w - PAD * 2 - 2) / 2;
        kids.get(i++).setBounds(x + PAD, cy, half, ROW);
        kids.get(i).setBounds(x + PAD + half + 2, cy, half, ROW);
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        ui.skin.panel(ctx, x, y, w, h, Skin.Surface.POPUP);
    }
}
