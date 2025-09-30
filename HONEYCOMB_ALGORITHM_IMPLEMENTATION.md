# 蜂窝状道路网络算法实现

## 📋 实现概述

本次更新实现了新的图连接算法，用于生成更加稀疏、蜂窝状的道路网络，解决了原有算法产生过于密集网络的问题。

## 🎯 问题描述

**原有问题**:
- 道路网络过于密集
- 存在大量交叉和重复连接
- 远距离结构之间有多条冗余路径
- 网络呈现"蜘蛛网"状而非"蜂窝"状

**期望效果**:
- 相邻村庄互相连接
- 呈现蜂窝状结构
- 避免多次重复链接更远的结构
- 减少道路交叉

## 🔧 实现的算法

### 1. Gabriel 图算法 (默认)
**原理**: 如果以两个节点为直径的圆内不包含其他节点，则连接这两个节点。

**特点**:
- 生成蜂窝状网络
- 平衡了连通性和稀疏性
- 是 Delaunay 三角剖分的子图
- 每个节点平均 3-4 条边

**代码位置**: `GraphConnectivityAlgorithm.computeGabrielGraph()`

### 2. 相对邻域图 (RNG)
**原理**: 只连接"相对接近"的节点对，即不存在第三个节点同时比两个节点都近。

**特点**:
- 更加稀疏
- 是 Gabriel 图的子图
- 每个节点平均 2-3 条边

**代码位置**: `GraphConnectivityAlgorithm.computeRelativeNeighborhoodGraph()`

### 3. K 最近邻 (KNN)
**原理**: 每个节点只连接到最近的 K 个邻居。

**特点**:
- 可预测的连接数量
- 计算速度最快
- 每个节点恰好 K 条边

**代码位置**: `GraphConnectivityAlgorithm.computeKNearestNeighbors()`

### 4. 传统模式 (Legacy)
**原理**: 连接所有在范围内且不交叉的节点对。

**特点**:
- 保留原有行为
- 最大连通性
- 网络密集

**代码位置**: `RoadGraphState.rebuildEdgesLegacyMode()`

## 📁 新增文件

### 核心算法
- `GraphConnectivityAlgorithm.java` - 图连接算法实现
- `GraphConnectivityMode.java` - 算法模式枚举
- `GraphAlgorithmComparison.java` - 算法比较工具

### 测试
- `GraphConnectivityAlgorithmTest.java` - 单元测试

### 文档
- `GRAPH_ALGORITHM_GUIDE.md` - 算法使用指南
- `HONEYCOMB_ALGORITHM_IMPLEMENTATION.md` - 本文档

## 🔄 修改的文件

### RoadGraphState.java
**主要变更**:
1. 添加 `rebuildEdgesWithHoneycombAlgorithm()` 方法
2. 添加 `rebuildEdgesWithAlgorithm(GraphConnectivityMode mode)` 方法
3. 修改 `addNodeWithEdges()` 以使用新算法
4. 添加 `rebuildEdgesLegacyMode()` 保留原有行为

**关键代码**:
```java
public Node addNodeWithEdges(BlockPos pos, String type) {
    Node newNode = this.nodeStorage.add(pos, type);
    // 使用新的蜂窝状连接算法重新计算所有连接
    rebuildEdgesWithHoneycombAlgorithm();
    this.markDirty();
    return newNode;
}
```

## 🎨 算法效果对比

### 传统算法 (Legacy)
```
节点数: 9
边数: ~20+
平均度: 4-6
```

### Gabriel 图
```
节点数: 9
边数: ~12
平均度: 2.7
```

### RNG
```
节点数: 9
边数: ~8
平均度: 1.8
```

### KNN (K=3)
```
节点数: 9
边数: ~13
平均度: 3.0
```

## 🚀 使用方法

### 自动应用
新节点添加时会自动使用 Gabriel 图算法重建网络。

### 手动重建
```java
RoadGraphState graph = RoadGraphState.get(world);
graph.rebuildEdgesWithAlgorithm(GraphConnectivityMode.GABRIEL);
```

### 测试不同算法
```java
// 运行比较测试
GraphAlgorithmComparison.compareAlgorithms(nodes, maxDistance);
```

## 📊 性能分析

| 算法 | 时间复杂度 | 空间复杂度 | 适用节点数 |
|------|-----------|-----------|-----------|
| Gabriel | O(n³) | O(n²) | < 200 |
| RNG | O(n³) | O(n²) | < 200 |
| KNN | O(n² log n) | O(n²) | < 500 |
| Legacy | O(n²) | O(n²) | < 100 |

## ✅ 测试

运行单元测试:
```bash
./gradlew :modules:common:test --tests GraphConnectivityAlgorithmTest
```

## 🔮 未来改进

### 短期
- [ ] 添加配置选项选择默认算法
- [ ] 添加游戏内命令 `/roadarchitect rebuild <algorithm>`
- [ ] 在调试界面显示当前使用的算法

### 中期
- [ ] 实现 Delaunay 三角剖分
- [ ] 添加最小生成树 (MST) 算法
- [ ] 支持混合算法（例如：Gabriel + MST）

### 长期
- [ ] 基于地形的智能连接
- [ ] 考虑生物群系的连接权重
- [ ] 动态调整算法参数

## 🐛 已知问题

1. **性能**: 对于超过 200 个节点的网络，重建可能需要几秒钟
2. **连通性**: RNG 算法可能产生不连通的子图（待验证）
3. **边恢复**: 重建时会尝试恢复旧边的状态，但新算法可能不包含某些旧边

## 📝 技术细节

### Gabriel 图判定条件
```java
// 以 A-B 为直径的圆
BlockPos midpoint = (A + B) / 2
double radius = distance(A, B) / 2

// 如果圆内没有其他节点 C
for each node C (C != A, C != B):
    if distance(C, midpoint) < radius:
        return false  // 不是 Gabriel 边
return true  // 是 Gabriel 边
```

### RNG 判定条件
```java
// 检查是否存在"见证节点" C
for each node C (C != A, C != B):
    if distance(A, C) < distance(A, B) AND 
       distance(B, C) < distance(A, B):
        return false  // 不是 RNG 边
return true  // 是 RNG 边
```

## 🎓 参考资料

- [Gabriel Graph - Wikipedia](https://en.wikipedia.org/wiki/Gabriel_graph)
- [Relative Neighborhood Graph - Wikipedia](https://en.wikipedia.org/wiki/Relative_neighborhood_graph)
- [Delaunay Triangulation - Wikipedia](https://en.wikipedia.org/wiki/Delaunay_triangulation)
- Computational Geometry: Algorithms and Applications (de Berg et al.)

## 👥 贡献者

- 算法设计与实现: AI Assistant
- 问题提出: 项目维护者

## 📄 许可证

Apache-2.0 (与项目主许可证相同)
