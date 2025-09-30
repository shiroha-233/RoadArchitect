// 测试K-最近邻算法的简单验证代码
// 这个文件用于验证修复后的连接逻辑

import java.util.*;
import java.util.stream.Collectors;

public class TEST_K_NEAREST_ALGORITHM {
    
    // 模拟节点位置
    static class TestNode {
        String id;
        int x, z;
        
        TestNode(String id, int x, int z) {
            this.id = id;
            this.x = x;
            this.z = z;
        }
        
        double distanceSquaredTo(TestNode other) {
            double dx = this.x - other.x;
            double dz = this.z - other.z;
            return dx * dx + dz * dz;
        }
        
        @Override
        public String toString() {
            return id + "(" + x + "," + z + ")";
        }
    }
    
    // 模拟K-最近邻算法
    static List<TestNode> findKNearestNeighbors(TestNode target, List<TestNode> allNodes, int k) {
        return allNodes.stream()
            .filter(n -> !n.id.equals(target.id))
            .sorted((a, b) -> Double.compare(
                target.distanceSquaredTo(a),
                target.distanceSquaredTo(b)
            ))
            .limit(k)
            .collect(Collectors.toList());
    }
    
    public static void main(String[] args) {
        // 测试场景1: 线性排列的村庄
        System.out.println("=== 测试场景1: 线性排列 ===");
        List<TestNode> linearNodes = Arrays.asList(
            new TestNode("A", 0, 0),
            new TestNode("B", 50, 0),
            new TestNode("C", 100, 0)
        );
        
        for (TestNode node : linearNodes) {
            List<TestNode> neighbors = findKNearestNeighbors(node, linearNodes, 2);
            System.out.println(node + " -> " + neighbors);
        }
        
        // 测试场景2: 三角形排列
        System.out.println("\n=== 测试场景2: 三角形排列 ===");
        List<TestNode> triangleNodes = Arrays.asList(
            new TestNode("A", 0, 0),
            new TestNode("B", 50, 50),
            new TestNode("C", 100, 0)
        );
        
        for (TestNode node : triangleNodes) {
            List<TestNode> neighbors = findKNearestNeighbors(node, triangleNodes, 2);
            System.out.println(node + " -> " + neighbors);
        }
        
        // 测试场景3: 复杂网络（模拟原始蜘蛛网问题）
        System.out.println("\n=== 测试场景3: 复杂网络 ===");
        List<TestNode> complexNodes = Arrays.asList(
            new TestNode("Village1", 0, 0),
            new TestNode("Village2", 30, 40),
            new TestNode("Village3", 80, 20),
            new TestNode("Village4", 120, 60),
            new TestNode("Village5", 60, 80),
            new TestNode("Village6", 150, 100)
        );
        
        System.out.println("使用K=3的最近邻连接:");
        for (TestNode node : complexNodes) {
            List<TestNode> neighbors = findKNearestNeighbors(node, complexNodes, 3);
            System.out.println(node + " -> " + neighbors);
        }
        
        // 对比：如果连接所有节点会怎样
        System.out.println("\n对比 - 如果连接所有节点(旧算法):");
        for (TestNode node : complexNodes) {
            List<TestNode> allOthers = complexNodes.stream()
                .filter(n -> !n.id.equals(node.id))
                .collect(Collectors.toList());
            System.out.println(node + " -> " + allOthers.size() + " 个连接: " + allOthers);
        }
        
        // 验证距离限制
        System.out.println("\n=== 距离限制测试 (maxDistance=400) ===");
        TestNode farNode = new TestNode("FarVillage", 500, 500);
        List<TestNode> testWithFar = new ArrayList<>(complexNodes);
        testWithFar.add(farNode);
        
        for (TestNode node : testWithFar) {
            List<TestNode> neighbors = findKNearestNeighbors(node, testWithFar, 3)
                .stream()
                .filter(n -> node.distanceSquaredTo(n) <= 400 * 400) // 距离限制
                .collect(Collectors.toList());
            System.out.println(node + " -> " + neighbors + " (在400块范围内)");
        }
    }
}