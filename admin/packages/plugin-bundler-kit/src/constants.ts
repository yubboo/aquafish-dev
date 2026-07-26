/** 插件 UI 默认入口和产物名称。 */
export const AQ_PLUGIN_DEFAULT_ENTRY = 'src/index.ts'
export const AQ_PLUGIN_DEFAULT_MANIFEST = '../src/main/resources/plugin.yaml'
/**
 * 生产产物直接进入 Gradle 标准资源输出目录，随后执行 jar/bootJar 即可得到 JAR 根目录 ui/。
 * 开发模式保留在 UI 工程自身 build/dist，便于单独检查产物且不污染 Java 构建目录。
 */
export const AQ_PLUGIN_DEFAULT_PRODUCTION_OUT_DIR = '../build/resources/main/ui'
export const AQ_PLUGIN_DEFAULT_DEVELOPMENT_OUT_DIR = 'build/dist'
export const AQ_PLUGIN_ENTRY_FILE = 'main.js'
export const AQ_PLUGIN_STYLE_FILE = 'style.css'
export const AQ_PLUGIN_UI_MANIFEST_FILE = 'ui-manifest.json'

/**
 * 由 Aquafish 宿主提供的前端运行时。
 *
 * 插件不得重复打包这些依赖，否则会产生多个 Vue Runtime、不同 Axios 安全策略和重复组件样式。
 */
export const AQ_PLUGIN_GLOBALS = Object.freeze({
  vue: 'Vue',
  'vue-router': 'VueRouter',
  pinia: 'Pinia',
  axios: 'axios',
  '@aquafish/components': 'AquafishComponents',
  '@aquafish/api-client': 'AquafishApiClient',
  '@aquafish/ui-shared': 'AquafishUiShared',
})

export const AQ_PLUGIN_EXTERNALS = Object.freeze(
  Object.keys(AQ_PLUGIN_GLOBALS),
)
