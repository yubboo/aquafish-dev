@echo off
chcp 65001 >nul
setlocal

set "PROJECT_ROOT=%~dp0.."

echo [1/2] Aquafish Admin test and build
pushd "%PROJECT_ROOT%\admin"
call pnpm test
if errorlevel 1 goto failed
call pnpm build
if errorlevel 1 goto failed
popd

echo [2/2] Aquafish Server test
pushd "%PROJECT_ROOT%\app"
call gradlew.bat test --no-daemon
if errorlevel 1 goto failed
popd

echo.
echo Aquafish verification passed.
exit /b 0

:failed
set "VERIFY_EXIT_CODE=%ERRORLEVEL%"
popd
echo.
echo Aquafish verification failed.
exit /b %VERIFY_EXIT_CODE%
