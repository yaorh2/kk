#!/bin/bash

echo "========================================"
echo "K线训练APP - 一键编译脚本 (macOS/Linux)"
echo "========================================"
echo ""

cd "$(dirname "$0")/android_app"

echo "[1/3] 检查Android SDK环境..."
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "警告: 未检测到Android SDK环境变量"
    echo "请先安装Android Studio并配置SDK"
    echo ""
    read -p "按Enter键打开项目目录，或Ctrl+C退出"
    open . 2>/dev/null || xdg-open . 2>/dev/null
    exit 1
fi
echo "✓ Android SDK已检测"
echo ""

echo "[2/3] 检查Gradle Wrapper..."
if [ ! -f "gradlew" ]; then
    echo "请使用Android Studio打开项目，自动生成Gradle Wrapper"
    echo ""
    read -p "按Enter键继续"
    exit 1
fi
chmod +x gradlew
echo "✓ Gradle Wrapper已就绪"
echo ""

echo "[3/3] 开始编译Debug APK..."
echo "这可能需要几分钟时间，请耐心等待..."
echo "========================================"
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "✓ 编译成功！"
    echo "========================================"
    echo ""
    echo "APK文件位置:"
    echo "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    read -p "是否打开APK所在目录？(y/N) " open_dir
    if [ "$open_dir" = "y" ] || [ "$open_dir" = "Y" ]; then
        open "app/build/outputs/apk/debug" 2>/dev/null || xdg-open "app/build/outputs/apk/debug" 2>/dev/null
    fi
else
    echo ""
    echo "========================================"
    echo "✗ 编译失败！"
    echo "========================================"
    echo ""
    echo "请检查:"
    echo "1. Android Studio已正确安装"
    echo "2. 首次编译请使用Android Studio打开项目"
    echo "3. 网络连接正常（需要下载依赖）"
fi

echo ""
read -p "按Enter键退出"
