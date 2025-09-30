package net.oxcodsnet.roadarchitect.util;

import net.minecraft.util.math.BlockPos;
import net.oxcodsnet.roadarchitect.storage.components.Node;

import java.util.*;

/**
 * 图连接算法，用于生成稀疏的道路网络。
 * <p>Graph connectivity algorithms for generating sparse road networks.</p>
 */
public class GraphConnectivityAlgorithm {

    /**
     * 使用最小生成树 (MST) 算法计算应该连接的节点对。
     * 这是最稀疏的连通图，只有 N-1 条边。
     * 
     * <p>Computes node pairs using Minimum Spanning Tree (MST) algorithm.
     * This is the sparsest connected graph with only N-1 edges.</p>
     *
     * @param nodes 所有节点的映射 (Map of all nodes)
     * @param maxDistance 最大连接距离（仅作参考，MST 会忽略以保证连通性）
     * @return 应该连接的节点对列表 (List of node pairs to connect)
     */
    public static List<NodePair> computeMinimumSpanningTree(Map<String, Node> nodes, double maxDistance) {
        if (nodes.size() <= 1) {
            return new ArrayList<>();
        }

        List<NodePair> edges = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<EdgeCandidate> candidates = new PriorityQueue<>(
            Comparator.comparingDouble(EdgeCandidate::distance)
        );

        // 从第一个节点开始
        Node startNode = nodes.values().iterator().next();
        visited.add(startNode.id());

        // 添加起始节点的所有边（不限制距离）
        for (Node other : nodes.values()) {
            if (!other.id().equals(startNode.id())) {
                double dist = distance2D(startNode.pos(), other.pos());
                candidates.add(new EdgeCandidate(startNode, other, dist));
            }
        }

        // Prim 算法
        while (!candidates.isEmpty() && visited.size() < nodes.size()) {
            EdgeCandidate edge = candidates.poll();
            
            // 如果目标节点已访问，跳过
            if (visited.contains(edge.to.id())) {
                continue;
            }

            // 添加这条边
            edges.add(new NodePair(edge.from, edge.to, edge.distance));
            visited.add(edge.to.id());

            // 添加新节点的所有边（不限制距离）
            for (Node other : nodes.values()) {
                if (!visited.contains(other.id())) {
                    double dist = distance2D(edge.to.pos(), other.pos());
                    candidates.add(new EdgeCandidate(edge.to, other, dist));
                }
            }
        }

        return edges;
    }

    /**
     * 使用增强型最小生成树算法：MST + 关键短边。
     * 在 MST 基础上添加少量短边以提高网络鲁棒性。
     * 
     * <p>Enhanced MST: adds a few short edges to improve network robustness.</p>
     *
     * @param nodes 所有节点的映射
     * @param maxDistance 最大连接距离
     * @param enhancementRatio 增强比例（0.0-1.0），0 表示纯 MST
     * @return 应该连接的节点对列表
     */
    public static List<NodePair> computeEnhancedMST(Map<String, Node> nodes, double maxDistance, double enhancementRatio) {
        // 先计算 MST
        List<NodePair> mstEdges = computeMinimumSpanningTree(nodes, maxDistance);
        
        if (enhancementRatio <= 0.0 || nodes.size() <= 2) {
            return mstEdges;
        }

        // 将 MST 边加入集合
        Set<String> existingEdges = new HashSet<>();
        for (NodePair pair : mstEdges) {
            existingEdges.add(pair.edgeKey());
        }

        // 找出所有可能的短边（不在 MST 中）
        List<NodePair> shortEdges = new ArrayList<>();
        List<Node> nodeList = new ArrayList<>(nodes.values());
        
        for (int i = 0; i < nodeList.size(); i++) {
            for (int j = i + 1; j < nodeList.size(); j++) {
                Node a = nodeList.get(i);
                Node b = nodeList.get(j);
                double dist = distance2D(a.pos(), b.pos());
                
                // 不限制距离，但优先选择短边
                String edgeKey = KeyUtil.edgeKey(a.id(), b.id());
                if (!existingEdges.contains(edgeKey)) {
                    shortEdges.add(new NodePair(a, b, dist));
                }
            }
        }

        // 按距离排序
        shortEdges.sort(Comparator.comparingDouble(NodePair::distance));

        // 添加最短的几条边
        int additionalEdges = (int) Math.ceil(mstEdges.size() * enhancementRatio);
        for (int i = 0; i < Math.min(additionalEdges, shortEdges.size()); i++) {
            mstEdges.add(shortEdges.get(i));
        }

        return mstEdges;
    }


    /**
     * 计算两个位置之间的 2D 距离（忽略 Y 坐标）。
     * <p>Computes 2D distance between two positions (ignoring Y coordinate).</p>
     */
    private static double distance2D(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * 节点对，表示应该连接的两个节点。
     * <p>Node pair representing two nodes that should be connected.</p>
     */
    public record NodePair(Node nodeA, Node nodeB, double distance) {
        public String edgeKey() {
            return KeyUtil.edgeKey(nodeA.id(), nodeB.id());
        }
    }

    /**
     * 边候选，用于 MST 算法。
     * <p>Edge candidate for MST algorithm.</p>
     */
    private record EdgeCandidate(Node from, Node to, double distance) {}
}
