(() => {
  const logoutButtons = Array.from(document.querySelectorAll('[data-member-logout]'))
  if (logoutButtons.length === 0) return

  let busy = false

  /** 安全读取统一 ApiResult；非 JSON 响应按请求失败处理。 */
  const readJson = async (response) => {
    const text = await response.text()
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch {
      throw new Error('退出接口返回了无法识别的内容。')
    }
  }

  /** 按接口返回的请求头名称提交 CSRF 保护的退出请求。 */
  const logoutWith = async (csrfPath, logoutPath) => {
    const csrfResponse = await fetch(csrfPath, {
      credentials: 'include',
      headers: { Accept: 'application/json' }
    })
    const csrfBody = await readJson(csrfResponse)
    const headerName = String(csrfBody?.data?.headerName || '').trim()
    const token = String(csrfBody?.data?.token || '')
    if (!csrfResponse.ok || !csrfBody?.success || !headerName || !token) {
      return false
    }

    const response = await fetch(logoutPath, {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        [headerName]: token
      }
    })
    const body = await readJson(response)
    return response.ok && body?.success === true
  }

  /**
   * 管理员优先调用后台退出接口，以便同时清除前台和后台 Cookie；
   * 普通会员或尚未建立后台会话时回退到会员退出接口。
   */
  const logout = async () => {
    if (await logoutWith('/api/admin/auth/csrf', '/api/admin/auth/logout')) {
      return true
    }
    return logoutWith('/api/member/auth/csrf', '/api/member/auth/logout')
  }

  logoutButtons.forEach((button) => {
    button.addEventListener('click', async () => {
      if (busy) return
      busy = true
      logoutButtons.forEach((item) => {
        item.disabled = true
        item.textContent = '退出中…'
      })

      try {
        if (!await logout()) {
          throw new Error('退出登录失败，请稍后重试。')
        }
        window.location.assign('/site')
      } catch (error) {
        logoutButtons.forEach((item) => {
          item.disabled = false
          item.textContent = '重试退出'
          item.title = error instanceof Error ? error.message : '退出登录失败。'
        })
        busy = false
      }
    })
  })
})()
