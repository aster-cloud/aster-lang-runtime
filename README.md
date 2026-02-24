# aster-lang-runtime -- Aster 运行时支持库

提供 Aster 语言的运行时基础设施，包括 GraalVM Native Image 兼容性替换、标准库原语、Result 类型和工作流引擎。

## 核心模块

### 运行时原语 (`aster.runtime`)

- **Result / Ok / Err** -- 函数式错误处理类型，类似 Rust 的 `Result<T, E>`
- **StdList / StdMap** -- 标准集合封装
- **Primitives / Builtins** -- 内置函数与基本类型支持
- **Fn0 ~ Fn4** -- 函数式接口（0 到 4 参数）
- **Interop / Async** -- 互操作与异步支持
- **AsterCapability / AsterOrigin / AsterPii** -- 能力标记与数据分类注解

### 工作流引擎 (`aster.runtime.workflow` / `io.aster.workflow`)

- **WorkflowRuntime** -- 工作流运行时接口，定义执行、暂停、恢复等生命周期
- **InMemoryWorkflowRuntime** -- 内存实现，用于测试与开发
- **EventStore / InMemoryEventStore** -- 事件溯源存储
- **DeterministicClock / ReplayDeterministicClock** -- 确定性时钟，保障工作流回放一致性
- **ReplayDeterministicRandom / ReplayDeterministicUuid** -- 确定性随机数与 UUID 生成
- **IdempotencyKeyManager** -- 幂等键管理

### GraalVM Native Image 替换 (`io.quarkus.runtime.graal`)

解决 GraalVM Native Image 编译时 `Inet4Address` / `Inet6Address` 的反射限制：

- **Inet4AnyAccessor / Inet4LoopbackAccessor / Inet4BroadcastAccessor** -- IPv4 地址访问器
- **Inet6AnyAccessor / Inet6LoopbackAccessor** -- IPv6 地址访问器
- **InetAccessorUtils** -- 通用网络地址工具

## 依赖

- Quarkus 3.30.2（BOM 管理）
- Quarkus Cache / Core
- SmallRye Common Net（CidrAddress 支持）
- Jakarta CDI 4.0.1 / Jakarta Inject 2.0.1

## 构建

```bash
./gradlew build
```

## 环境要求

- Java 25+
- Gradle

## 项目关系

被 `aster-lang-truffle`（Truffle 解释器）作为运行时依赖引用。

## 许可证

Apache License 2.0
