@echo off
chcp 65001 >nul
echo ========================================
echo K线训练APP - 一键编译脚本 (Windows)
echo ========================================
echo.

cd /d "%~dp0android_app"

echo [1/3] 检查Android SDK环境...
if not defined ANDROID_HOME (
    if not defined ANDROID_SDK_ROOT (
        echo 警告: 未检测到Android SDK环境变量
        echo 请先安装Android Studio并配置SDK
        echo.
        echo 按任意键打开项目目录，或Ctrl+C退出
        pause >nul
        explorer .
        exit /b 1
    )
)
echo ✓ Android SDK已检测
echo.

echo [2/3] 下载Gradle Wrapper...
if not exist "gradlew.bat" (
    echo 正在创建gradlew...
    echo 请使用Android Studio打开项目，自动生成Gradle Wrapper
    echo.
    pause
    exit /b 1
)
echo ✓ Gradle Wrapper已存在
echo.

echo [3/3] 开始编译Debug APK...
echo 这可能需要几分钟时间，请耐心等待...
echo ========================================
call gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✓ 编译成功！
    echo ========================================
    echo.
    echo APK文件位置:
    echo %cd%\app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 是否打开APK所在目录？(Y/N)
    set /p open_dir=
    if /i "%open_dir%"=="Y" explorer "app\build\outputs\apk\debug"
) else (
    echo.
    echo ========================================
    echo ✗ 编译失败！
    echo ========================================
    echo.
    echo 请检查:
    echo 1. Android Studio已正确安装
    echo 2. 首次编译请使用Android Studio打开项目
    echo 3. 网络连接正常（需要下载依赖）
    echo.
    echo 错误代码: %errorlevel%
)

echo.
pause
