package dev.blurredclient.mod.ui.kit.widget;

import dev.blurredclient.mod.ui.kit.KitComponent;
import dev.blurredclient.mod.ui.kit.Skins;
import dev.blurredclient.mod.ui.kit.Ui;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A one-line text input: the module search box, text settings, hex colours.
 *
 * <p>Caret editing with the keys people expect — arrows, Home/End,
 * Backspace/Delete, Ctrl to move or delete by word, Ctrl+V to paste — and the
 * text scrolls sideways to keep the caret in view. No selection: every field
 * this kit needs is short enough that it isn't missed.
 */
public class TextField extends KitComponent {
    private static final int PAD = 4;

    private final int maxLength;
    private final Consumer<String> onChange;
    private String placeholder = "";
    private Runnable onSubmit = () -> {};
    private String text;
    private int caret;
    private int scrollPx;
    private long lastEdit;
    private int padLeft = PAD;

    public TextField(String initial, int maxLength, Consumer<String> onChange) {
        this.text = initial == null ? "" : initial;
        this.maxLength = maxLength;
        this.onChange = onChange;
        this.caret = this.text.length();
    }

    public TextField placeholder(String hint) {
        this.placeholder = hint;
        return this;
    }

    /** Leave room on the left (for a search icon drawn by the owner). */
    public TextField padLeft(int px) {
        this.padLeft = px;
        return this;
    }

    public TextField onSubmit(Runnable action) {
        this.onSubmit = action;
        return this;
    }

    public String text() {
        return text;
    }

    /** Replace the text without firing {@code onChange} (e.g. syncing from a picker). */
    public void setText(String next) {
        text = next == null ? "" : next;
        caret = Math.min(caret, text.length());
    }

    @Override
    public boolean focusable() {
        return true;
    }

    private static int width(String s) {
        return Skins.current().width(Minecraft.getInstance().font, s);
    }

    @Override
    protected void draw(GuiGraphicsExtractor ctx, Ui ui) {
        boolean focused = ui.isFocused(this);
        float hov = hover.follow(ui.isHovered(this));
        ui.skin.field(ctx, x, y, w, h, hov, focused);

        int inner = w - padLeft - PAD;
        int caretPx = width(text.substring(0, caret));
        // Keep the caret inside the visible strip.
        if (caretPx - scrollPx > inner) {
            scrollPx = caretPx - inner;
        } else if (caretPx < scrollPx) {
            scrollPx = caretPx;
        }
        scrollPx = Math.max(0, Math.min(scrollPx, Math.max(0, width(text) - inner)));

        int ty = y + (h - ui.font.lineHeight) / 2 + 1;
        dev.blurredclient.mod.ui.Draw.scissor(ctx, x + padLeft - 1, y, x + w - PAD + 1, y + h);
        if (text.isEmpty() && !focused) {
            ui.skin.drawText(ctx, ui.font, placeholder, x + padLeft, ty, ui.skin.textFaint());
        } else {
            ui.skin.drawText(ctx, ui.font, text, x + padLeft - scrollPx, ty, ui.skin.textPrimary());
        }
        // Solid while typing, then blink — so the caret never vanishes mid-word.
        boolean blinkOn = (System.currentTimeMillis() - lastEdit) < 500
                || (System.currentTimeMillis() / 530) % 2 == 0;
        if (focused && blinkOn) {
            int cx = x + padLeft - scrollPx + caretPx;
            ctx.fill(cx, ty - 1, cx + 1, ty + ui.font.lineHeight, ui.skin.accent());
        }
        dev.blurredclient.mod.ui.Draw.unscissor(ctx);
    }

    @Override
    public boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        // Put the caret at the character boundary nearest the click.
        double local = mx - (x + padLeft) + scrollPx;
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            double d = Math.abs(width(text.substring(0, i)) - local);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        caret = best;
        lastEdit = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean onChar(String typed) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            // No control characters, and no § — it's Minecraft's formatting code.
            if (c >= 32 && c != 127 && c != '§') {
                clean.append(c);
            }
        }
        insert(clean.toString());
        return true;
    }

    private void insert(String s) {
        int room = maxLength - text.length();
        if (room <= 0 || s.isEmpty()) {
            return;
        }
        if (s.length() > room) {
            s = s.substring(0, room);
        }
        text = text.substring(0, caret) + s + text.substring(caret);
        caret += s.length();
        changed();
    }

    private void changed() {
        lastEdit = System.currentTimeMillis();
        onChange.accept(text);
    }

    @Override
    public boolean onKey(int key, int scancode, int modifiers) {
        boolean word = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER | GLFW.GLFW_MOD_ALT)) != 0;
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caret > 0) {
                    int from = word ? wordLeft(caret) : caret - 1;
                    text = text.substring(0, from) + text.substring(caret);
                    caret = from;
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caret < text.length()) {
                    int to = word ? wordRight(caret) : caret + 1;
                    text = text.substring(0, caret) + text.substring(to);
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                caret = word ? wordLeft(caret) : Math.max(0, caret - 1);
                lastEdit = System.currentTimeMillis();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                caret = word ? wordRight(caret) : Math.min(text.length(), caret + 1);
                lastEdit = System.currentTimeMillis();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                caret = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                caret = text.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.run();
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    onChar(clip.replace('\n', ' ').replace('\r', ' '));
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private int wordLeft(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }

    private int wordRight(int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        return i;
    }
}
