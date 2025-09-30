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
     * Добавляет новый узел и сразу строит с ним все допустимые рёбра
     *
     * @param pos позиция для нового узла
     * @return созданный узел
     */
    public Node addNodeWithEdges(BlockPos pos, String type) {
        Node newNode = this.nodeStorage.add(pos, type);
        // 使用新的蜂窝状连接算法重新计算所有连接
        rebuildEdgesWithHoneycombAlgorithm();
        this.markDirty();
        return newNode;
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
     * 检查新边是否会与现有边交叉。
     */
    private boolean wouldIntersectExistingEdges(Node nodeA, Node nodeB) {
        for (EdgeStorage.Edge e : edgeStorage.all().values()) {
            if (e.connects(nodeA.id()) || e.connects(nodeB.id())) continue;
            Node n1 = nodeStorage.all().get(e.nodeA());
            Node n2 = nodeStorage.all().get(e.nodeB());
            if (n1 == null || n2 == null) continue;

            if (GeometryUtils.segmentsIntersect2D(nodeA.pos(), nodeB.pos(), n1.pos(), n2.pos())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Пытается соединить два узла, запрещая «крестовые» рёбра.
     */
    public void connect(Node nodeA, Node nodeB) {
        if (nodeA == null || nodeB == null) {
            return;
        }
        String idNodeA = nodeA.id();
        String idNodeB = nodeB.id();
        if (idNodeA.equals(idNodeB)) {
            return;
        }

        // 1) проверяем радиус
        double dx = nodeA.pos().getX() - nodeB.pos().getX();
        double dz = nodeA.pos().getZ() - nodeB.pos().getZ();
        double max = edgeStorage.radius() * 2.0;
        if (dx * dx + dz * dz > max * max) {
            return;
        }

        // 2) уже существует?
        if (edgeStorage.all().containsKey(KeyUtil.edgeKey(idNodeA, idNodeB))) {
            return;
        }

        // 3) пересекает ли новое ребро какие-нибудь существующие?
        for (EdgeStorage.Edge e : edgeStorage.all().values()) {
            if (e.connects(idNodeA) || e.connects(idNodeB)) continue;
            Node n1 = nodeStorage.all().get(e.nodeA());
            Node n2 = nodeStorage.all().get(e.nodeB());
            if (n1 == null || n2 == null) continue;

            if (GeometryUtils.segmentsIntersect2D(nodeA.pos(), nodeB.pos(), n1.pos(), n2.pos())) {
                return;
            }
        }

        // 4) всё чисто — делегируем фактическое создание
        boolean added = edgeStorage.add(nodeA, nodeB);
        if (added) this.markDirty();
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
