package dev.blurredclient.mod.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudPlacementTest {
    @Test
    void rightAnchorKeepsDistanceFromRightEdgeAcrossResolutions() {
        HudPlacement p = new HudPlacement(HudPlacement.Anchor.TOP_RIGHT, -4, 4);
        // Module 50 wide: its right edge should sit 4px in from the screen edge.
        assertEquals(960 - 50 - 4, p.left(960, 50));
        assertEquals(640 - 50 - 4, p.left(640, 50));
        assertEquals(4, p.top(540, 10));
    }

    @Test
    void centerAnchorCentres() {
        HudPlacement p = new HudPlacement(HudPlacement.Anchor.CENTER, 0, 0);
        assertEquals(400, p.left(1000, 200));
        assertEquals(240, p.top(500, 20));
    }

    @Test
    void clampsOnScreen() {
        HudPlacement p = new HudPlacement(HudPlacement.Anchor.TOP_LEFT, -500, 9000);
        assertEquals(0, p.left(800, 100));
        assertEquals(600 - 30, p.top(600, 30));
        // A module bigger than the screen pins to 0 rather than going negative.
        assertEquals(0, p.left(100, 300));
    }

    @Test
    void nearestPicksAnchorByThirdAndPreservesPosition() {
        int sw = 900;
        int sh = 600;
        // Dropped in the bottom-right third.
        HudPlacement p = HudPlacement.nearest(800, 550, 60, 20, sw, sh, 1.5f);
        assertEquals(HudPlacement.Anchor.BOTTOM_RIGHT, p.anchor);
        assertEquals(1.5f, p.scale);
        assertEquals(800, p.left(sw, 60));
        assertEquals(550, p.top(sh, 20));

        // Top-middle.
        HudPlacement q = HudPlacement.nearest(420, 10, 60, 20, sw, sh, 1f);
        assertEquals(HudPlacement.Anchor.TOP_CENTER, q.anchor);
        assertEquals(420, q.left(sw, 60));
    }
}
