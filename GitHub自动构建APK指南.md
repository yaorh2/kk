# 🚀 GitHub Actions 自动构建APK指南

只需3步，不用安装任何软件，直接下载编译好的APK！

---

## 📋 步骤总览

1. **创建GitHub仓库** → 上传代码
2. **触发自动构建** → 等待2-3分钟
3. **下载APK** → 安装使用

---

## 🔧 详细步骤

### 第一步：创建GitHub仓库

1. 访问 https://github.com 并登录
2. 点击右上角 **"+"** → **"New repository"**
3. 填写仓库信息：
   - Repository name: `Kline-Training-App`（随便起名）
   - 选择 **Public** 或 **Private** 都可以
   - 不要勾选任何初始化选项
4. 点击 **"Create repository"**

### 第二步：上传代码

**方法A：使用Git命令（推荐）**
```bash
# 进入项目目录
cd K线训练APP

# 初始化Git
git init
git add .
git commit -m "Initial commit"

# 关联到你的GitHub仓库（替换为你的仓库地址）
git remote add origin https://github.com/你的用户名/Kline-Training-App.git
git branch -M main
git push -u origin main
```

**方法B：网页手动上传**
1. 在GitHub仓库页面点击 **"uploading an existing file"**
2. 将 `K线训练APP` 文件夹里的所有文件拖进去
3. 点击 **"Commit changes"**

### 第三步：触发构建并下载APK

1. 上传代码后，GitHub会**自动开始构建**（Actions标签页可查看）
2. 等待 **2-3分钟**（首次构建可能稍久）
3. 构建完成后：
   - 点击仓库首页的 **"Actions"** 标签
   - 点击最新的工作流运行记录
   - 页面底部 **"Artifacts"** 区域
   - 点击 `K线训练APP_v1.0.0` 下载APK！

---

## 📱 下载后的APK安装

1. 将下载的ZIP文件解压，得到 `app-debug.apk`
2. 发送到手机
3. 手机上点击安装（需要允许"未知来源应用"）

---

## 🔍 如何查看构建状态

### 构建成功 ✅
- Actions页面显示绿色 ✓ 标记
- Artifacts区域有可下载的APK文件

### 构建中 ⏳
- 显示黄色 ● 标记
- 通常需要2-3分钟

### 构建失败 ❌
- 显示红色 ✗ 标记
- 点击进入查看详细日志，通常是网络问题或依赖下载失败
- 点击 **"Re-run jobs"** 重新尝试即可

---

## 💡 高级功能

### 手动触发构建
1. 进入 **Actions** 标签
2. 点击左侧 **"Build Android APK"**
3. 点击右侧 **"Run workflow"** → **"Run workflow"**

### 自定义版本号
修改 `android_app/app/build.gradle` 中的：
```gradle
versionCode 1        // 内部版本号（整数）
versionName "1.0.0"  // 显示版本名（字符串）
```

---

## 📊 构建耗时参考

| 步骤 | 耗时 |
|-----|------|
| 检出代码 | ~10秒 |
| 设置JDK | ~20秒 |
| 下载依赖 | ~30秒 |
| 编译APK | ~60秒 |
| **总计** | **~2分钟** |

---

## ⚠️ 常见问题

### Q1: 为什么没有看到Actions运行？
**A:** 检查：
1. `.github/workflows/build.yml` 文件是否正确上传
2. 是否上传到 `main` 或 `master` 分支
3. 仓库设置中Actions是否启用（默认启用）

### Q2: 构建失败怎么办？
**A:**
1. 点击失败的工作流查看详细错误日志
2. 最常见原因是网络超时，点击 **Re-run jobs** 重试
3. 如仍失败，检查 `build.gradle` 配置是否正确

### Q3: 下载的ZIP里有什么？
**A:** ZIP包内直接就是可安装的 `app-debug.apk` 文件

### Q4: 可以构建Release版本吗？
**A:** 配置文件会尝试构建Release版本，但因为没有签名密钥，Release APK是未签名的，Debug版本可正常安装使用。

---

## 🎯 优势对比

| 方式 | 需要Android Studio | 需要本地SDK | 耗时 | 难度 |
|-----|------------------|------------|------|------|
| GitHub自动构建 | ❌ 不需要 | ❌ 不需要 | 2-3分钟 | ⭐ 简单 |
| 本地编译 | ✅ 需要 | ✅ 需要 | 5-10分钟 | ⭐⭐⭐ 中等 |

---

## 📞 需要帮助？

如果构建遇到问题：
1. 截图Actions的错误日志
2. 检查网络连接（GitHub Actions服务器在国外）
3. 多尝试几次重新运行工作流

**提示：** GitHub Actions每月有免费额度，个人用户完全够用！
