package dev.blurredclient.mod.setting;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingTest {
    enum Mode { A, B, C }

    @Test
    void sliderSnapsAndClamps() {
        SliderSetting s = new SliderSetting("k", "K", "", 1.0, 0.5, 2.0, 0.1, "x");
        s.set(1.234);
        assertEquals(1.2, s.get(), 1e-9);
        s.set(9.0);
        assertEquals(2.0, s.get(), 1e-9);
        s.set(-3.0);
        assertEquals(0.5, s.get(), 1e-9);
        s.set(0.8);
        // Float noise from the step multiply must not survive the snap.
        assertEquals("0.8", s.get().toString());
    }

    @Test
    void sliderFractionRoundTrips() {
        SliderSetting s = new SliderSetting("k", "K", "", 0, 0, 100, 1, "%");
        s.setFraction(0.25);
        assertEquals(25, s.intValue());
        assertEquals(0.25, s.fraction(), 1e-9);
    }

    @Test
    void wrongJsonShapeKeepsCurrentValue() {
        BoolSetting b = new BoolSetting("k", "K", "", true);
        b.fromJson(new JsonPrimitive("yes"));
        assertTrue(b.isOn());
        b.fromJson(null);
        assertTrue(b.isOn());

        SliderSetting s = new SliderSetting("k", "K", "", 1, 0, 2, 1, "");
        s.fromJson(new JsonPrimitive("big"));
        assertEquals(1.0, s.get());
    }

    @Test
    void modeStoresByNameAndIgnoresUnknown() {
        ModeSetting<Mode> m = new ModeSetting<>("k", "K", "", Mode.A, Mode::name);
        m.fromJson(new JsonPrimitive("C"));
        assertEquals(Mode.C, m.get());
        m.fromJson(new JsonPrimitive("Z"));
        assertEquals(Mode.C, m.get());
        assertEquals("C", m.toJson().getAsString());
        m.cycle();
        assertEquals(Mode.A, m.get());
    }

    @Test
    void colorParsesBothHexForms() {
        assertEquals(0xFF35E0D0, ColorSetting.parseHex("#35E0D0"));
        assertEquals(0x8035E0D0, ColorSetting.parseHex("8035E0D0"));
        assertNull(ColorSetting.parseHex("#12345"));
        assertNull(ColorSetting.parseHex("#GGGGGG"));

        ColorSetting c = new ColorSetting("k", "K", "", 0xFF000000);
        c.fromJson(new JsonPrimitive("#4035E0D0"));
        assertEquals("#4035E0D0", c.toJson().getAsString());
    }

    @Test
    void textIsCappedAndVisibilityFollowsCondition() {
        TextSetting t = new TextSetting("k", "K", "", "", 4);
        t.set("abcdefg");
        assertEquals("abcd", t.get());

        boolean[] flag = {false};
        t.visibleIf(() -> flag[0]);
        assertFalse(t.isVisible());
        flag[0] = true;
        assertTrue(t.isVisible());
    }
}
