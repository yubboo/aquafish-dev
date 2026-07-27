'use strict';

/**
 * Aquafish 开发镜像触发范围优化脚本。
 *
 * 作用：
 * 1. 只在容器构建相关文件变化时构建开发镜像。
 * 2. 修改普通文档、开发菜单、普通辅助脚本时不再构建镜像。
 * 3. 完整保留 Release 正式镜像与 1Panel 附件自动上传逻辑。
 * 4. 修改前自动备份 publish-image.yml。
 */

const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..');

const workflowFile = path.join(
  projectRoot,
  '.github',
  'workflows',
  'publish-image.yml',
);

function stop(message) {
  console.error('');
  console.error('[错误] ' + message);
  console.error('');
  process.exit(1);
}

if (!fs.existsSync(workflowFile)) {
  stop('找不到工作流文件：' + workflowFile);
}

if (!fs.statSync(workflowFile).isFile()) {
  stop('工作流路径不是文件：' + workflowFile);
}

const originalRaw = fs.readFileSync(workflowFile, 'utf8');

const hasBom = originalRaw.charCodeAt(0) === 0xFEFF;
const lineEnding = originalRaw.includes('\r\n') ? '\r\n' : '\n';

const original = originalRaw
  .replace(/^\uFEFF/, '')
  .replace(/\r\n/g, '\n');

const oldBlock = [
  '  # main 分支代码变化时发布开发测试镜像。',
  '  push:',
  '    branches:',
  '      - main',
  '    paths:',
  '      - "**"',
  '      - "!**.md"',
].join('\n');

const newBlock = [
  '  # main 分支仅在容器构建相关文件变化时发布开发测试镜像。',
  '  # 修改普通文档或本地辅助脚本时，不重复构建多架构镜像。',
  '  push:',
  '    branches:',
  '      - main',
  '    paths:',
  '      # Java 后端源码与构建配置。',
  '      - "app/**"',
  '',
  '      # Vue 管理端源码与构建配置。',
  '      - "admin/**"',
  '',
  '      # 1Panel 正式应用配置会参与后端发行构建校验。',
  '      - "packaging/1panel/aquafish/**"',
  '',
  '      # 最终容器启动入口。',
  '      - "scripts/docker-entrypoint.sh"',
  '',
  '      # Docker 构建配置。',
  '      - "Dockerfile"',
  '      - ".dockerignore"',
  '',
  '      # 工作流本身发生变化时，重新验证镜像构建。',
  '      - ".github/workflows/publish-image.yml"',
].join('\n');

const expectedPaths = [
  'app/**',
  'admin/**',
  'packaging/1panel/aquafish/**',
  'scripts/docker-entrypoint.sh',
  'Dockerfile',
  '.dockerignore',
  '.github/workflows/publish-image.yml',
];

const alreadyOptimized =
  !original.includes('      - "**"') &&
  expectedPaths.every(function checkExpectedPath(expectedPath) {
    return original.includes('      - "' + expectedPath + '"');
  });

if (alreadyOptimized) {
  console.log('');
  console.log('[提示] 镜像工作流触发范围已经优化，无需重复修改。');
  console.log('[文件] ' + workflowFile);
  console.log('');
  process.exit(0);
}

const occurrences = original.split(oldBlock).length - 1;

if (occurrences === 0) {
  stop(
    '没有找到原来的 push.paths 配置。'
    + ' 为避免误改工作流，本次操作已停止。',
  );
}

if (occurrences > 1) {
  stop(
    '发现多个相同的 push.paths 配置。'
    + ' 为避免替换错误，本次操作已停止。',
  );
}

/*
 * 修改前检查自动发布功能仍然存在。
 * 避免在错误版本的工作流上操作。
 */
const requiredReleaseMarkers = [
  'release:',
  'workflow_dispatch:',
  'release-assets:',
  'gh release upload',
  'docker/build-push-action',
];

for (const marker of requiredReleaseMarkers) {
  if (!original.includes(marker)) {
    stop(
      '工作流缺少关键发布标记：'
      + marker
      + '。本次操作已停止。',
    );
  }
}

/*
 * 创建备份。
 * backups 已经被 Git 忽略，不会进入正式仓库。
 */
const timestamp = new Date()
  .toISOString()
  .replace(/[:.]/g, '-');

const backupDirectory = path.join(
  projectRoot,
  'backups',
  'workflow-trigger-scope',
  timestamp,
);

const backupFile = path.join(
  backupDirectory,
  'publish-image.yml',
);

fs.mkdirSync(backupDirectory, {
  recursive: true,
});

fs.copyFileSync(
  workflowFile,
  backupFile,
);

/*
 * 只替换 main 分支的 push.paths。
 * Release 和 workflow_dispatch 配置保持不变。
 */
const updated = original.replace(
  oldBlock,
  newBlock,
);

/*
 * 修改后验收。
 */
for (const expectedPath of expectedPaths) {
  const expectedLine = '      - "' + expectedPath + '"';

  if (!updated.includes(expectedLine)) {
    stop('修改后缺少触发路径：' + expectedPath);
  }
}

if (updated.includes('      - "**"')) {
  stop('修改后仍然存在全仓库触发规则。');
}

for (const marker of requiredReleaseMarkers) {
  if (!updated.includes(marker)) {
    stop('修改后丢失关键发布标记：' + marker);
  }
}

if (!updated.includes('contents: write')) {
  stop('修改后丢失 Release 附件写入权限。');
}

const output =
  (hasBom ? '\uFEFF' : '')
  + updated.replace(/\n/g, lineEnding);

fs.writeFileSync(
  workflowFile,
  output,
  'utf8',
);

console.log('');
console.log('==============================================================');
console.log(' Aquafish 开发镜像触发范围优化完成');
console.log('==============================================================');
console.log('');
console.log('[修改文件]');
console.log(workflowFile);
console.log('');
console.log('[备份文件]');
console.log(backupFile);
console.log('');
console.log('[以后会触发开发镜像]');
for (const expectedPath of expectedPaths) {
  console.log('  - ' + expectedPath);
}
console.log('');
console.log('[以后不会触发开发镜像]');
console.log('  - 普通 Markdown 文档');
console.log('  - scripts/aquafish-dev-menu.cjs');
console.log('  - 普通 Windows 辅助脚本');
console.log('  - 与容器构建无关的项目文件');
console.log('');
console.log('[保留功能]');
console.log('  - GitHub Release 正式镜像发布');
console.log('  - Docker Hub 正式镜像发布');
console.log('  - 1Panel ZIP 自动生成和上传');
console.log('  - 手动运行 workflow_dispatch');
console.log('');