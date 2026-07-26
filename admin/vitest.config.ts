import { defineConfig } from 'vitest/config'

/**
 * 后台前端单元测试配置。
 * 当前首先覆盖首次安装路由决策，测试运行在 Node 环境，不启动真实浏览器或后端。
 */
export default defineConfig({
  test: {
    environment: 'node',
    include: [
      'src/**/*.test.ts',
      'packages/**/*.test.ts',
    ],
  },
})
