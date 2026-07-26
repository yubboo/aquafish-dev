(() => {
  const shell = document.querySelector('.af-auth-shell')
  if (!(shell instanceof HTMLElement)) return

  const card = shell.querySelector('.af-auth-card')
  const subtitle = shell.querySelector('[data-auth-subtitle]')
  const alternate = shell.querySelector('[data-auth-alternate]')
  const tabs = Array.from(shell.querySelectorAll('[data-auth-mode]'))
  const forms = Array.from(shell.querySelectorAll('[data-auth-form]'))
  let mode = shell.dataset.initialMode === 'register' ? 'register' : 'login'
  let busy = false
  let userInteracted = false

  const safeRedirect = () => {
    const value = new URLSearchParams(window.location.search).get('redirect')
    return value && value.startsWith('/') && !value.startsWith('//') ? value : '/member'
  }

  const readJson = async (response) => {
    const text = await response.text()
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch {
      throw new Error(text || `请求失败：HTTP ${response.status}`)
    }
  }

  const fetchCsrf = async () => {
    const response = await fetch('/api/member/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' }
    })
    const body = await readJson(response)
    if (!response.ok || !body?.success) {
      throw new Error(body?.message || '无法获取安全令牌，请刷新页面重试。')
    }
    const headerName = String(body.data?.headerName || '').trim()
    const token = String(body.data?.token || '')
    if (!headerName || !token) {
      throw new Error('安全令牌响应不完整，请刷新页面重试。')
    }
    return { headerName, token }
  }

  const confirmSession = async () => {
    const response = await fetch('/api/member/auth/me', {
      credentials: 'include',
      headers: { Accept: 'application/json' }
    })
    const body = await readJson(response)
    if (!response.ok || !body?.success || !body.data) {
      throw new Error(body?.message || '登录成功，但浏览器未保存会话，请检查 Cookie 设置。')
    }
  }

  const setMessage = (formMode, message, success = false) => {
    const target = shell.querySelector(`[data-auth-message="${formMode}"]`)
    if (target instanceof HTMLElement) {
      target.textContent = message
      target.classList.toggle('is-success', success)
    }
  }

  const updateMode = (nextMode, updateHistory = true) => {
    if (busy) return
    mode = nextMode === 'register' ? 'register' : 'login'
    shell.dataset.mode = mode
    forms.forEach((form) => {
      const active = form.getAttribute('data-auth-form') === mode
      form.toggleAttribute('hidden', !active)
    })
    tabs.forEach((tab) => {
      const active = tab.getAttribute('data-auth-mode') === mode
      tab.setAttribute('aria-selected', String(active))
      tab.classList.toggle('is-active', active)
    })
    if (subtitle instanceof HTMLElement) {
      subtitle.textContent = mode === 'login' ? '统一账号登录' : '创建社区账号'
    }
    if (alternate instanceof HTMLButtonElement) {
      alternate.textContent = mode === 'login' ? '注册账号' : '返回登录'
    }
    document.title = `${mode === 'login' ? '用户登录' : '用户注册'} - Aquafish`

    if (updateHistory) {
      const query = window.location.search
      window.history.replaceState({}, '', `${mode === 'login' ? '/login' : '/register'}${query}`)
    }
  }

  const setBusy = (value) => {
    busy = value
    shell.classList.toggle('is-busy', value)
    shell.querySelectorAll('button, input').forEach((element) => {
      if (element instanceof HTMLButtonElement || element instanceof HTMLInputElement) {
        element.disabled = value
      }
    })
  }

  const submitLogin = async (values) => {
    const csrf = await fetchCsrf()
    const response = await fetch('/api/member/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token
      },
      body: JSON.stringify({
        loginName: String(values.get('loginName') || ''),
        password: String(values.get('password') || ''),
        rememberMe: values.get('rememberMe') === 'on'
      })
    })
    const body = await readJson(response)
    if (!response.ok || !body?.success) {
      throw new Error(body?.message || '登录失败，请检查账号和密码。')
    }
    await confirmSession()
  }

  const submitRegister = async (values) => {
    const csrf = await fetchCsrf()
    const response = await fetch('/api/member/auth/register', {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token
      },
      body: JSON.stringify({
        username: String(values.get('username') || ''),
        displayName: String(values.get('displayName') || ''),
        email: String(values.get('email') || ''),
        password: String(values.get('password') || ''),
        confirmPassword: String(values.get('confirmPassword') || ''),
        acceptedTerms: values.get('acceptedTerms') === 'on'
      })
    })
    const body = await readJson(response)
    if (!response.ok || !body?.success) {
      throw new Error(body?.message || '注册失败，请检查填写内容。')
    }
    await confirmSession()
  }

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => updateMode(tab.getAttribute('data-auth-mode')))
  })
  alternate?.addEventListener('click', () => updateMode(mode === 'login' ? 'register' : 'login'))
  shell.addEventListener('pointerdown', () => { userInteracted = true }, { once: true })
  shell.addEventListener('keydown', () => { userInteracted = true }, { once: true })

  forms.forEach((form) => {
    form.addEventListener('submit', async (event) => {
      event.preventDefault()
      if (!(form instanceof HTMLFormElement) || busy || !form.reportValidity()) return
      const formMode = form.getAttribute('data-auth-form') === 'register' ? 'register' : 'login'
      // 必须先保存表单值，再禁用输入框；FormData 会忽略 disabled 控件。
      const values = new FormData(form)
      setBusy(true)
      setMessage(formMode, formMode === 'login' ? '正在验证账号…' : '正在创建账号…')
      try {
        if (formMode === 'login') await submitLogin(values)
        else await submitRegister(values)
        setMessage(formMode, '认证成功，正在跳转…', true)
        window.location.assign(safeRedirect())
      } catch (error) {
        setMessage(formMode, error instanceof Error ? error.message : '请求失败，请稍后重试。')
      } finally {
        setBusy(false)
      }
    })
  })

  updateMode(mode, false)
  window.setTimeout(() => {
    card?.classList.add('is-visible')
    if (!userInteracted) {
      const input = shell.querySelector(`[data-auth-form="${mode}"] input:not([type="checkbox"])`)
      if (input instanceof HTMLInputElement) input.focus({ preventScroll: true })
    }
  }, 1500)
})()
