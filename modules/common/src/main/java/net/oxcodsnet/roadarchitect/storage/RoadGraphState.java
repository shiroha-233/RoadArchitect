package net.oxcodsnet.roadarchitect.storage;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.oxcodsnet.roadarchitect.RoadArchitect;
import net.oxcodsnet.roadarchitect.storage.components.Node;
import net.oxcodsnet.roadarchitect.util.GeometryUtils;
import net.oxcodsnet.roadarchitect.util.GraphConnectivityAlgorithm;
import net.oxcodsnet.roadarchitect.util.GraphConnectivityMode;
import net.oxcodsnet.roadarchitect.util.KeyUtil;
import net.oxcodsnet.roadarchitect.util.PersistentStateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Сохраняет узлы и рёбра дорог как {@link PersistentState}.
 * <p>Stores road nodes and edges as a {@link PersistentState}.</p>
 */
public class RoadGraphState extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadArchitect.MOD_ID + "/RoadGraphState");
    private static final String KEY = "road_graph";
    private static final String NODES_KEY = "nodes";
    private static final String EDGES_KEY = "edges";
    private static final String RADIUS_KEY = "radius";

    public static final Type<RoadGraphState> TYPE = new Type<>(
            () -> new RoadGraphState(RoadArchitect.CONFIG.maxConnectionDistance()),
            RoadGraphState::fromNbt,
            DataFixTypes.SAVED_DATA_SCOREBOARD
    );

    private final NodeStorage nodeStorage;
    private final EdgeStorage edgeStorage;

    /**
     * Создает новое состояние графа дорог с указанным радиусом соединений.
     * <p>Creates a new road graph state with the given connection radius.</p>
     */
    public RoadGraphState(double radius) {
        this(new NodeStorage(), new EdgeStorage(radius));
    }

    /**
     * Внутренний конструктор с заданными хранилищами.
     * <p>Internal constructor using the provided storages.</p>
     */
    private RoadGraphState(NodeStorage nodes, EdgeStorage edges) {
        this.nodeStorage = nodes;
        this.edgeStorage = edges;
    }


    /**
     * Получает или создает состояние графа для мира.
     * <p>Gets or creates the road graph state for the given world.</p>
     */
    public static RoadGraphState get(ServerWorld world) {
        return PersistentStateUtil.get(world, TYPE, KEY);
    }

    /*========== helpers ==========*/

    /**
     * Восстанавливает состояние графа из NBT.
     * <p>Restores the road graph state from NBT.</p>
     */
    public static RoadGraphState fromNbt(NbtCompound tag, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        double radius = tag.getDouble(RADIUS_KEY);
        NodeStorage nodes = NodeStorage.fromNbt(tag.getList(NODES_KEY, NbtElement.COMPOUND_TYPE));
        EdgeStorage edges = EdgeStorage.fromNbt(tag.getCompound(EDGES_KEY), radius);
        return new RoadGraphState(nodes, edges);
    }

    /**
     * Возвращает хранилище узлов.
     * <p>Returns the node storage.</p>
     */
    public NodeStorage nodes() {
        return nodeStorage;
    }

    /**
     * Возвращает хранилище рёбер.
     * <p>Returns the edge storage.</p>
     */
    public EdgeStorage edges() {
        return edgeStorage;
    }

    /**
     * 添加新节点并增量连接到现有网络。
     * 使用 MST 策略：连接到最近的已有节点。
     *
     * @param pos 节点位置
     * @param type 节点类型
     * @return 创建的节点，如果节点已存在则返回 null
     */
    public Node addNode(BlockPos pos, String type) {
        // 检查该位置是否已有节点
        for (Node existing : nodeStorage.all().values()) {
            if (existing.pos().equals(pos)) {
                return null; // 该位置已有节点，不添加
            }
        }
        
        Node newNode = this.nodeStorage.add(pos, type);
        
        // 增量连接：找到最近的节点并连接
        connectToNearestNode(newNode);
        
        this.markDirty();
        return newNode;
    }

    /**
     * 将新节点连接到最近的现有节点（MST 增量策略）。
     * 
     * @param newNode 新添加的节点
     */
    private void connectToNearestNode(Node newNode) {
        Node nearestNode = null;
        double minDistance = Double.MAX_VALUE;
        
        // 找到最近的节点
        for (Node other : nodeStorage.all().values()) {
            if (other.id().equals(newNode.id())) continue;
            
            double dx = newNode.pos().getX() - other.pos().getX();
            double dz = newNode.pos().getZ() - other.pos().getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist < minDistance) {
                minDistance = dist;
                nearestNode = other;
            }
        }
        
        // 连接到最近的节点
        if (nearestNode != null) {
            edgeStorage.add(newNode, nearestNode);
            LOGGER.debug("Connected new node {} to nearest node {} (distance: {:.1f})", 
                newNode.id(), nearestNode.id(), minDistance);
        }
    }

    /**
     * @deprecated 不再使用，改用增量添加
     */
    @Deprecated
    public Node addNodeWithEdges(BlockPos pos, String type) {
        return addNode(pos, type);
    }

    /**
     * 使用稀疏算法重新构建所有边。
     * 清除现有边并使用增强型 MST 算法重新连接。
     * 
     * <p>Rebuilds all edges using sparse algorithm.
     * Clears existing edges and reconnects using Enhanced MST algorithm.</p>
     */
    public void rebuildEdgesWithHoneycombAlgorithm() {
        rebuildEdgesWithAlgorithm(GraphConnectivityMode.ENHANCED_MST);
    }

    /**
     * 使用指定算法重新构建所有边。
     * 
     * <p>Rebuilds all edges using the specified algorithm.</p>
     * 
     * @param mode 连接算法模式
     */
    public void rebuildEdgesWithAlgorithm(GraphConnectivityMode mode) {
        LOGGER.info("Rebuilding road network using {} algorithm", mode.getDisplayName());
        
        // 保存现有边的状态
        var existingStatuses = edgeStorage.allWithStatus();
        int oldEdgeCount = existingStatuses.size();
        
        // 清除所有边（但保留节点）
        edgeStorage.clear();
        
        // 根据模式选择算法
        List<GraphConnectivityAlgorithm.NodePair> newEdges;
        double maxDist = edgeStorage.radius() * 2.0;
        
        switch (mode) {
            case MST -> newEdges = GraphConnectivityAlgorithm.computeMinimumSpanningTree(
                nodeStorage.all(), maxDist);
            case ENHANCED_MST -> newEdges = GraphConnectivityAlgorithm.computeEnhancedMST(
                nodeStorage.all(), maxDist, 0.2); // 增加 20% 的短边
            default -> {
                LOGGER.warn("Unknown connectivity mode: {}, using ENHANCED_MST", mode);
                newEdges = GraphConnectivityAlgorithm.computeEnhancedMST(
                    nodeStorage.all(), maxDist, 0.2);
            }
        }
        
        // 添加新边，并尝试恢复之前的状态
        int addedCount = 0;
        int restoredCount = 0;
        
        for (GraphConnectivityAlgorithm.NodePair pair : newEdges) {
            // MST 算法已经保证不会有交叉，直接添加
            String edgeKey = pair.edgeKey();
            edgeStorage.add(pair.nodeA(), pair.nodeB());
            addedCount++;
            
            // 如果这条边之前存在，恢复其状态
            if (existingStatuses.containsKey(edgeKey)) {
                edgeStorage.setStatus(edgeKey, existingStatuses.get(edgeKey));
                restoredCount++;
            }
        }
        
        LOGGER.info("Network rebuild complete: {} -> {} edges ({} restored)", 
            oldEdgeCount, addedCount, restoredCount);
    }


    /**
     * Сохраняет состояние в NBT.
     * <p>Writes this state into an NBT compound.</p>
     */
    @Override
    public NbtCompound writeNbt(NbtCompound tag, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        tag.putDouble(RADIUS_KEY, edgeStorage.radius());
        tag.put(NODES_KEY, nodeStorage.toNbt());
        tag.put(EDGES_KEY, edgeStorage.toNbt());
        return tag;
    }
}
