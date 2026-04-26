# java-zero-copy

用于对比 Java 在网络传输过程中零拷贝、非零拷贝以及 `MappedByteBuffer` 三种发送方式差异的实验项目。

## 项目目标

本项目主要关注下面三类差异：

- 发送端实现差异
  - 非零拷贝: `FileInputStream/BufferedInputStream -> byte[] -> Socket`
  - 零拷贝: `FileChannel.transferTo -> SocketChannel`
  - 内存映射: `MappedByteBuffer -> SocketChannel`
- 服务端处理差异
  - `memory`: 服务端只接收网络字节，不落盘
  - `disk`: 服务端接收网络字节后写入文件，模拟“网络接收 + 磁盘写入”
- 运行方式差异
  - `local`: 单进程，客户端与服务端在同一 JVM 内
  - `remote`: 多进程，客户端与独立服务端分离

## 输出结果

每次 benchmark 会输出：

- 单轮客户端耗时
- 单轮服务端接收耗时
- 吞吐量（MB/s）
- CPU 使用率采样（overall / avg / peak）
- 汇总统计（avg / min / max）
- 相对非零拷贝的吞吐、耗时和服务端耗时变化

同时会导出结果文件：

- `*-results.csv`: 每一轮原始结果
- `*-summary.csv`: 每种模式聚合结果
- `*-report.json`: 完整报告

## 运行参数

客户端默认参数：

- `--execution-mode=local`
- `--file=data/zero-copy-benchmark.bin`
- `--file-size=256MB`
- `--buffer-size=64KB`
- `--mapped-chunk-size=32MB`
- `--warmup=1`
- `--iterations=3`
- `--cpu-sample-interval-ms=50`
- `--host=127.0.0.1`
- `--port=0`
- `--mode=all`
- `--server-mode=all`
- `--server-output-dir=data/received`
- `--delete-generated-file=false`
- `--delete-received-files=true`
- `--result-dir=data/results`
- `--result-prefix=benchmark`
- `--write-csv=true`
- `--write-json=true`

独立服务端默认参数：

- `--host=0.0.0.0`
- `--port=9090`
- `--buffer-size=64KB`
- `--server-output-dir=data/server-received`
- `--delete-received-files=true`

## 测试方法

### 方法一：单进程本地测试

适合快速验证逻辑、观察趋势，启动最简单。

```bash
mvn compile exec:java
```

也可以显式写出参数：

```bash
mvn compile exec:java "-Dexec.args=--execution-mode=local --file-size=256MB --warmup=1 --iterations=3 --mode=all --server-mode=all --result-prefix=local-run"
```

说明：

- `local` 模式下客户端和服务端都在一个 JVM 内
- 输出里的 CPU 指标是单 JVM 聚合值
- 更适合做功能验证和相对趋势观察

### 方法二：多进程独立服务端测试

适合更接近真实客户端/服务端部署方式的测试。

先启动独立服务端：

```bash
mvn compile exec:java "-Dexec.mainClass=io.github.zerocopy.ZeroCopyBenchmarkServerApplication" "-Dexec.args=--host=127.0.0.1 --port=9090 --buffer-size=64KB --server-output-dir=data/server-remote --delete-received-files=true"
```

再启动客户端 benchmark：

```bash
mvn exec:java "-Dexec.args=--execution-mode=remote --host=127.0.0.1 --port=9090 --file-size=256MB --warmup=1 --iterations=3 --mode=all --server-mode=all --result-prefix=remote-run"
```

说明：

- `remote` 模式下客户端只负责发送和统计客户端 CPU
- 服务端接收耗时由服务端 ACK 返回
- 如果要观察服务端 CPU，建议配合 `top`、`perf`、任务管理器、`pidstat` 等系统工具

### 方法三：只测某一种发送模式

只跑零拷贝：

```bash
mvn exec:java "-Dexec.args=--mode=zero-copy"
```

只跑传统非零拷贝：

```bash
mvn exec:java "-Dexec.args=--mode=traditional"
```

只跑 `MappedByteBuffer`：

```bash
mvn exec:java "-Dexec.args=--mode=mapped-buffer"
```

### 方法四：只测某一种服务端场景

只测服务端内存接收：

```bash
mvn exec:java "-Dexec.args=--server-mode=memory"
```

只测服务端接收并落盘：

```bash
mvn exec:java "-Dexec.args=--server-mode=disk --delete-received-files=false"
```

## 推荐测试步骤

为了让结果更有参考价值，建议按下面顺序做：

1. 先用 `local` 模式跑 `8MB` 或 `32MB` 做功能自检，确认三种模式和两种服务端场景都能正常完成。
2. 再把文件提升到 `256MB`、`512MB` 或 `1GB`，把 `warmup` 至少设为 `1`，`iterations` 至少设为 `3`。
3. 如果想观察更贴近真实部署的结果，再切到 `remote` 模式，用独立服务端重跑一遍。
4. 如果重点看磁盘影响，使用 `--server-mode=disk`，并把 `--delete-received-files=false`，方便你确认服务端真实落盘文件。
5. 对比结果时，不只看吞吐量，也看：
   - 客户端耗时
   - 服务端接收耗时
   - CPU 指标
   - 在 `memory` 和 `disk` 两种场景下模式排序是否发生变化

## 结果文件说明

假设使用：

```bash
mvn exec:java "-Dexec.args=--result-prefix=remote-run"
```

那么默认会生成：

- `data/results/remote-run-results.csv`
- `data/results/remote-run-summary.csv`
- `data/results/remote-run-report.json`

其中：

- `results.csv` 适合导入 Excel、Numbers、Pandas 做二次分析
- `summary.csv` 适合快速画模式对比表
- `report.json` 适合做自动化归档和脚本分析

## 结果解读

- 非零拷贝会多经历用户态和内核态之间的数据搬运，通常 CPU 开销更高。
- 零拷贝通过 `transferTo` 把文件内容尽量直接交给内核发送，减少一次或多次用户态拷贝。
- `MappedByteBuffer` 不是严格意义上的网络零拷贝，但它减少了传统读写中的部分复制和系统调用，常适合作为中间态方案。
- `disk` 场景会把磁盘写入成本叠加到网络接收路径上，常见现象是总吞吐下降、服务端耗时增加、不同发送模式排名发生变化。
- `local` 模式下 CPU 是客户端和服务端共享一个 JVM 的聚合值。
- `remote` 模式下导出的 CPU 指标是客户端进程 CPU，服务端 CPU 需要用系统工具补充观察。
- 在本地回环网络下，零拷贝不一定总是吞吐最佳；不同操作系统、JDK 版本、网卡、文件系统和文件大小，都会改变结果。

## 建议

- 想看“纯发送路径”的差异，优先用 `server-mode=memory`
- 想看“网络 + 落盘链路”的差异，优先用 `server-mode=disk`
- 想做更稳定的横向对比，优先用更大的文件、更长的预热和更多轮次
- 想做自动化分析，直接消费导出的 `csv/json`
