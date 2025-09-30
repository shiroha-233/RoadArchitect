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
import net.oxcodsnet.roadarchitect.util.KeyUtil;
import net.oxcodsnet.roadarchitect.util.PersistentStateUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сохраняет узлы и рёбра дорог как {@link PersistentState}.
 * <p>Stores road nodes and edges as a {@link PersistentState}.</p>
 */
public class RoadGraphState extends PersistentState {
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
     * Добавляет новый узел и строит с ним оптимальные рёбра используя K-ближайших соседей
     *
     * @param pos позиция для нового узла
     * @return созданный узел
     */
    public Node addNodeWithEdges(BlockPos pos, String type) {
        // Сначала найдем ближайших соседей ПЕРЕД добавлением нового узла
        int maxConnections = RoadArchitect.CONFIG.maxNearestConnections();
        List<Node> nearestNeighbors = findKNearestNeighborsForPosition(pos, maxConnections);
        
        // Теперь добавляем новый узел
        Node newNode = this.nodeStorage.add(pos, type);
        
        System.out.println("[RoadArchitect] Adding node at " + pos + ", found " + nearestNeighbors.size() + " potential neighbors, maxConnections=" + maxConnections);
        
        // подключаем новый узел к ближайшим соседям
        int connectionsAdded = 0;
        for (Node neighbor : nearestNeighbors) {
            // 额外的安全检查：确保不连接到自己
            if (neighbor.pos().equals(newNode.pos())) {
                System.out.println("[RoadArchitect] Skipping self-connection for " + newNode.pos());
                continue;
            }
            
            double distance = Math.sqrt(distanceSquared(newNode.pos(), neighbor.pos()));
            System.out.println("[RoadArchitect] Attempting to connect " + newNode.pos() + " to " + neighbor.pos() + " at distance " + distance);
            
            // 直接连接，不再使用canConnect检查，因为我们已经在findKNearestNeighbors中过滤了
            edgeStorage.add(newNode, neighbor);
            connectionsAdded++;
            System.out.println("[RoadArchitect] Successfully connected " + newNode.pos() + " to " + neighbor.pos());
        }
        
        System.out.println("[RoadArchitect] Added " + connectionsAdded + " connections for node at " + pos);
        
        // Важно: обновляем существующие соединения с учетом нового узла
        updateExistingConnections(newNode);
        
        this.markDirty();
        return newNode;
    }

    /**
     * 为指定位置找到K个最近的邻居节点（用于添加新节点前）
     */
    private List<Node> findKNearestNeighborsForPosition(BlockPos pos, int k) {
        List<Node> neighbors = nodeStorage.all().values().stream()
            .filter(n -> {
                double dist = Math.sqrt(distanceSquared(pos, n.pos()));
                return dist <= RoadArchitect.CONFIG.maxConnectionDistance(); // 只考虑距离内的节点
            })
            .sorted((a, b) -> Double.compare(
                distanceSquared(pos, a.pos()),
                distanceSquared(pos, b.pos())
            ))
            .limit(k)
            .collect(Collectors.toList());
        
        System.out.println("[RoadArchitect] Found " + neighbors.size() + " valid neighbors within distance for " + pos);
        return neighbors;
    }

    /**
     * Находит K ближайших соседей для заданного узла
     */
    private List<Node> findKNearestNeighbors(Node target, int k) {
        List<Node> neighbors = nodeStorage.all().values().stream()
            .filter(n -> !n.id().equals(target.id())) // 排除自身
            .filter(n -> {
                double dist = Math.sqrt(distanceSquared(target.pos(), n.pos()));
                return dist <= RoadArchitect.CONFIG.maxConnectionDistance(); // 只考虑距离内的节点
            })
            .sorted((a, b) -> Double.compare(
                distanceSquared(target.pos(), a.pos()),
                distanceSquared(target.pos(), b.pos())
            ))
            .limit(k)
            .collect(Collectors.toList());
        
        System.out.println("[RoadArchitect] Found " + neighbors.size() + " valid neighbors within distance for " + target.pos());
        return neighbors;
    }

    /**
     * Вычисляет квадрат расстояния между двумя позициями (для оптимизации)
     */
    private double distanceSquared(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Проверяет, можно ли соединить два узла (расстояние + пересечения)
     */
    private boolean canConnect(Node nodeA, Node nodeB) {
        if (nodeA == null || nodeB == null) return false;
        if (nodeA.id().equals(nodeB.id())) return false;

        // Проверяем расстояние
        double maxDist = RoadArchitect.CONFIG.maxConnectionDistance();
        if (distanceSquared(nodeA.pos(), nodeB.pos()) > maxDist * maxDist) {
            return false;
        }

        // Проверяем пересечения с существующими рёбрами
        String idA = nodeA.id();
        String idB = nodeB.id();
        
        for (EdgeStorage.Edge e : edgeStorage.all().values()) {
            if (e.connects(idA) || e.connects(idB)) continue;
            Node n1 = nodeStorage.all().get(e.nodeA());
            Node n2 = nodeStorage.all().get(e.nodeB());
            if (n1 == null || n2 == null) continue;

            if (GeometryUtils.segmentsIntersect2D(nodeA.pos(), nodeB.pos(), n1.pos(), n2.pos())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Обновляет существующие соединения с учетом нового узла
     * Если новый узел ближе к существующему узлу, чем его текущие дальние соседи,
     * заменяем соединение
     */
    private void updateExistingConnections(Node newNode) {
        int maxConnections = RoadArchitect.CONFIG.maxNearestConnections();
        
        for (Node existing : nodeStorage.all().values()) {
            if (existing.id().equals(newNode.id())) continue;
            
            // Получаем текущих соседей существующего узла
            List<Node> currentNeighbors = getConnectedNeighbors(existing);
            double distToNew = distanceSquared(existing.pos(), newNode.pos());
            
            // Если у узла уже максимум соединений, проверяем замену
            if (currentNeighbors.size() >= maxConnections) {
                Node farthest = getFarthestNeighbor(existing, currentNeighbors);
                if (farthest != null) {
                    double farthestDist = distanceSquared(existing.pos(), farthest.pos());
                    
                    // Если новый узел ближе и можно подключить, заменяем соединение
                    if (distToNew < farthestDist && canConnect(existing, newNode)) {
                        String edgeKey = KeyUtil.edgeKey(existing.id(), farthest.id());
                        edgeStorage.remove(edgeKey);
                        edgeStorage.add(existing, newNode);
                    }
                }
            } else if (canConnect(existing, newNode)) {
                // Если есть свободные слоты, просто добавляем соединение
                edgeStorage.add(existing, newNode);
            }
        }
    }

    /**
     * Получает список соседей, подключенных к данному узлу
     */
    private List<Node> getConnectedNeighbors(Node node) {
        List<Node> neighbors = new ArrayList<>();
        String nodeId = node.id();
        
        for (EdgeStorage.Edge edge : edgeStorage.all().values()) {
            if (edge.connects(nodeId)) {
                String otherId = edge.nodeA().equals(nodeId) ? edge.nodeB() : edge.nodeA();
                Node other = nodeStorage.all().get(otherId);
                if (other != null) {
                    neighbors.add(other);
                }
            }
        }
        
        return neighbors;
    }

    /**
     * Находит самого дальнего соседа среди подключенных узлов
     */
    private Node getFarthestNeighbor(Node center, List<Node> neighbors) {
        if (neighbors.isEmpty()) return null;
        
        return neighbors.stream()
            .max((a, b) -> Double.compare(
                distanceSquared(center.pos(), a.pos()),
                distanceSquared(center.pos(), b.pos())
            ))
            .orElse(null);
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
