# Heart Rate Manager

一个基于Android的心率监测管理应用，支持蓝牙心率设备连接和实时数据可视化。

## 功能特性

### 核心功能
- **蓝牙设备扫描与连接**：支持扫描和连接蓝牙低功耗(BLE)心率监测设备
- **实时心率监测**：实时显示心率数据，支持心率图表可视化
- **数据可视化**：
  - 实时心率曲线图表
  - 点击图表查看详细心率和时间信息
  - 横轴以秒为单位显示，起始点为0
- **WebSocket服务器**：
  - 内置WebSocket服务器，支持多客户端连接
  - 实时推送心率数据到连接的客户端
  - 显示客户端连接状态和数量

### 界面特性
- 现代化深色主题界面
- 实时蓝牙连接状态显示
- 设备列表对话框，支持滚动选择
- 连接失败自动提示重新扫描
- 3秒连接超时检测

## 技术栈

- **开发语言**：Kotlin
- **最低SDK版本**：Android 6.0 (API 23)
- **目标SDK版本**：Android 14 (API 34)
- **主要依赖库**：
  - MPAndroidChart - 图表可视化
  - Java-WebSocket - WebSocket服务器
  - Android Jetpack组件

## 安装

### 前置要求
- Android设备（Android 6.0及以上）
- 支持蓝牙低功耗(BLE)的心率监测设备
- 蓝牙和位置权限

### 安装步骤

1. 从[Releases](https://github.com/H1d3rOne/HRManager/releases)页面下载最新版本的APK文件
2. 在Android设备上启用"允许安装未知来源应用"
3. 安装APK文件
4. 授予应用所需的权限（蓝牙、位置）

## 使用说明

### 首次使用

1. **授予权限**：首次启动应用时，授予蓝牙和位置权限
2. **扫描设备**：点击"扫描设备"按钮搜索附近的心率监测设备
3. **选择设备**：在设备列表对话框中选择要连接的设备
4. **连接设备**：点击"连接"按钮连接到选中的设备
5. **查看数据**：连接成功后，实时心率数据将显示在界面上

### WebSocket功能

1. **启动服务器**：点击"启动WebSocket"按钮启动WebSocket服务器
2. **查看地址**：服务器地址将显示在界面上（格式：ws://IP:端口）
3. **客户端连接**：使用WebSocket客户端连接到显示的地址
4. **数据推送**：心率数据将实时推送到所有连接的客户端

### 图表交互

- **查看详情**：点击心率图表上的数据点查看具体的心率和时间信息
- **缩放平移**：支持手势缩放和平移图表
- **时间显示**：横轴显示从连接开始的秒数

## 权限说明

应用需要以下权限：
- `BLUETOOTH` - 蓝牙基础功能
- `BLUETOOTH_ADMIN` - 蓝牙管理
- `BLUETOOTH_SCAN` - 扫描蓝牙设备（Android 12+）
- `BLUETOOTH_CONNECT` - 连接蓝牙设备（Android 12+）
- `ACCESS_FINE_LOCATION` - 精确位置（用于蓝牙扫描）
- `ACCESS_COARSE_LOCATION` - 粗略位置（用于蓝牙扫描）
- `INTERNET` - 网络访问（WebSocket功能）
- `ACCESS_NETWORK_STATE` - 网络状态

## 项目结构

```
HRManager/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/heartrate/manager/
│   │   │   │   ├── MainActivity.kt          # 主活动
│   │   │   │   ├── BleManager.kt            # 蓝牙管理
│   │   │   │   └── WebSocketManager.kt      # WebSocket管理
│   │   │   ├── res/
│   │   │   │   ├── layout/                  # 布局文件
│   │   │   │   ├── mipmap-*/                # 应用图标
│   │   │   │   └── values/                  # 资源文件
│   │   │   └── AndroidManifest.xml          # 清单文件
│   │   └── build.gradle                     # 应用级构建配置
│   └── build.gradle                         # 项目级构建配置
├── gradle/                                  # Gradle包装器
├── logo.png                                 # 应用图标源文件
└── README.md                                # 项目文档
```

## 开发环境

- **IDE**：Android Studio
- **Gradle版本**：9.0-milestone-1
- **Kotlin版本**：1.9.0
- **JDK版本**：17

## 构建项目

```bash
# 克隆项目
git clone https://github.com/H1d3rOne/HRManager.git

# 进入项目目录
cd HRManager

# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本
./gradlew assembleRelease
```

## 故障排除

### 无法扫描到设备
- 确保蓝牙已开启
- 确保位置权限已授予
- 确保心率设备已开启并处于可发现状态
- 尝试重启蓝牙

### 连接失败
- 确保设备在范围内
- 确保设备未被其他应用连接
- 尝试重新扫描设备
- 检查设备电量是否充足

### WebSocket连接失败
- 确保设备和客户端在同一网络
- 检查防火墙设置
- 确认WebSocket服务器已启动

## 版本历史

### v1.0.0 (2026-03-01)
- 初始版本发布
- 支持蓝牙心率设备连接
- 实时心率监测和图表显示
- WebSocket服务器功能

## 贡献

欢迎提交Issue和Pull Request！

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- GitHub: [@H1d3rOne](https://github.com/H1d3rOne)
- 项目地址: [https://github.com/H1d3rOne/HRManager](https://github.com/H1d3rOne/HRManager)
