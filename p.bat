@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

where node >nul 2>&1
if errorlevel 1 (
    echo [Aquafish] 未找到 Node.js，请先安装 Node.js 22 并加入 PATH。
    pause
    endlocal
    exit /b 1
)

node "%~dp0scripts\aquafish-dev-menu.cjs" %*
set "AQUAFISH_MENU_EXIT_CODE=%ERRORLEVEL%"

endlocal & exit /b %AQUAFISH_MENU_EXIT_CODE%
