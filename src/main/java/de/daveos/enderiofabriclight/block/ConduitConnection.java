package de.daveos.enderiofabriclight.block;

import net.minecraft.util.StringRepresentable;

/** What a conduit side is attached to. Drives both the model (arm vs. connector plate) and the hitbox. */
public enum ConduitConnection implements StringRepresentable {
    NONE("none"),
    /** Another conduit: plain tube with a coupling ring. */
    PIPE("pipe"),
    /** Storage block or terminal: tube ending in a connector plate. */
    PLUG("plug"),
    /** Switched off with the wrench; survives neighbour updates until switched on again. */
    DISABLED("disabled");

    private final String name;

    ConduitConnection(String name) {
        this.name = name;
    }

    public boolean isConnected() {
        return this == PIPE || this == PLUG;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
