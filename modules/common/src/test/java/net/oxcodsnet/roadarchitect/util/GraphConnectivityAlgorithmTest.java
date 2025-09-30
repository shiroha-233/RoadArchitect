package net.oxcodsnet.roadarchitect.util;

import net.minecraft.util.math.BlockPos;
import net.oxcodsnet.roadarchitect.storage.components.Node;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图连接算法测试 - MST 版本。
 */
class GraphConnectivityAlgorithmTest {

    /**
     * 创建一个简单的测试节点集合（3x3 网格）。
     */
    private Map<String, Node> createTestNodes() {
        Map<String, Node> nodes = new HashMap<>();
        int spacing = 100;
        
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                BlockPos pos = new BlockPos(x * spacing, 64, z * spacing);
                String id = "node_" + x + "_" + z;
                nodes.put(id, new Node(id, pos, "test"));
            }
        }
        
        return nodes;
    }

    @Test
    void testMinimumSpanningTree() {
        Map<String, Node> nodes = createTestNodes();
        double maxDistance = 500.0;
        
        List<GraphConnectivityAlgorithm.NodePair> edges = 
            GraphConnectivityAlgorithm.computeMinimumSpanningTree(nodes, maxDistance);
        
        assertNotNull(edges);
        assertFalse(edges.isEmpty(), "MST should produce edges");
        
        // MST 应该恰好有 N-1 条边
        assertEquals(nodes.size() - 1, edges.size(), 
            "MST should have exactly N-1 edges");
        
        System.out.println("MST: " + edges.size() + " edges for " + nodes.size() + " nodes");
    }

    @Test
    void testEnhancedMST() {
        Map<String, Node> nodes = createTestNodes();
        double maxDistance = 500.0;
        double enhancementRatio = 0.2;
        
        List<GraphConnectivityAlgorithm.NodePair> edges = 
            GraphConnectivityAlgorithm.computeEnhancedMST(nodes, maxDistance, enhancementRatio);
        
        assertNotNull(edges);
        assertFalse(edges.isEmpty(), "Enhanced MST should produce edges");
        
        // 增强型 MST 应该比纯 MST 多一些边
        int mstEdges = nodes.size() - 1;
        assertTrue(edges.size() >= mstEdges, 
            "Enhanced MST should have at least MST edges");
        
        System.out.println("Enhanced MST: " + edges.size() + " edges for " + nodes.size() + " nodes");
    }

    @Test
    void testMSTConnectivity() {
        Map<String, Node> nodes = createTestNodes();
        double maxDistance = 500.0;
        
        List<GraphConnectivityAlgorithm.NodePair> edges = 
            GraphConnectivityAlgorithm.computeMinimumSpanningTree(nodes, maxDistance);
        
        // 验证连通性：应该能从任意节点到达其他所有节点
        // 简单验证：边数应该是 N-1
        assertEquals(nodes.size() - 1, edges.size());
    }

    @Test
    void testMSTSparseness() {
        Map<String, Node> nodes = createTestNodes();
        double maxDistance = 500.0;
        
        List<GraphConnectivityAlgorithm.NodePair> mstEdges = 
            GraphConnectivityAlgorithm.computeMinimumSpanningTree(nodes, maxDistance);
        
        // MST 应该是最稀疏的连通图
        int maxPossibleEdges = nodes.size() * (nodes.size() - 1) / 2;
        assertTrue(mstEdges.size() < maxPossibleEdges / 2, 
            "MST should be much sparser than complete graph");
        
        System.out.println("MST sparseness: " + mstEdges.size() + " / " + maxPossibleEdges + " edges");
    }

    @Test
    void testEdgeDistances() {
        Map<String, Node> nodes = createTestNodes();
        double maxDistance = 500.0;
        
        List<GraphConnectivityAlgorithm.NodePair> edges = 
            GraphConnectivityAlgorithm.computeMinimumSpanningTree(nodes, maxDistance);
        
        // 所有边的距离应该在最大距离内
        for (GraphConnectivityAlgorithm.NodePair edge : edges) {
            assertTrue(edge.distance() <= maxDistance, 
                "Edge distance should not exceed max distance");
        }
    }

    @Test
    void testEmptyNodes() {
        Map<String, Node> nodes = new HashMap<>();
        double maxDistance = 500.0;
        
        List<GraphConnectivityAlgorithm.NodePair> edges = 
            GraphConnectivityAlgorithm.computeMinimumSpanningTree(nodes, maxDistance);
        
        assertTrue(edges.isEmpty(), "Empty node set should produce no edges");
    }

    @Test
    void testSingleNode() {
        Map<String, Node> nodes = new HashMap<>();
        nodes.put("node1", new Node("node1", new BlockPos(0, 64, 0), "test"));
        double maxDistance = 500.0;
        
        List<GraphConnectivityAlgorithm.NodePair> edges = 
            GraphConnectivityAlgorithm.computeMinimumSpanningTree(nodes, maxDistance);
        
        assertTrue(edges.isEmpty(), "Single node should produce no edges");
    }

    @Test
    void testEnhancementRatio() {
        Map<String, Node> nodes = createTestNodes();
        double maxDistance = 500.0;
        
        // 测试不同的增强比例
        List<GraphConnectivityAlgorithm.NodePair> mst = 
            GraphConnectivityAlgorithm.computeEnhancedMST(nodes, maxDistance, 0.0);
        List<GraphConnectivityAlgorithm.NodePair> enhanced20 = 
            GraphConnectivityAlgorithm.computeEnhancedMST(nodes, maxDistance, 0.2);
        List<GraphConnectivityAlgorithm.NodePair> enhanced50 = 
            GraphConnectivityAlgorithm.computeEnhancedMST(nodes, maxDistance, 0.5);
        
        // 增强比例越高，边数越多
        assertTrue(mst.size() <= enhanced20.size());
        assertTrue(enhanced20.size() <= enhanced50.size());
        
        System.out.println("Enhancement comparison:");
        System.out.println("  0%: " + mst.size() + " edges");
        System.out.println("  20%: " + enhanced20.size() + " edges");
        System.out.println("  50%: " + enhanced50.size() + " edges");
    }
}
