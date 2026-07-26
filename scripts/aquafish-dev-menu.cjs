const { spawn, spawnSync } = require('node:child_process')
const crypto = require('node:crypto')
const fs = require('node:fs')
const net = require('node:net')
const os = require('node:os')
const path = require('node:path')
const readline = require('node:readline')

const projectRoot = path.resolve(__dirname, '..')
const appDir = path.join(projectRoot, 'app')
const adminDir = path.join(projectRoot, 'admin')
const onePanelPackageScript = path.join(
  projectRoot,
  'scripts',
  'package-1panel.ps1'
)
const onePanelPackageRoot = path.join(
  projectRoot,
  'packaging',
  '1panel',
  'aquafish'
)

const backendPort = 8520
const adminPort = 18520
const launcherStateDir = path.join(
  os.tmpdir(),
  'aquafish-dev-menu',
  crypto
    .createHash('sha256')
    .update(projectRoot.toLowerCase())
    .digest('hex')
    .slice(0, 16)
)
const launcherStateFile = path.join(launcherStateDir, 'processes.json')

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
})

let inputClosed = false
rl.once('close', () => {
  inputClosed = true
})

function ask(question) {
  if (inputClosed) {
    return Promise.resolve(null)
  }

  return new Promise(resolve => {
    let settled = false

    const handleClose = () => {
      if (settled) {
        return
      }
      settled = true
      resolve(null)
    }

    rl.once('close', handleClose)

    try {
      rl.question(question, answer => {
        if (settled) {
          return
        }
        settled = true
        rl.off('close', handleClose)
        resolve(answer.trim())
      })
    } catch (error) {
      rl.off('close', handleClose)
      if (error && error.code === 'ERR_USE_AFTER_CLOSE') {
        settled = true
        resolve(null)
        return
      }
      throw error
    }
  })
}

function sleep(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

function printSeparator() {
  console.log('='.repeat(62))
}

function prefixOutput(prefix, stream) {
  let buffer = ''

  stream.on('data', chunk => {
    buffer += chunk.toString()
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() || ''

    for (const line of lines) {
      console.log(line ? `${prefix} ${line}` : '')
    }
  })

  stream.on('end', () => {
    if (buffer) {
      console.log(`${prefix} ${buffer}`)
    }
  })
}

function showMenu() {
  console.clear()
  printSeparator()
  console.log(' Aquafish 开发启动菜单')
  printSeparator()
  console.log('')
  console.log(` 后端端口：${backendPort}`)
  console.log(` 管理端口：${adminPort}`)
  console.log('')
  console.log(' 1. 重启后端 + 管理端')
  console.log(' 2. 只重启后端 Spring Boot')
  console.log(' 3. 只重启管理端 aqadmin')
  console.log(' 4. 停止后端 + 管理端')
  console.log(' 5. 查看开发服务状态')
  console.log(' 6. 打开常用地址')
  console.log(' 7. 一键生成 1Panel 应用包')
  console.log(' 0. 退出')
  console.log('')
  printSeparator()
}

async function pause() {
  await ask('\n按回车返回菜单...')
}

function isPortOpen(port, host = '127.0.0.1') {
  return new Promise(resolve => {
    const socket = net.createConnection({ host, port })

    socket.setTimeout(800)
    socket.once('connect', () => {
      socket.destroy()
      resolve(true)
    })
    socket.once('timeout', () => {
      socket.destroy()
      resolve(false)
    })
    socket.once('error', () => {
      resolve(false)
    })
  })
}

function readLauncherState() {
  try {
    const value = JSON.parse(fs.readFileSync(launcherStateFile, 'utf8'))
    return value && typeof value === 'object' ? value : {}
  } catch {
    return {}
  }
}

function writeLauncherState(state) {
  fs.mkdirSync(launcherStateDir, { recursive: true })
  const temporaryFile = `${launcherStateFile}.${process.pid}.tmp`
  fs.writeFileSync(temporaryFile, `${JSON.stringify(state, null, 2)}\n`, 'utf8')
  fs.renameSync(temporaryFile, launcherStateFile)
}

function rememberServiceProcess(name, pid, marker) {
  const state = readLauncherState()
  state[name] = {
    pid,
    marker,
    startedAt: new Date().toISOString()
  }
  writeLauncherState(state)
}

function forgetServiceProcess(name, expectedPid) {
  const state = readLauncherState()
  if (!state[name]) {
    return
  }
  if (expectedPid && Number(state[name].pid) !== Number(expectedPid)) {
    return
  }
  delete state[name]
  writeLauncherState(state)
}

function getProcessCommandLine(pid) {
  const command =
    `$process = Get-CimInstance Win32_Process -Filter "ProcessId = ${pid}"; `
    + 'if ($process) { $process.CommandLine }'
  const result = spawnSync(
    'powershell.exe',
    ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command],
    {
      encoding: 'utf8',
      windowsHide: true
    }
  )
  return `${result.stdout || ''}`.trim()
}

function isTrackedServiceProcessRunning(name) {
  const tracked = readLauncherState()[name]
  if (!tracked || !tracked.pid || !tracked.marker) {
    return false
  }
  return getProcessCommandLine(tracked.pid).includes(tracked.marker)
}

/**
 * 结束启动器上次记录的进程树，但先校验命令行标记。
 *
 * Windows 可能复用已经退出的 PID，因此不能只凭临时状态文件直接 taskkill；
 * 标记不匹配时只清理过期记录，不触碰当前 PID 对应的其他程序。
 */
function stopTrackedServiceProcess(name) {
  const state = readLauncherState()
  const tracked = state[name]

  if (!tracked || !Number.isInteger(Number(tracked.pid))) {
    return true
  }

  const pid = String(tracked.pid)
  const commandLine = getProcessCommandLine(pid)

  if (!commandLine) {
    forgetServiceProcess(name, pid)
    return true
  }

  if (!tracked.marker || !commandLine.includes(tracked.marker)) {
    console.log(`[${name}] PID ${pid} 已被复用或标记不匹配，忽略该过期记录。`)
    forgetServiceProcess(name, pid)
    return true
  }

  console.log(`[${name}] 正在终止启动器记录的进程树 PID：${pid}`)
  const result = spawnSync('taskkill.exe', ['/PID', pid, '/T', '/F'], {
    encoding: 'utf8',
    windowsHide: true
  })
  forgetServiceProcess(name, pid)

  if (result.status === 0) {
    console.log(`[${name}] 已终止启动器记录的进程树。`)
    return true
  }

  const message = `${result.stderr || result.stdout || ''}`.trim()
  if (message) {
    console.log(message)
  }
  return getProcessCommandLine(pid) === ''
}

/**
 * 只读取指定监听端口对应的 PID。
 *
 * 优先使用 Windows 的 Get-NetTCPConnection；旧系统缺少该命令时再退回 netstat。
 * 端口是脚本内固定数字，不接受外部字符串输入，避免命令注入和误匹配其他端口。
 */
function getListeningPids(port) {
  const pids = new Set()
  const powershellCommand =
    "$ErrorActionPreference = 'SilentlyContinue'; "
    + `Get-NetTCPConnection -State Listen -LocalPort ${port} `
    + '| ForEach-Object { $_.OwningProcess } '
    + '| Sort-Object -Unique'

  const powershellResult = spawnSync(
    'powershell.exe',
    ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', powershellCommand],
    {
      encoding: 'utf8',
      windowsHide: true
    }
  )

  for (const line of `${powershellResult.stdout || ''}`.split(/\r?\n/)) {
    const pid = line.trim()
    if (/^\d+$/.test(pid) && pid !== '0') {
      pids.add(pid)
    }
  }

  if (pids.size > 0) {
    return [...pids]
  }

  const netstatResult = spawnSync('netstat.exe', ['-ano', '-p', 'tcp'], {
    encoding: 'utf8',
    windowsHide: true
  })

  for (const sourceLine of `${netstatResult.stdout || ''}`.split(/\r?\n/)) {
    const columns = sourceLine.trim().split(/\s+/)
    if (columns.length < 5 || columns[0].toUpperCase() !== 'TCP') {
      continue
    }

    const localAddress = columns[1]
    const state = columns[3].toUpperCase()
    const pid = columns[4]

    if (
      state === 'LISTENING'
      && localAddress.endsWith(`:${port}`)
      && /^\d+$/.test(pid)
      && pid !== '0'
    ) {
      pids.add(pid)
    }
  }

  return [...pids]
}

async function waitUntilPortFree(port, name, timeoutMilliseconds = 12_000) {
  const startedAt = Date.now()

  while (Date.now() - startedAt < timeoutMilliseconds) {
    if (getListeningPids(port).length === 0) {
      console.log(`[${name}] 端口 ${port} 已释放。`)
      return true
    }
    await sleep(400)
  }

  console.log(`[${name}] 端口 ${port} 在等待时间内没有释放。`)
  return false
}

/**
 * 按用户选择只终止明确开发端口的监听进程。
 *
 * /T 会同时结束该监听进程创建的子进程，避免 Vite 或 Java 留下孤儿进程；
 * 不按 java.exe、node.exe 等宽泛进程名批量结束，减少影响其他项目的风险。
 */
async function stopPort(port, name) {
  const pids = getListeningPids(port)

  if (pids.length === 0) {
    console.log(`[${name}] 端口 ${port} 当前未被占用。`)
    return true
  }

  console.log(`[${name}] 准备终止端口 ${port} 的监听 PID：${pids.join(', ')}`)

  for (const pid of pids) {
    const result = spawnSync(
      'taskkill.exe',
      ['/PID', pid, '/T', '/F'],
      {
        encoding: 'utf8',
        windowsHide: true
      }
    )

    if (result.status === 0) {
      console.log(`[${name}] 已终止 PID ${pid}。`)
    } else {
      console.log(`[${name}] 无法终止 PID ${pid}。`)
      const message = `${result.stderr || result.stdout || ''}`.trim()
      if (message) {
        console.log(message)
      }
    }
  }

  return waitUntilPortFree(port, name)
}

async function stopService(name, port) {
  const trackedProcessStopped = stopTrackedServiceProcess(name)
  const portReleased = await stopPort(port, name)
  return trackedProcessStopped && portReleased
}

async function waitForServicePort(port, name, timeoutMilliseconds) {
  const startedAt = Date.now()

  while (Date.now() - startedAt < timeoutMilliseconds) {
    if (await isPortOpen(port)) {
      console.log(`[${name}] 已在端口 ${port} 就绪。`)
      return true
    }
    if (!isTrackedServiceProcessRunning(name)) {
      console.log(`[${name}] 启动窗口已提前退出，端口 ${port} 未就绪。`)
      return false
    }
    await sleep(600)
  }

  console.log(`[${name}] 等待端口 ${port} 启动超时，请查看新打开的终端窗口。`)
  return false
}

function runSync(name, cwd, command) {
  console.log(`[${name}] ${command}`)
  const result = spawnSync('cmd.exe', ['/d', '/c', command], {
    cwd,
    env: process.env,
    stdio: 'inherit',
    windowsHide: false
  })
  return result.status === 0
}

function ensureAdminDependencies() {
  const modulesMetadata = path.join(adminDir, 'node_modules', '.modules.yaml')

  if (fs.existsSync(modulesMetadata)) {
    return true
  }

  console.log('[admin] 未发现 node_modules，先根据锁文件安装依赖。')
  return runSync('admin', adminDir, 'pnpm install --frozen-lockfile')
}

/**
 * 每个服务使用独立终端窗口运行。
 *
 * cmd 使用 /c：服务被菜单按端口停止后，Gradle/pnpm 会自然退出，旧终端窗口也会
 * 自动关闭；不会在每次重启后留下一个空闲的命令行窗口。
 */
function startServiceWindow(name, title, cwd, command, environmentOverrides = {}) {
  const child = spawn(
    'cmd.exe',
    ['/d', '/c', `title ${title} && ${command}`],
    {
      cwd,
      detached: true,
      env: {
        ...process.env,
        ...environmentOverrides
      },
      stdio: 'ignore',
      windowsHide: false
    }
  )

  child.once('error', error => {
    console.error(`[${name}] 无法创建启动窗口：${error.message}`)
    forgetServiceProcess(name, child.pid)
  })
  child.once('exit', code => {
    console.log(`[${name}] 启动窗口已退出，退出码：${code}`)
    forgetServiceProcess(name, child.pid)
  })
  rememberServiceProcess(name, child.pid, title)
  child.unref()
  console.log(`[${name}] 已创建独立启动窗口，PID：${child.pid}`)
}

/**
 * 菜单 1 使用一个终端承载两个子进程，日志在同一个窗口按来源添加前缀。
 *
 * 该模式由独立 Node 进程运行，因此菜单窗口仍可继续执行状态查看和停止命令；
 * 任意一端提前退出时会结束另一端，避免留下半套开发环境。
 */
async function runCombinedServices() {
  console.clear()
  printSeparator()
  console.log(' Aquafish 后端 + aqadmin')
  printSeparator()
  console.log('')
  console.log(`后端：http://127.0.0.1:${backendPort}`)
  console.log(`管理端：http://127.0.0.1:${adminPort}/admin`)
  console.log('停止方式：在菜单中选择 4，或关闭本窗口。')
  console.log('')
  printSeparator()
  console.log('')

  const backend = spawn(
    'cmd.exe',
    ['/d', '/c', 'gradlew.bat :boot:bootRun --no-daemon --args=--spring.profiles.active=dev'],
    {
      cwd: appDir,
      env: {
        ...process.env,
        SERVER_PORT: String(backendPort),
        AQUAFISH_SERVER_PORT: String(backendPort),
        AQUAFISH_EXTERNAL_URL: `http://127.0.0.1:${backendPort}`
      },
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true
    }
  )
  const admin = spawn(
    'cmd.exe',
    ['/d', '/c', 'pnpm dev:force'],
    {
      cwd: adminDir,
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true
    }
  )

  prefixOutput('[backend]', backend.stdout)
  prefixOutput('[backend]', backend.stderr)
  prefixOutput('[admin]', admin.stdout)
  prefixOutput('[admin]', admin.stderr)

  const children = [
    { name: 'backend', process: backend },
    { name: 'admin', process: admin }
  ]
  let shuttingDown = false

  function stopChildren(source) {
    if (shuttingDown) {
      return
    }
    shuttingDown = true
    console.log(`\n[services] ${source}，正在停止前后端...`)

    for (const item of children) {
      if (item.process.exitCode !== null || !item.process.pid) {
        continue
      }
      spawnSync('taskkill.exe', ['/PID', String(item.process.pid), '/T', '/F'], {
        encoding: 'utf8',
        windowsHide: true
      })
    }
  }

  process.once('SIGINT', () => {
    stopChildren('收到 Ctrl+C')
  })
  process.once('SIGTERM', () => {
    stopChildren('收到停止信号')
  })

  await new Promise(resolve => {
    let exitedChildren = 0

    for (const item of children) {
      item.process.once('error', error => {
        console.error(`[${item.name}] 进程启动失败：${error.message}`)
        stopChildren(`${item.name} 启动失败`)
      })
      item.process.once('exit', code => {
        console.log(`[${item.name}] 进程已退出，退出码：${code}`)
        exitedChildren++

        if (!shuttingDown) {
          stopChildren(`${item.name} 已退出`)
        }
        if (exitedChildren === children.length) {
          resolve()
        }
      })
    }
  })
}

function launchCombinedServices() {
  const title = 'Aquafish Backend + aqadmin'
  const child = spawn(
    'cmd.exe',
    [
      '/d',
      '/c',
      `title ${title} && node scripts\\aquafish-dev-menu.cjs --run-services`
    ],
    {
      cwd: projectRoot,
      detached: true,
      env: process.env,
      stdio: 'ignore',
      windowsHide: false
    }
  )

  child.once('error', error => {
    console.error(`[services] 无法创建合并启动窗口：${error.message}`)
    forgetServiceProcess('backend', child.pid)
    forgetServiceProcess('admin', child.pid)
  })
  child.once('exit', code => {
    console.log(`[services] 合并启动窗口已退出，退出码：${code}`)
    forgetServiceProcess('backend', child.pid)
    forgetServiceProcess('admin', child.pid)
  })
  rememberServiceProcess('backend', child.pid, title)
  rememberServiceProcess('admin', child.pid, title)
  child.unref()
  console.log(`[services] 已创建前后端共用窗口，PID：${child.pid}`)
}

function launchBackend() {
  /*
   * 开发者目录可能保留旧版 application.yaml。这里使用 Spring Boot 命令行参数，
   * 以最高配置优先级固定开发端口和外部地址，不修改用户已有的数据库与站点配置。
   */
  startServiceWindow(
    'backend',
    'Aquafish Backend - 8520',
    appDir,
    'gradlew.bat :boot:bootRun --no-daemon --args=--spring.profiles.active=dev',
    {
      SERVER_PORT: String(backendPort),
      AQUAFISH_SERVER_PORT: String(backendPort),
      AQUAFISH_EXTERNAL_URL: `http://127.0.0.1:${backendPort}`
    }
  )
}

function launchAdmin() {
  startServiceWindow(
    'admin',
    'Aquafish aqadmin - 18520',
    adminDir,
    'pnpm dev:force'
  )
}

async function restartAll() {
  console.log('正在重启 Aquafish 后端与管理端...')

  if (!ensureAdminDependencies()) {
    console.log('[admin] 依赖安装失败，本次重启已取消。')
    return false
  }

  const backendStopped = await stopService('backend', backendPort)
  const adminStopped = await stopService('admin', adminPort)
  if (!backendStopped || !adminStopped) {
    console.log('对应端口未能完全释放，本次重启已取消。')
    return false
  }

  launchCombinedServices()

  const [backendReady, adminReady] = await Promise.all([
    waitForServicePort(backendPort, 'backend', 180_000),
    waitForServicePort(adminPort, 'admin', 60_000)
  ])

  if (!backendReady || !adminReady) {
    console.log('两端未能全部就绪，正在清理本次启动进程。')
    await stopService('backend', backendPort)
    await stopService('admin', adminPort)
    return false
  }

  printAddresses()
  return true
}

async function restartBackend() {
  console.log('正在重启 Aquafish 后端...')

  if (!await stopService('backend', backendPort)) {
    console.log('后端端口未能释放，本次重启已取消。')
    return false
  }

  launchBackend()
  const ready = await waitForServicePort(backendPort, 'backend', 180_000)
  if (!ready) {
    await stopService('backend', backendPort)
    return false
  }
  printAddresses()
  return true
}

async function restartAdmin() {
  console.log('正在重启 Aquafish 管理端...')

  if (!ensureAdminDependencies()) {
    console.log('[admin] 依赖安装失败，本次重启已取消。')
    return false
  }

  if (!await stopService('admin', adminPort)) {
    console.log('管理端端口未能释放，本次重启已取消。')
    return false
  }

  launchAdmin()
  const ready = await waitForServicePort(adminPort, 'admin', 60_000)
  if (!ready) {
    await stopService('admin', adminPort)
    return false
  }
  printAddresses()
  return true
}

async function stopAll() {
  console.log('正在停止 Aquafish 开发服务...')
  const backendStopped = await stopService('backend', backendPort)
  const adminStopped = await stopService('admin', adminPort)

  if (backendStopped && adminStopped) {
    console.log('后端与管理端均已停止。')
    return true
  }

  console.log('仍有端口未能释放，请查看上面的 PID 信息。')
  return false
}

function printAddresses() {
  console.log('')
  console.log('常用地址：')
  console.log(`- 前台：http://127.0.0.1:${backendPort}/site`)
  console.log(`- 管理端：http://127.0.0.1:${adminPort}/admin`)
  console.log(`- 首次安装：http://127.0.0.1:${adminPort}/setup`)
  console.log(`- 健康检查：http://127.0.0.1:${backendPort}/api/health`)
}

async function showStatus() {
  const backendPids = getListeningPids(backendPort)
  const adminPids = getListeningPids(adminPort)

  console.log('Aquafish 开发服务状态：')
  console.log(
    `- 后端 ${backendPort}：${backendPids.length > 0 ? `运行中，PID ${backendPids.join(', ')}` : '未启动'}`
  )
  console.log(
    `- 管理端 ${adminPort}：${adminPids.length > 0 ? `运行中，PID ${adminPids.join(', ')}` : '未启动'}`
  )
  printAddresses()
}

function openUrl(url) {
  const child = spawn(
    'cmd.exe',
    ['/d', '/s', '/c', `start "" "${url}"`],
    {
      detached: true,
      stdio: 'ignore',
      windowsHide: true
    }
  )
  child.once('error', error => {
    console.error(`无法打开 ${url}：${error.message}`)
  })
  child.unref()
}

function openCommonAddresses() {
  openUrl(`http://127.0.0.1:${backendPort}/site`)
  openUrl(`http://127.0.0.1:${adminPort}/admin`)
  openUrl(`http://127.0.0.1:${adminPort}/setup`)
  console.log('已打开前台、管理端和首次安装页面。')
}

/**
 * 校验正式 1Panel 模板并生成可上传到面板的 ZIP。
 *
 * PowerShell 脚本负责自动选择最高语义化版本、校验镜像标签和必需文件，
 * 菜单只负责提供稳定入口，避免在 JavaScript 中复制第二套打包规则。
 */
function packageOnePanelApplication() {
  console.log('正在校验并生成 Aquafish 1Panel 应用包...')

  const result = spawnSync(
    'powershell.exe',
    [
      '-NoProfile',
      '-ExecutionPolicy',
      'Bypass',
      '-File',
      onePanelPackageScript
    ],
    {
      cwd: projectRoot,
      env: process.env,
      stdio: 'inherit',
      windowsHide: false
    }
  )

  if (result.error) {
    console.error(`1Panel 应用包生成失败：${result.error.message}`)
    return false
  }

  if (result.status !== 0) {
    console.error(`1Panel 应用包生成失败，退出码：${result.status}`)
    return false
  }

  console.log('1Panel 应用包已经生成，可上传到 1Panel 本地应用目录。')
  return true
}

/**
 * --check 只做静态自检，不启动或终止任何进程，供自动化验收和排错使用。
 */
function validateLauncher() {
  const requiredFiles = [
    path.join(projectRoot, 'p.bat'),
    path.join(appDir, 'gradlew.bat'),
    path.join(adminDir, 'package.json'),
    path.join(adminDir, 'vite.config.ts'),
    path.join(appDir, 'boot', 'src', 'main', 'resources', 'application.yml'),
    onePanelPackageScript,
    path.join(onePanelPackageRoot, 'data.yml'),
    path.join(onePanelPackageRoot, 'logo.png')
  ]
  const missingFiles = requiredFiles.filter(file => !fs.existsSync(file))

  if (missingFiles.length > 0) {
    console.error('启动器缺少必要文件：')
    for (const file of missingFiles) {
      console.error(`- ${file}`)
    }
    return false
  }

  const packageJson = fs.readFileSync(path.join(adminDir, 'package.json'), 'utf8')
  const viteConfig = fs.readFileSync(path.join(adminDir, 'vite.config.ts'), 'utf8')
  const applicationYaml = fs.readFileSync(
    path.join(appDir, 'boot', 'src', 'main', 'resources', 'application.yml'),
    'utf8'
  )

  const checks = [
    ['admin package 端口', packageJson.includes(`--port ${adminPort}`)],
    ['Vite 监听端口', viteConfig.includes(`port: ${adminPort}`)],
    ['Vite 后端代理', viteConfig.includes(`http://127.0.0.1:${backendPort}`)],
    ['Spring Boot 默认端口', applicationYaml.includes(`AQUAFISH_SERVER_PORT:${backendPort}`)]
  ]

  let passed = true
  for (const [name, ok] of checks) {
    console.log(`${ok ? '[通过]' : '[失败]'} ${name}`)
    passed = passed && ok
  }

  if (passed) {
    console.log(`启动器自检通过：backend=${backendPort}，admin=${adminPort}`)
  }
  return passed
}

async function main() {
  const argument = process.argv[2]

  if (argument === '--run-services') {
    await runCombinedServices()
    rl.close()
    return
  }

  if (argument === '--check') {
    const passed = validateLauncher()
    rl.close()
    process.exitCode = passed ? 0 : 1
    return
  }

  if (argument === '--status') {
    await showStatus()
    rl.close()
    return
  }

  if (argument === '--package-1panel') {
    const passed = packageOnePanelApplication()
    rl.close()
    process.exitCode = passed ? 0 : 1
    return
  }

  while (true) {
    showMenu()
    const choice = await ask('请输入数字：')

    if (choice === null) {
      return
    }

    console.log('')

    if (choice === '1') {
      await restartAll()
      await pause()
      continue
    }

    if (choice === '2') {
      await restartBackend()
      await pause()
      continue
    }

    if (choice === '3') {
      await restartAdmin()
      await pause()
      continue
    }

    if (choice === '4') {
      await stopAll()
      await pause()
      continue
    }

    if (choice === '5') {
      await showStatus()
      await pause()
      continue
    }

    if (choice === '6') {
      openCommonAddresses()
      await pause()
      continue
    }

    if (choice === '7') {
      packageOnePanelApplication()
      await pause()
      continue
    }

    if (choice === '0') {
      console.log('已退出 Aquafish 开发启动菜单。')
      rl.close()
      return
    }

    console.log('输入无效，请输入 1、2、3、4、5、6、7 或 0。')
    await pause()
  }
}

main().catch(error => {
  console.error('[Aquafish] 启动菜单发生错误：')
  console.error(error)
  rl.close()
  process.exitCode = 1
})
