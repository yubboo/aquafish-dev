'use strict';

/**
 * Aquafish GitHub Release 自动上传 1Panel 包配置脚本。
 *
 * 修改内容：
 * 1. 为 publish 作业暴露当前发行版本。
 * 2. 新增 release-assets 作业。
 * 3. 镜像成功发布后自动生成并上传 1Panel ZIP。
 * 4. 重写 package-1panel.ps1，使其兼容 Windows/Linux。
 * 5. 每个 ZIP 只包含当前发行版本，避免混入无效旧版本。
 */

const fs = require('node:fs');
const path = require('node:path');

const repositoryRoot = path.resolve(__dirname, '..', '..');

const workflowPath = path.join(
  repositoryRoot,
  '.github',
  'workflows',
  'publish-image.yml',
);

const packageScriptPath = path.join(
  repositoryRoot,
  'scripts',
  'package-1panel.ps1',
);

const timestamp = new Date()
  .toISOString()
  .replace(/[:.]/g, '-');

const backupRoot = path.join(
  repositoryRoot,
  'backups',
  'release-1panel-automation',
  timestamp,
);

function assertFile(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`缺少文件：${filePath}`);
  }

  if (!fs.statSync(filePath).isFile()) {
    throw new Error(`路径不是文件：${filePath}`);
  }
}

function replaceOnce(content, search, replacement, label) {
  const index = content.indexOf(search);

  if (index < 0) {
    throw new Error(`没有找到修改位置：${label}`);
  }

  const secondIndex = content.indexOf(
    search,
    index + search.length,
  );

  if (secondIndex >= 0) {
    throw new Error(`修改位置不唯一：${label}`);
  }

  return (
    content.slice(0, index) +
    replacement +
    content.slice(index + search.length)
  );
}

function backupFile(filePath) {
  const relativePath = path.relative(
    repositoryRoot,
    filePath,
  );

  const destination = path.join(
    backupRoot,
    relativePath,
  );

  fs.mkdirSync(
    path.dirname(destination),
    { recursive: true },
  );

  fs.copyFileSync(filePath, destination);
}

assertFile(workflowPath);
assertFile(packageScriptPath);

fs.mkdirSync(backupRoot, { recursive: true });

backupFile(workflowPath);
backupFile(packageScriptPath);

// ============================================================
// 修改 GitHub Actions 工作流
// ============================================================

let workflow = fs
  .readFileSync(workflowPath, 'utf8')
  .replace(/^\uFEFF/, '')
  .replace(/\r\n/g, '\n');

if (workflow.includes('release-assets:')) {
  throw new Error(
    'publish-image.yml 已经包含 release-assets，停止重复修改。',
  );
}

const publishJobAnchor = [
  '    runs-on: ubuntu-latest',
  '',
  '    steps:',
].join('\n');

const publishJobReplacement = [
  '    runs-on: ubuntu-latest',
  '',
  '    # 把解析出的版本传递给 Release 附件任务。',
  '    outputs:',
  '      version: ${{ steps.aquafish-version.outputs.version }}',
  '',
  '    steps:',
].join('\n');

workflow = replaceOnce(
  workflow,
  publishJobAnchor,
  publishJobReplacement,
  'publish 作业输出版本',
);

const releaseAssetsJob = [
  '',
  '  # ========================================================',
  '  # GitHub Release 1Panel 附件',
  '  #',
  '  # 只在正式 Release 时运行。',
  '  # 必须等待镜像成功发布，避免出现有安装包却没有镜像的情况。',
  '  # ========================================================',
  '  release-assets:',
  '    name: Publish 1Panel release package',
  "    if: github.event_name == 'release'",
  '    needs: publish',
  '    runs-on: ubuntu-latest',
  '',
  '    # 上传 GitHub Release 附件需要 contents: write。',
  '    permissions:',
  '      contents: write',
  '',
  '    env:',
  '      AQUAFISH_VERSION: ${{ needs.publish.outputs.version }}',
  '      RELEASE_TAG: ${{ github.event.release.tag_name }}',
  '',
  '    steps:',
  '      - name: Check out release source',
  '        uses: actions/checkout@v6',
  '',
  '      - name: Build 1Panel release package',
  '        shell: pwsh',
  '        run: |',
  '          ./scripts/package-1panel.ps1 -Version "$env:AQUAFISH_VERSION"',
  '',
  '      - name: Generate package checksum',
  '        shell: bash',
  '        run: |',
  '          set -euo pipefail',
  '',
  '          PACKAGE_FILE="packaging/1panel/aquafish-${AQUAFISH_VERSION}-1panel.zip"',
  '',
  '          if [ ! -f "$PACKAGE_FILE" ]; then',
  '            echo "Missing 1Panel package: $PACKAGE_FILE"',
  '            exit 1',
  '          fi',
  '',
  '          sha256sum "$PACKAGE_FILE" > "${PACKAGE_FILE}.sha256"',
  '',
  '          echo "Generated release files:"',
  '          ls -lh "$PACKAGE_FILE" "${PACKAGE_FILE}.sha256"',
  '',
  '      - name: Upload package to GitHub Release',
  '        shell: bash',
  '        env:',
  '          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}',
  '        run: |',
  '          set -euo pipefail',
  '',
  '          PACKAGE_FILE="packaging/1panel/aquafish-${AQUAFISH_VERSION}-1panel.zip"',
  '',
  '          gh release upload "$RELEASE_TAG" \\',
  '            "$PACKAGE_FILE" \\',
  '            "${PACKAGE_FILE}.sha256" \\',
  '            --clobber \\',
  '            --repo "$GITHUB_REPOSITORY"',
].join('\n');

workflow = workflow.trimEnd() +
  '\n' +
  releaseAssetsJob +
  '\n';

fs.writeFileSync(
  workflowPath,
  workflow,
  'utf8',
);

// ============================================================
// 重写跨平台 1Panel 打包脚本
// ============================================================

const packageScriptContent = String.raw`param(
    # 不填写时自动选择最高语义化版本目录。
    # 正式发布时由 GitHub Actions 明确传入版本。
    [string]$Version = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# scripts 的上一级就是项目根目录。
$repositoryRoot = [System.IO.Path]::GetFullPath(
    [System.IO.Path]::Combine(
        $PSScriptRoot,
        ".."
    )
)

# 使用 System.IO.Path.Combine，兼容 Windows 和 Linux。
$appRoot = [System.IO.Path]::Combine(
    $repositoryRoot,
    "packaging",
    "1panel",
    "aquafish"
)

if (-not (Test-Path -LiteralPath $appRoot -PathType Container)) {
    throw "1Panel application root does not exist: $appRoot"
}

# ============================================================
# 确定打包版本
# ============================================================

if ([string]::IsNullOrWhiteSpace($Version)) {
    $versionDirectories = @(
        Get-ChildItem -LiteralPath $appRoot -Directory |
            Where-Object {
                $_.Name -match '^\d+\.\d+\.\d+$'
            } |
            Sort-Object {
                [System.Version]::Parse($_.Name)
            }
    )

    if ($versionDirectories.Count -eq 0) {
        throw "No semantic version directory found in: $appRoot"
    }

    $Version = $versionDirectories[-1].Name
}

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Invalid semantic version: $Version"
}

$versionRoot = [System.IO.Path]::Combine(
    $appRoot,
    $Version
)

$composeFile = [System.IO.Path]::Combine(
    $versionRoot,
    "docker-compose.yml"
)

$versionDataFile = [System.IO.Path]::Combine(
    $versionRoot,
    "data.yml"
)

if (-not (Test-Path -LiteralPath $versionRoot -PathType Container)) {
    throw "1Panel version directory does not exist: $versionRoot"
}

# ============================================================
# 校验必需文件
# ============================================================

$requiredRootFileNames = @(
    "data.yml",
    "logo.png",
    "README.md",
    "README_en.md"
)

$requiredFiles = @(
    $composeFile,
    $versionDataFile
)

foreach ($fileName in $requiredRootFileNames) {
    $requiredFiles += [System.IO.Path]::Combine(
        $appRoot,
        $fileName
    )
}

foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Missing required 1Panel package file: $requiredFile"
    }
}

# ============================================================
# 校验 Docker Hub 正式镜像
# ============================================================

$expectedImage = "aqhub/aquafish:$Version"

$composeText = Get-Content     -LiteralPath $composeFile     -Raw     -Encoding UTF8

if (-not $composeText.Contains("image: $expectedImage")) {
    throw @"
Compose image version is incorrect.

Expected:
image: $expectedImage

File:
$composeFile
"@
}

# ============================================================
# 准备输出目录和临时目录
# ============================================================

$outputDirectory = [System.IO.Path]::Combine(
    $repositoryRoot,
    "packaging",
    "1panel"
)

$outputFile = [System.IO.Path]::Combine(
    $outputDirectory,
    "aquafish-$Version-1panel.zip"
)

$stageRoot = [System.IO.Path]::Combine(
    $outputDirectory,
    ".aquafish-1panel-stage"
)

$stageAppRoot = [System.IO.Path]::Combine(
    $stageRoot,
    "aquafish"
)

$stageVersionRoot = [System.IO.Path]::Combine(
    $stageAppRoot,
    $Version
)

New-Item     -ItemType Directory     -Path $outputDirectory     -Force |
    Out-Null

if (Test-Path -LiteralPath $outputFile) {
    Remove-Item -LiteralPath $outputFile -Force
}

if (Test-Path -LiteralPath $stageRoot) {
    Remove-Item         -LiteralPath $stageRoot         -Recurse         -Force
}

New-Item     -ItemType Directory     -Path $stageVersionRoot     -Force |
    Out-Null

try {
    # 复制根级应用描述文件。
    foreach ($fileName in $requiredRootFileNames) {
        $sourceFile = [System.IO.Path]::Combine(
            $appRoot,
            $fileName
        )

        Copy-Item             -LiteralPath $sourceFile             -Destination $stageAppRoot             -Force
    }

    # 只复制当前发行版本。
    #
    # 仓库里可以保留历史版本，
    # 但当前 ZIP 不再把所有历史版本一起带进去。
    Get-ChildItem         -LiteralPath $versionRoot         -Force |
        ForEach-Object {
            Copy-Item                 -LiteralPath $_.FullName                 -Destination $stageVersionRoot                 -Recurse                 -Force
        }

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $archive = [System.IO.Compression.ZipFile]::Open(
        $outputFile,
        [System.IO.Compression.ZipArchiveMode]::Create
    )

    try {
        $normalizedStageRoot = $stageRoot.TrimEnd(
            [char[]]"\/"
        )

        $sourceFiles = @(
            Get-ChildItem                 -LiteralPath $stageRoot                 -File                 -Recurse |
                Sort-Object FullName
        )

        foreach ($sourceFile in $sourceFiles) {
            $relativePath = $sourceFile.FullName.Substring(
                $normalizedStageRoot.Length
            )

            $relativePath = $relativePath.TrimStart(
                [char[]]"\/"
            )

            # ZIP 内部统一使用正斜杠。
            $entryName = $relativePath -replace '\\', '/'

            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive,
                $sourceFile.FullName,
                $entryName,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    }
    finally {
        $archive.Dispose()
    }
}
catch {
    if (Test-Path -LiteralPath $outputFile) {
        Remove-Item -LiteralPath $outputFile -Force
    }

    throw
}
finally {
    if (Test-Path -LiteralPath $stageRoot) {
        Remove-Item             -LiteralPath $stageRoot             -Recurse             -Force
    }
}

# ============================================================
# 验证 ZIP 内容
# ============================================================

$requiredEntries = @(
    "aquafish/data.yml",
    "aquafish/logo.png",
    "aquafish/README.md",
    "aquafish/README_en.md",
    "aquafish/$Version/data.yml",
    "aquafish/$Version/docker-compose.yml"
)

$validationArchive = [System.IO.Compression.ZipFile]::OpenRead(
    $outputFile
)

try {
    $entryNames = @(
        $validationArchive.Entries |
            ForEach-Object {
                $_.FullName
            }
    )

    foreach ($requiredEntry in $requiredEntries) {
        if ($entryNames -notcontains $requiredEntry) {
            throw "Missing ZIP entry: $requiredEntry"
        }
    }

    foreach ($entryName in $entryNames) {
        if (
            $entryName -match '^aquafish/(?<PackageVersion>\d+\.\d+\.\d+)/' -and
            $Matches["PackageVersion"] -ne $Version
        ) {
            throw "Unexpected historical version in ZIP: $entryName"
        }
    }
}
catch {
    Remove-Item -LiteralPath $outputFile -Force
    throw
}
finally {
    $validationArchive.Dispose()
}

Write-Host ""
Write-Host "Aquafish 1Panel package created successfully."
Write-Host "Version:         $Version"
Write-Host "Container image: $expectedImage"
Write-Host "Package file:    $outputFile"
Write-Host ""
`;

fs.writeFileSync(
  packageScriptPath,
  '\uFEFF' + packageScriptContent,
  'utf8',
);

console.log('');
console.log('Aquafish Release 自动化配置完成。');
console.log(`工作流：${workflowPath}`);
console.log(`打包脚本：${packageScriptPath}`);
console.log(`备份目录：${backupRoot}`);
console.log('');