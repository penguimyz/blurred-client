package dev.blurredclient.mod.ui.kit;

/** A component that only holds others. Clicks on its empty space fall through. */
public class Container extends KitComponent {
    @Override
    protected boolean hitsSelf() {
        return false;
    }
}
