package de.daveos.enderiofabriclight.block;

import net.minecraft.util.StringRepresentable;

/** What a conduit side is attached to. Drives both the model (arm vs. connector plate) and the hitbox. */
public enum ConduitConnection implements StringRepresentable {
    NONE("none"),
    /** Another conduit: plain tube with a coupling ring. */
    PIPE("pipe"),
    /** Storage block or terminal: tube ending in a connector plate. */
    PLUG("plug");

    private final String name;

    ConduitConnection(String name) {
        this.name = name;
    }

    public boolean isConnected() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
