<!-- 当前主题、安装/启用/升级/卸载、主题设置能力和模板诊断共用页面。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Delete, Download, Refresh, SwitchButton, Upload } from '@element-plus/icons-vue'
import { adminRequest } from '../../api/admin-workspace'
import '../workspace/admin-workspace.css'

interface ThemeItem {
  name: string
  title: string
  version: string
  engine: string
  authorName: string
  parent: string
  description: string
  active: boolean
  builtin: boolean
  canUninstall: boolean
  settingsAvailable: boolean
  templatesAvailable: boolean
  assetsAvailable: boolean
}

interface ThemeList {
  activeTheme: string
  activeThemeInstalled: boolean
  count: number
  items: ThemeItem[]
}

interface ThemeDiagnosis {
  theme: string
  title: string
  engine: string
  healthy: boolean
  settingsAvailable: boolean
  assetsAvailable: boolean
  expectedTemplateCount: number
  actualTemplateCount: number
  missingTemplates: string[]
  expectedTemplates: string[]
}

interface ThemeOperation {
  action: 'install' | 'activate' | 'upgrade' | 'uninstall'
  themeId: string
  version: string
  active?: boolean
  message: string
  warnings?: string[]
}

// ===== BEGIN：settings.yaml 动态表单类型 =====
type ThemeSettingValue = string | number | boolean

interface ThemeSettingOption {
  label: string
  value: ThemeSettingValue
}

interface ThemeSettingField {
  key: string
  label: string
  description: string
  type: 'text' | 'textarea' | 'select' | 'boolean' | 'number' | 'color' | 'image'
  defaultValue: ThemeSettingValue
  options: ThemeSettingOption[]
}

interface ThemeSettingsSnapshot {
  themeId: string
  title: string
  available: boolean
  customized: boolean
  fields: ThemeSettingField[]
  values: Record<string, ThemeSettingValue>
}
// ===== END：settings.yaml 动态表单类型 =====

const props = defineProps<{
  mode: 'current' | 'list' | 'settings' | 'diagnosis'
}>()
const themes = ref<ThemeList | null>(null)
const diagnosis = ref<ThemeDiagnosis | null>(null)
const themeSettings = ref<ThemeSettingsSnapshot | null>(null)
const settingsValues = ref<Record<string, ThemeSettingValue>>({})
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const busyAction = ref('')
const upgradeThemeId = ref('')
const installInput = ref<HTMLInputElement | null>(null)
const upgradeInput = ref<HTMLInputElement | null>(null)

const heading = computed(() => ({
  current: '当前主题',
  list: '已安装主题',
  settings: '主题设置能力',
  diagnosis: '模板完整性诊断',
}[props.mode]))

const description = computed(() => ({
  current: '查看运行配置选择的主题，以及当前服务器是否已经安装它。',
  list: '从实例 workdir/themes 扫描真实主题，不读取源码目录副本。',
  settings: '检查主题是否提供 settings.yaml；未声明的主题不会伪造设置项。',
  diagnosis: '检查当前主题对 Aquafish 16 个内置页面模板的覆盖情况。',
}[props.mode]))

const displayedThemes = computed(() => {
  const items = themes.value?.items || []
  return props.mode === 'current'
    ? items.filter(theme => theme.active)
    : items
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    themes.value = await adminRequest<ThemeList>('/api/admin/themes')
    diagnosis.value = props.mode === 'diagnosis' || props.mode === 'settings'
      ? await adminRequest<ThemeDiagnosis>('/api/admin/themes/diagnosis')
      : null
    if (props.mode === 'settings' && themes.value.activeTheme) {
      themeSettings.value = await adminRequest<ThemeSettingsSnapshot>(
        `/api/admin/themes/${encodeURIComponent(themes.value.activeTheme)}/settings`,
      )
      settingsValues.value = { ...themeSettings.value.values }
    } else {
      themeSettings.value = null
      settingsValues.value = {}
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '主题数据读取失败。'
  } finally {
    loading.value = false
  }
}

// ===== BEGIN：主题设置保存与恢复默认值 =====
async function saveThemeSettings() {
  if (!themeSettings.value?.available) return
  busyAction.value = 'settings:save'
  errorMessage.value = ''
  successMessage.value = ''
  try {
    themeSettings.value = await adminRequest<ThemeSettingsSnapshot>(
      `/api/admin/themes/${encodeURIComponent(themeSettings.value.themeId)}/settings`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ values: settingsValues.value }),
      },
    )
    settingsValues.value = { ...themeSettings.value.values }
    successMessage.value = '主题设置已保存，前台刷新后立即使用新配置。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '主题设置保存失败。'
  } finally {
    busyAction.value = ''
  }
}

async function resetThemeSettings() {
  if (!themeSettings.value?.available) return
  if (!window.confirm('确认恢复 settings.yaml 中声明的全部默认值吗？')) return
  busyAction.value = 'settings:reset'
  errorMessage.value = ''
  successMessage.value = ''
  try {
    themeSettings.value = await adminRequest<ThemeSettingsSnapshot>(
      `/api/admin/themes/${encodeURIComponent(themeSettings.value.themeId)}/settings`,
      { method: 'DELETE' },
    )
    settingsValues.value = { ...themeSettings.value.values }
    successMessage.value = '主题设置已恢复默认值。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '恢复默认设置失败。'
  } finally {
    busyAction.value = ''
  }
}
// ===== END：主题设置保存与恢复默认值 =====

function chooseInstallPackage() {
  installInput.value?.click()
}

function chooseUpgradePackage(themeId: string) {
  upgradeThemeId.value = themeId
  upgradeInput.value?.click()
}

async function submitPackage(
  url: string,
  file: File,
  actionKey: string,
) {
  const formData = new FormData()
  formData.append('file', file)
  await runOperation(actionKey, () => adminRequest<ThemeOperation>(url, {
    method: 'POST',
    body: formData,
  }))
}

async function installPackage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  await submitPackage('/api/admin/themes/install', file, 'install')
}

async function upgradePackage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const themeId = upgradeThemeId.value
  upgradeThemeId.value = ''
  if (!file || !themeId) return
  await submitPackage(
    `/api/admin/themes/${encodeURIComponent(themeId)}/upgrade`,
    file,
    `upgrade:${themeId}`,
  )
}

async function activateTheme(theme: ThemeItem) {
  await runOperation(`activate:${theme.name}`, () =>
    adminRequest<ThemeOperation>(
      `/api/admin/themes/${encodeURIComponent(theme.name)}/activate`,
      { method: 'POST' },
    ))
}

async function uninstallTheme(theme: ThemeItem) {
  if (!window.confirm(`确认卸载主题“${theme.title}”吗？运行目录会移入服务器安全备份。`)) {
    return
  }
  await runOperation(`uninstall:${theme.name}`, () =>
    adminRequest<ThemeOperation>(
      `/api/admin/themes/${encodeURIComponent(theme.name)}`,
      { method: 'DELETE' },
    ))
}

// ===== BEGIN：导出可重新安装的标准主题 ZIP =====
async function exportTheme(theme: ThemeItem) {
  busyAction.value = `export:${theme.name}`
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const response = await fetch(
      `/api/admin/themes/${encodeURIComponent(theme.name)}/export`,
    )
    if (!response.ok) {
      const body = await response.json().catch(() => null) as { message?: string } | null
      throw new Error(body?.message || `主题导出失败：HTTP ${response.status}`)
    }
    const archive = await response.blob()
    const url = URL.createObjectURL(archive)
    const link = document.createElement('a')
    link.href = url
    link.download = `${theme.name}.zip`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    successMessage.value = `主题“${theme.title}”已导出。`
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '主题导出失败。'
  } finally {
    busyAction.value = ''
  }
}
// ===== END：导出可重新安装的标准主题 ZIP =====

async function runOperation(
  actionKey: string,
  operation: () => Promise<ThemeOperation>,
) {
  busyAction.value = actionKey
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await operation()
    successMessage.value = result.warnings?.length
      ? `${result.message} 注意：${result.warnings.join('；')}`
      : result.message
    await load()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '主题操作失败。'
  } finally {
    busyAction.value = ''
  }
}

watch(() => props.mode, () => void load())
onMounted(() => void load())
</script>

<template>
  <section class="admin-workspace-page">
    <header class="admin-workspace-hero">
      <div>
        <span class="admin-workspace-eyebrow">Theme / {{ mode }}</span>
        <h2>{{ heading }}</h2>
        <p>{{ description }}</p>
      </div>
      <div class="admin-workspace-hero-actions">
        <button
          v-if="mode === 'list'"
          type="button"
          :disabled="Boolean(busyAction)"
          @click="chooseInstallPackage"
        >
          <Upload aria-hidden="true" />
          安装主题
        </button>
        <button type="button" :disabled="loading" @click="load">
          <Refresh aria-hidden="true" />
          {{ loading ? '扫描中…' : '重新扫描' }}
        </button>
      </div>
      <input
        ref="installInput"
        class="admin-workspace-file-input"
        type="file"
        accept=".zip,application/zip"
        @change="installPackage"
      >
      <input
        ref="upgradeInput"
        class="admin-workspace-file-input"
        type="file"
        accept=".zip,application/zip"
        @change="upgradePackage"
      >
    </header>

    <div v-if="errorMessage" class="admin-workspace-state is-error">{{ errorMessage }}</div>
    <div v-if="successMessage" class="admin-workspace-state is-success">{{ successMessage }}</div>

    <template v-if="themes">
      <section class="admin-workspace-card">
        <div class="admin-workspace-summary">
          <div><span>配置中的当前主题</span><strong>{{ themes.activeTheme }}</strong></div>
          <div><span>是否安装</span><strong>{{ themes.activeThemeInstalled ? '已安装' : '缺失' }}</strong></div>
          <div><span>已安装主题</span><strong>{{ themes.count }}</strong></div>
        </div>
      </section>

      <!-- ===== BEGIN：settings.yaml 驱动的主题设置表单 ===== -->
      <section
        v-if="mode === 'settings' && themeSettings && !themeSettings.available"
        class="admin-workspace-state"
      >
        <strong>当前主题没有可编辑设置清单</strong>
        <p>没有 settings.yaml 时不伪造字段，也不会直接修改主题文件。</p>
      </section>

      <form
        v-if="mode === 'settings' && themeSettings?.available"
        class="admin-workspace-form"
        @submit.prevent="saveThemeSettings"
      >
        <div class="admin-workspace-form-heading">
          <div>
            <h3>{{ themeSettings.title }} · 主题设置</h3>
            <p>
              当前值保存于实例 workdir；主题源码和主题升级包保持只读。
              {{ themeSettings.customized ? '当前使用自定义值。' : '当前使用清单默认值。' }}
            </p>
          </div>
        </div>

        <div class="admin-workspace-fields">
          <label
            v-for="field in themeSettings.fields"
            :key="field.key"
            class="admin-workspace-field"
            :class="{
              'is-wide': field.type === 'textarea' || field.type === 'image',
              'is-boolean': field.type === 'boolean',
            }"
          >
            <span>{{ field.label }}</span>

            <textarea
              v-if="field.type === 'textarea'"
              v-model="settingsValues[field.key] as string"
            />

            <select
              v-else-if="field.type === 'select'"
              v-model="settingsValues[field.key]"
            >
              <option
                v-for="option in field.options"
                :key="String(option.value)"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>

            <span v-else-if="field.type === 'boolean'" class="admin-workspace-switch">
              <input
                v-model="settingsValues[field.key] as boolean"
                type="checkbox"
              >
              <span>{{ settingsValues[field.key] ? '已启用' : '已关闭' }}</span>
            </span>

            <input
              v-else-if="field.type === 'number'"
              v-model.number="settingsValues[field.key] as number"
              type="number"
            >

            <input
              v-else-if="field.type === 'color'"
              v-model="settingsValues[field.key] as string"
              type="color"
            >

            <input
              v-else
              v-model="settingsValues[field.key] as string"
              type="text"
              :placeholder="field.type === 'image' ? '/theme-assets/… 或 /uploads/…' : ''"
            >

            <small v-if="field.description">{{ field.description }}</small>
          </label>
        </div>

        <div class="admin-workspace-form-actions">
          <button
            class="admin-workspace-action"
            type="submit"
            :disabled="Boolean(busyAction)"
          >
            {{ busyAction === 'settings:save' ? '保存中…' : '保存设置' }}
          </button>
          <button
            class="admin-workspace-action is-secondary"
            type="button"
            :disabled="Boolean(busyAction)"
            @click="resetThemeSettings"
          >
            {{ busyAction === 'settings:reset' ? '恢复中…' : '恢复默认值' }}
          </button>
        </div>
      </form>
      <!-- ===== END：settings.yaml 驱动的主题设置表单 ===== -->

      <section v-if="mode === 'diagnosis' && diagnosis" class="admin-workspace-card">
        <div class="admin-workspace-summary">
          <div><span>诊断状态</span><strong>{{ diagnosis.healthy ? '完整' : '需要补充' }}</strong></div>
          <div><span>内置模板 / 实际模板</span><strong>{{ diagnosis.expectedTemplateCount }} / {{ diagnosis.actualTemplateCount }}</strong></div>
          <div><span>静态资源</span><strong>{{ diagnosis.assetsAvailable ? '可用' : '缺失' }}</strong></div>
        </div>
        <div class="admin-workspace-empty">
          <strong>缺失模板</strong>
          <p>{{ diagnosis.missingTemplates.length ? diagnosis.missingTemplates.join('、') : '无' }}</p>
        </div>
      </section>

      <section v-if="mode === 'current' || mode === 'list'" class="admin-workspace-card">
        <div v-if="!displayedThemes.length" class="admin-workspace-empty">
          当前实例没有扫描到任何已安装主题。
        </div>
        <div v-else class="admin-workspace-table-wrap">
          <table class="admin-workspace-table">
            <thead>
              <tr>
                <th>主题</th><th>版本</th><th>引擎</th><th>作者</th>
                <th>模板 / 资源 / 设置</th><th>状态</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="theme in displayedThemes" :key="theme.name">
                <td>
                  <strong>{{ theme.title }}</strong>
                  <span v-if="theme.builtin" class="admin-workspace-badge">系统回退</span>
                  <br>{{ theme.name }}
                </td>
                <td>{{ theme.version }}</td>
                <td>{{ theme.engine }}</td>
                <td>{{ theme.authorName || '—' }}</td>
                <td>
                  {{ theme.templatesAvailable ? '模板✓' : '模板×' }} /
                  {{ theme.assetsAvailable ? '资源✓' : '资源×' }} /
                  {{ theme.settingsAvailable ? '设置✓' : '设置×' }}
                </td>
                <td>{{ theme.active ? '当前启用' : '已安装' }}</td>
                <td>
                  <div class="admin-workspace-row-actions">
                    <button
                      v-if="!theme.active"
                      class="admin-workspace-action"
                      type="button"
                      :disabled="Boolean(busyAction)"
                      @click="activateTheme(theme)"
                    >
                      <SwitchButton aria-hidden="true" />
                      {{ busyAction === `activate:${theme.name}` ? '启用中…' : '启用' }}
                    </button>
                    <span v-if="theme.active" class="admin-workspace-status-badge">当前启用</span>
                    <button
                      v-if="!theme.builtin"
                      class="admin-workspace-action is-secondary"
                      type="button"
                      :disabled="Boolean(busyAction)"
                      @click="chooseUpgradePackage(theme.name)"
                    >
                      <Upload aria-hidden="true" />
                      {{ busyAction === `upgrade:${theme.name}` ? '升级中…' : '升级' }}
                    </button>
                    <button
                      class="admin-workspace-action is-secondary"
                      type="button"
                      :disabled="Boolean(busyAction)"
                      @click="exportTheme(theme)"
                    >
                      <Download aria-hidden="true" />
                      {{ busyAction === `export:${theme.name}` ? '导出中…' : '导出' }}
                    </button>
                    <button
                      v-if="theme.canUninstall"
                      class="admin-workspace-action is-danger"
                      type="button"
                      :disabled="Boolean(busyAction)"
                      @click="uninstallTheme(theme)"
                    >
                      <Delete aria-hidden="true" />
                      {{ busyAction === `uninstall:${theme.name}` ? '卸载中…' : '卸载（保留备份）' }}
                    </button>
                    <span v-if="theme.active && !theme.builtin && !theme.canUninstall" class="admin-workspace-operation-note">
                      先启用其他主题后可卸载
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>
