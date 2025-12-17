package cn.sh1rocu.tacz.api;

/**
 * TACZ API Stub - for compilation only
 */
public enum LogicalSide {
    CLIENT,
    SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
