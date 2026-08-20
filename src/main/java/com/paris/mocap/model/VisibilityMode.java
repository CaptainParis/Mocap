package com.paris.mocap.model;

public enum VisibilityMode {
    ALL("Visible to Everyone"),
    OPS_ONLY("Visible to OPs Only"),
    NO_OPS("Visible to Non-OPs");

    private final String label;

    VisibilityMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public VisibilityMode next() {
        VisibilityMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
