'use strict'

/**
 * 关联页面：docs/license-center/index.html（开发者本地授权签发中心）。
 *
 * 功能：完成本地访问码登录、安全状态展示、签发参数提交、授权码复制/下载、审计刷新
 * 和主动锁定。脚本只访问同源 127.0.0.1 接口；私钥主密码从不进入这个页面。
 */

const state = {
  csrfToken: '',
  status: null,
  lastLicense: null,
  licenses: []
}

// 登录提交互斥锁：关联 #loginForm，防止回车与点击在同一帧产生两次会话请求。
let loginPending = false

const elements = {
  loginView: document.querySelector('#loginView'),
  workspaceView: document.querySelector('#workspaceView'),
  loginForm: document.querySelector('#loginForm'),
  loginError: document.querySelector('#loginError'),
  accessCode: document.querySelector('#accessCode'),
  lockButton: document.querySelector('#lockButton'),
  issueForm: document.querySelector('#issueForm'),
  issueButton: document.querySelector('#issueButton'),
  editionOptions: document.querySelector('#editionOptions'),
  featureOptions: document.querySelector('#featureOptions'),
  customDaysField: document.querySelector('#customDaysField'),
  days: document.querySelector('#days'),
  notBefore: document.querySelector('#notBefore'),
  globalMessage: document.querySelector('#globalMessage'),
  resultPanel: document.querySelector('#resultPanel'),
  resultCode: document.querySelector('#resultCode'),
  copyCodeButton: document.querySelector('#copyCodeButton'),
  downloadCodeButton: document.querySelector('#downloadCodeButton'),
  refreshButton: document.querySelector('#refreshButton'),
  auditList: document.querySelector('#auditList'),
  registryList: document.querySelector('#registryList'),
  registryRefreshButton: document.querySelector('#registryRefreshButton'),
  authorityCard: document.querySelector('#authorityCard'),
  authorityStatus: document.querySelector('#authorityStatus'),
  authorityDetail: document.querySelector('#authorityDetail')
}

const editionLabels = {
  professional: ['专业版', '适合个人与小型站点'],
  business: ['商业版', '适合企业正式运营'],
  enterprise: ['企业版', '适合完整模块与服务']
}

const featureLabels = {
  platform: '平台基础',
  cms: 'CMS',
  forum: '论坛',
  content: '内容中心',
  theme: '主题',
  plugin: '插件',
  market: '应用市场',
  ai: 'AI 能力',
  search: '搜索',
  updates: '更新服务'
}

/** 统一处理本地接口响应；错误正文只展示 message，不把调用栈带入页面。 */
async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(state.csrfToken ? { 'X-Aquafish-License-Center-CSRF': state.csrfToken } : {}),
      ...(options.headers || {})
    }
  })
  const body = await response.json().catch(() => null)
  if (!response.ok || !body?.success) {
    const error = new Error(body?.message || `本地接口请求失败：HTTP ${response.status}`)
    error.status = response.status
    throw error
  }
  return body.data
}

/** 在工作区顶部显示一次成功或错误消息。 */
function showMessage(message, tone = 'error') {
  elements.globalMessage.textContent = message
  elements.globalMessage.className = `message ${tone}`
  elements.globalMessage.hidden = false
}

/** 清除上一条全局反馈，避免旧错误干扰新一次签发。 */
function clearMessage() {
  elements.globalMessage.hidden = true
  elements.globalMessage.textContent = ''
}

/** 把 ISO 时间转换为本机可读时间；空到期时间表示永久授权。 */
function formatDate(value) {
  if (!value) return '永久有效'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

/** 根据后端白名单生成版本卡片，默认选择专业版。 */
function renderEditions(editions) {
  elements.editionOptions.replaceChildren(...editions.map((edition, index) => {
    const label = document.createElement('label')
    const input = document.createElement('input')
    const content = document.createElement('span')
    input.type = 'radio'
    input.name = 'edition'
    input.value = edition
    input.checked = index === 0
    const [title, description] = editionLabels[edition] || [edition, '自定义授权版本']
    content.textContent = `${title} · ${description}`
    label.append(input, content)
    return label
  }))
}

/** 根据后端功能白名单生成模块卡片；platform 固定选中且不可取消。 */
function renderFeatures(features) {
  elements.featureOptions.replaceChildren(...features.map(feature => {
    const label = document.createElement('label')
    const input = document.createElement('input')
    const content = document.createElement('span')
    input.type = 'checkbox'
    input.name = 'features'
    input.value = feature
    input.checked = feature === 'platform'
    input.disabled = feature === 'platform'
    content.textContent = featureLabels[feature] || feature
    label.append(input, content)
    return label
  }))
}

/** 展示明文私钥和加密仓库备份风险，不执行自动删除或复制。 */
function renderSecurity(security) {
  const plaintext = security.plaintextPrivateKeys || []
  const plaintextCard = document.querySelector('#plaintextCard')
  const plaintextStatus = document.querySelector('#plaintextStatus')
  const plaintextDetail = document.querySelector('#plaintextDetail')
  plaintextCard.classList.toggle('danger', plaintext.length > 0)
  plaintextCard.classList.toggle('success', plaintext.length === 0)
  plaintextStatus.textContent = plaintext.length > 0 ? '发现风险文件' : '未发现明文私钥'
  plaintextDetail.textContent = plaintext.length > 0
    ? `验证备份后请人工销毁：${plaintext.join(', ')}`
    : '仓库目录未发现 PEM/KEY 明文私钥'

  const backup = security.backup || {}
  const backupCard = document.querySelector('#backupCard')
  const backupStatus = document.querySelector('#backupStatus')
  const backupDetail = document.querySelector('#backupDetail')
  const backupSafe = backup.matchingCopies >= 2
  backupCard.classList.toggle('success', backupSafe)
  backupCard.classList.toggle('danger', !backupSafe)
  backupStatus.textContent = backupSafe ? '已检测到双备份' : `匹配备份 ${backup.matchingCopies || 0} 份`
  backupDetail.textContent = !backup.configured
    ? '未配置备份目录'
    : backup.matchingNames?.length
      ? backup.matchingNames.join(', ')
      : '备份目录中没有匹配当前指纹的 .aqvault'
}

/** 只展示 LICENSE_ISSUED 审计项；启动、登录和锁定事件保留在日志文件中。 */
function renderAudit(records) {
  const issued = (records || []).filter(item => item.type === 'LICENSE_ISSUED')
  if (issued.length === 0) {
    elements.auditList.innerHTML = '<p class="empty-state">暂无签发记录</p>'
    return
  }
  elements.auditList.replaceChildren(...issued.map(item => {
    const article = document.createElement('article')
    article.className = 'audit-item'
    const title = document.createElement('strong')
    const customer = document.createElement('p')
    const license = document.createElement('p')
    const time = document.createElement('p')
    title.textContent = item.customer || '未命名客户'
    customer.textContent = `${item.edition || '—'} · ${(item.features || []).join(', ')}`
    license.textContent = `授权编号：${item.licenseId || '—'}`
    time.textContent = `签发时间：${formatDate(item.recordedAt)}`
    article.append(title, customer, license, time)
    return article
  }))
}

/**
 * 渲染脱敏授权登记和设备操作按钮。
 * 吊销与解绑都只提交 licenseId/instanceId，完整 AQF1 授权码不会重新进入页面。
 */
function renderRegistry(records) {
  state.licenses = records || []
  if (state.licenses.length === 0) {
    elements.registryList.innerHTML = '<p class="empty-state">暂无授权登记</p>'
    return
  }
  elements.registryList.replaceChildren(...state.licenses.map(record => {
    const article = document.createElement('article')
    article.className = `registry-item ${record.status === 'REVOKED' ? 'is-revoked' : ''}`

    const heading = document.createElement('div')
    heading.className = 'registry-item-heading'
    const identity = document.createElement('div')
    const title = document.createElement('strong')
    const id = document.createElement('code')
    title.textContent = record.customer || '未命名客户'
    id.textContent = record.licenseId
    identity.append(title, id)
    const status = document.createElement('span')
    status.className = `registry-status is-${String(record.status).toLowerCase()}`
    status.textContent = record.status === 'REVOKED' ? '已吊销' : '有效'
    heading.append(identity, status)

    const facts = document.createElement('div')
    facts.className = 'registry-facts'
    const factValues = [
      ['版本', record.edition || '—'],
      ['设备码', record.instanceId || '—'],
      ['有效期', formatDate(record.expiresAt)],
      ['解绑次数', `${record.unbindCount || 0} / ${record.maxUnbinds || 0}`]
    ]
    for (const [label, value] of factValues) {
      const item = document.createElement('div')
      const name = document.createElement('span')
      const content = document.createElement('strong')
      name.textContent = label
      content.textContent = value
      item.append(name, content)
      facts.append(item)
    }

    const actions = document.createElement('div')
    actions.className = 'registry-actions'
    const binding = (record.bindings || []).find(item => item.instanceId === record.instanceId)
    const unbind = document.createElement('button')
    unbind.type = 'button'
    unbind.className = 'secondary-button'
    unbind.dataset.action = 'unbind'
    unbind.dataset.licenseId = record.licenseId
    unbind.dataset.instanceId = record.instanceId
    unbind.disabled = record.status === 'REVOKED' || !binding?.active
      || record.unbindCount >= record.maxUnbinds
    unbind.textContent = binding?.active ? '解绑当前设备' : '设备已解绑'

    const revoke = document.createElement('button')
    revoke.type = 'button'
    revoke.className = 'danger-button'
    revoke.dataset.action = 'revoke'
    revoke.dataset.licenseId = record.licenseId
    revoke.disabled = record.status === 'REVOKED'
    revoke.textContent = record.status === 'REVOKED' ? '授权已吊销' : '远程吊销授权'
    actions.append(unbind, revoke)

    article.append(heading, facts, actions)
    return article
  }))
}

/** 从本地登记簿刷新管理列表；登记簿只含脱敏元数据。 */
async function refreshRegistry(showFeedback = false) {
  try {
    const data = await api('/api/registry')
    renderRegistry(data.licenses)
    if (showFeedback) showMessage('授权登记已刷新。', 'success')
  } catch (error) {
    showMessage(error.message, 'error')
  }
}

/** 将后端状态应用到页面，并保存 CSRF Token 供后续签发和锁定请求使用。 */
function applyStatus(status) {
  state.status = status
  state.csrfToken = status.csrfToken
  document.querySelector('#keyId').textContent = status.key.keyId
  document.querySelector('#fingerprint').textContent = status.key.fingerprint
  document.querySelector('#idleStatus').textContent = `${status.runtime.idleMinutes} 分钟`
  elements.authorityCard.classList.toggle('success', status.authority.configured)
  elements.authorityStatus.textContent = status.authority.configured ? '已配置自动同步' : '未配置'
  elements.authorityDetail.textContent = status.authority.endpoint || '当前只保存本地登记'
  renderSecurity(status.security)
  renderEditions(status.options.editions)
  renderFeatures(status.options.features)
  renderAudit(status.recentAudit)
}

/**
 * 尝试恢复仍有效的 HttpOnly 会话。
 * 公开探测接口在未登录时返回 authenticated=false 和 HTTP 200，不制造预期内的 401。
 */
async function bootstrap() {
  try {
    const session = await api('/api/session')
    if (!session.authenticated) return
    applyStatus(session.status)
    await refreshRegistry(false)
    elements.loginView.hidden = true
    elements.workspaceView.hidden = false
    elements.lockButton.hidden = false
  } catch (error) {
    elements.loginError.textContent = error.message
    elements.loginError.hidden = false
  }
}

/**
 * 从粘贴内容提取终端生成的 ASCII 访问码。
 * 支持用户误复制整行“本次访问码：xxx”，并移除零宽字符；不会宽松匹配非访问码文本。
 */
function normalizeAccessCode(value) {
  const cleaned = String(value || '')
    .normalize('NFKC')
    .replace(/[\u200B-\u200D\u2060\uFEFF]/g, '')
    .trim()
  const candidates = cleaned.match(/[A-Za-z0-9_-]{32}/g)
  return candidates?.length === 1 ? candidates[0] : cleaned
}

/** 使用终端随机访问码换取本次进程的 HttpOnly 会话；重复提交按幂等恢复处理。 */
elements.loginForm.addEventListener('submit', async event => {
  event.preventDefault()
  if (loginPending) return
  loginPending = true
  elements.loginError.hidden = true
  const submit = elements.loginForm.querySelector('button[type="submit"]')
  submit.disabled = true
  try {
    const status = await api('/api/session', {
      method: 'POST',
      body: JSON.stringify({ accessCode: normalizeAccessCode(elements.accessCode.value) })
    })
    elements.accessCode.value = ''
    applyStatus(status)
    await refreshRegistry(false)
    elements.loginView.hidden = true
    elements.workspaceView.hidden = false
    elements.lockButton.hidden = false
  } catch (error) {
    elements.loginError.textContent = error.message
    elements.loginError.hidden = false
  } finally {
    loginPending = false
    submit.disabled = false
  }
})

/** 自定义期限卡片被选择时才显示天数输入框。 */
elements.issueForm.addEventListener('change', event => {
  if (event.target.name !== 'durationPreset') return
  elements.customDaysField.hidden = event.target.value !== 'custom'
  if (event.target.value !== 'custom') elements.days.value = event.target.value
})

/** 收集并提交签发参数；浏览器只传业务载荷，不传主密码或私钥路径。 */
elements.issueForm.addEventListener('submit', async event => {
  event.preventDefault()
  clearMessage()
  elements.issueButton.disabled = true
  elements.issueButton.textContent = '正在签名并保存…'
  try {
    const form = new FormData(elements.issueForm)
    const preset = String(form.get('durationPreset') || '365')
    const days = preset === 'custom' ? elements.days.value : preset
    const notBeforeValue = elements.notBefore.value
    const features = Array.from(elements.issueForm.querySelectorAll('input[name="features"]:checked'))
      .map(input => input.value)
    const data = await api('/api/licenses', {
      method: 'POST',
      body: JSON.stringify({
        customer: String(form.get('customer') || '').trim(),
        instanceId: String(form.get('instanceId') || '').trim(),
        edition: String(form.get('edition') || 'professional'),
        days,
        notBefore: notBeforeValue ? new Date(notBeforeValue).toISOString() : null,
        maxUnbinds: Number(form.get('maxUnbinds') || 3),
        features
      })
    })

    state.lastLicense = data
    document.querySelector('#resultLicenseId').textContent = data.payload.licenseId
    document.querySelector('#resultExpiresAt').textContent = formatDate(data.payload.expiresAt)
    document.querySelector('#resultFile').textContent = data.outputFile
    elements.resultCode.value = data.licenseCode
    elements.resultPanel.hidden = false
    const sync = data.onlineSync
    const syncFailed = sync?.configured && !sync.success
    showMessage(
      syncFailed
        ? `授权码已在本地安全签发，但${sync.message}`
        : '授权码已完成签名、登记和脱敏审计记录。',
      syncFailed ? 'warning' : 'success'
    )
    await refreshStatus(false)
    await refreshRegistry(false)
    elements.resultPanel.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    showMessage(error.message, 'error')
  } finally {
    elements.issueButton.disabled = false
    elements.issueButton.textContent = '验证并签发授权码'
  }
})

/** 重新读取安全检查与审计记录；不改变当前签发表单。 */
async function refreshStatus(showFeedback = true) {
  try {
    const status = await api('/api/status')
    state.csrfToken = status.csrfToken
    state.status = status
    renderSecurity(status.security)
    renderAudit(status.recentAudit)
    if (showFeedback) showMessage('安全状态和审计记录已刷新。', 'success')
  } catch (error) {
    showMessage(error.message, 'error')
  }
}

elements.refreshButton.addEventListener('click', () => refreshStatus(true))
elements.registryRefreshButton.addEventListener('click', () => refreshRegistry(true))

/** 处理登记簿中的解绑和不可逆吊销操作，并在成功后刷新脱敏列表。 */
elements.registryList.addEventListener('click', async event => {
  const button = event.target.closest('button[data-action]')
  if (!button || button.disabled) return
  const action = button.dataset.action
  const licenseId = button.dataset.licenseId
  const instanceId = button.dataset.instanceId
  const prompt = action === 'revoke'
    ? `确认永久吊销授权 ${licenseId} 吗？在线客户将在下次校验后停止使用。`
    : `确认解绑设备 ${instanceId} 吗？该操作会消耗一次解绑次数。`
  if (!window.confirm(prompt)) return

  button.disabled = true
  clearMessage()
  try {
    const body = action === 'revoke'
      ? { reason: '开发者在本地授权中心手动吊销' }
      : { instanceId }
    const result = await api(`/api/registry/${encodeURIComponent(licenseId)}/${action}`, {
      method: 'POST',
      body: JSON.stringify(body)
    })
    const syncFailed = result.onlineSync?.configured && !result.onlineSync.success
    showMessage(
      syncFailed
        ? `本地操作成功，但${result.onlineSync.message}`
        : action === 'revoke' ? '授权已吊销并完成在线同步。' : '设备解绑成功并完成在线同步。',
      syncFailed ? 'warning' : 'success'
    )
    await refreshRegistry(false)
    await refreshStatus(false)
  } catch (error) {
    showMessage(error.message, 'error')
    button.disabled = false
  }
})

/** 复制可发给客户的授权码；不会复制私钥、主密码或本地访问码。 */
elements.copyCodeButton.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(elements.resultCode.value)
    showMessage('授权码已复制，可以发送给对应设备的客户。', 'success')
  } catch {
    elements.resultCode.focus()
    elements.resultCode.select()
    showMessage('浏览器无法自动复制，已选中授权码，请按 Ctrl+C。', 'error')
  }
})

/** 在浏览器生成额外副本；服务端已经在安全输出目录保存正式文件。 */
elements.downloadCodeButton.addEventListener('click', () => {
  if (!state.lastLicense) return
  const blob = new Blob([state.lastLicense.licenseCode + '\n'], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = state.lastLicense.outputFile
  anchor.click()
  URL.revokeObjectURL(url)
})

/** 主动销毁本地会话并通知服务端关闭监听；关闭后必须重新输入仓库主密码启动。 */
elements.lockButton.addEventListener('click', async () => {
  if (!window.confirm('确认锁定并退出签发中心吗？未复制的授权码请先保存。')) return
  try {
    await api('/api/lock', { method: 'POST', body: '{}' })
  } catch {
    // 即使响应丢失，页面也立即转入锁定状态，避免继续显示敏感授权码。
  }
  state.csrfToken = ''
  state.lastLicense = null
  elements.resultCode.value = ''
  document.body.innerHTML = '<main class="login-view"><section class="login-card"><div class="shield-icon">✓</div><h2>签发中心已锁定</h2><p class="muted">本地监听和私钥会话已经关闭，可以安全关闭此页面。</p></section></main>'
})

bootstrap()
