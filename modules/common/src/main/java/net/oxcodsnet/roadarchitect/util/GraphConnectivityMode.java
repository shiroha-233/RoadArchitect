package net.oxcodsnet.roadarchitect.util;

/**
 * 图连接模式枚举。
 * <p>Graph connectivity mode enumeration.</p>
 */
public enum GraphConnectivityMode {
    /**
     * 最小生成树：最稀疏的连通网络，只有 N-1 条边。
     * <p>Minimum Spanning Tree: sparsest connected network with only N-1 edges.</p>
     */
    MST("mst", "Minimum Spanning Tree (Ultra Sparse)"),
    
    /**
     * 增强型 MST：MST + 少量短边，提高鲁棒性。
     * <p>Enhanced MST: MST + a few short edges for robustness.</p>
     */
    ENHANCED_MST("enhanced_mst", "Enhanced MST (Recommended)");

    private final String id;
    private final String displayName;

    GraphConnectivityMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static GraphConnectivityMode fromId(String id) {
        for (GraphConnectivityMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return ENHANCED_MST; // 默认使用增强型 MST
    }
}
