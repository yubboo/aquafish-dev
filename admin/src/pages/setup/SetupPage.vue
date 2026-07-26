<!-- P0-3-2B-15E：数据库检测结果弹窗化 -->
<!-- P0-3-2B-15D：统一危险重装弹窗与滚动收口 -->
<!-- P0-3-2B-15C：弹窗重装确认、耗时恢复与服务页视觉收口 -->
<!-- P0-3-2B-14D：真实连接测试移动到固定底栏 -->
<!-- P0-3-2B-14C：数据库与 Redis 真实测试统一到底部 -->
<!-- P0-3-2B-14B：Redis 摘要下沉与重新选择按钮迁移 -->
<!-- P0-3-2B-14A：数据库摘要信息下沉到连接卡片 -->
<!-- P0-3-2B-12：分发包数据库与 Redis 两小步服务向导 -->
<!-- P0-3-2B-11：分发包服务配置三段式向导 -->
<!-- P0-3-2B-10D：两栏对齐与 Redis 无跳动切换 -->
<!-- P0-3-2B-10C：数据库与 Redis 桌面端两栏布局 -->
<!-- P0-3-2B-10B：数据库图标、页面密度与 Redis 动画修正 -->
<!-- P0-3-2B-10：数据库与 Redis 配置页 UI 精修 -->
<!-- P0-3-2B-9C：基于真实耗时的自适应平滑安装进度 -->
<!-- P0-3-2B-9B：密码显示按钮与校验图标分离 -->
<!-- P0-3-2B-9：安装器表单交互与视觉精修 -->
<!--
  Aquafish 首次安装向导主页面。
  覆盖欢迎、协议、真实环境检测、数据库/Redis 测试、站点与管理员、安装进度和完成选择；
  所有状态与写操作来自 setup API，已安装系统会在加载上下文前被路由守卫移出本页面。
-->
<template>
  <main class="setup-page" :class="`setup-page--${entryScreen}`">
    <section v-if="loading" class="setup-card setup-state-card" aria-live="polite">
      <span class="setup-loading-ring" aria-hidden="true"></span>
      <h1>正在准备安装器</h1>
      <p>读取安装状态并执行第一轮服务器环境探测...</p>
    </section>

    <section v-else-if="errorMessage && !context" class="setup-card setup-state-card">
      <CircleCloseFilled class="setup-state-icon is-error" />
      <h1>安装器暂时无法启动</h1>
      <p>{{ errorMessage }}</p>
      <button type="button" class="setup-primary" @click="load">重新检测</button>
    </section>

    <template v-else-if="context && status">
      <section v-if="entryScreen === 'welcome'" class="setup-card setup-card--welcome">
        <header class="setup-brand-row">
          <span class="setup-brand">Aquafish</span>
          <span class="setup-mode-badge">{{ context.deploymentLabel }}</span>
        </header>

        <div class="setup-welcome-hero">
          <p class="setup-eyebrow">AQUAFISH SETUP</p>
          <h1>欢迎使用 Aquafish</h1>
          <p>用一套清晰、可验证的流程完成环境检查、服务连接和站点初始化。</p>
          <button type="button" class="setup-primary setup-primary--hero" @click="startInstall">
            <span>开始安装</span>
            <ArrowRight aria-hidden="true" />
          </button>
        </div>

        <div class="setup-welcome-features">
          <article>
            <Monitor aria-hidden="true" />
            <div><strong>真实环境检测</strong><span>目录探针、磁盘、内存与运行时驱动</span></div>
          </article>
          <article>
            <DataAnalysis aria-hidden="true" />
            <div><strong>三类数据库</strong><span>MySQL、MariaDB、PostgreSQL 真连接测试</span></div>
          </article>
          <article>
            <Connection aria-hidden="true" />
            <div><strong>Redis 可选</strong><span>与数据库同页配置，不增加无意义步骤</span></div>
          </article>
        </div>

        <footer class="setup-welcome-footer">
          <span>协议版本 {{ context.licenseVersion }}</span>
          <span>安装成功前不会锁定安装入口</span>
        </footer>
      </section>

      <section v-else-if="entryScreen === 'agreement'" class="setup-card setup-card--agreement">
        <header class="setup-agreement-header">
          <div>
            <p class="setup-eyebrow">安装前须知</p>
            <h1>Aquafish 安装与数据责任协议</h1>
            <p>请完整阅读协议；倒计时结束或滚动到底部后才可同意。</p>
          </div>
          <span class="setup-countdown" :class="{ 'is-ready': agreementReady }">
            {{ agreementReady ? '可以继续' : `${agreementSeconds} 秒` }}
          </span>
        </header>

        <div
          ref="agreementContent"
          class="setup-agreement-content"
          tabindex="0"
          @scroll="handleAgreementScroll"
        >
          <h2>Aquafish 安装许可与风险说明 {{ context.licenseVersion }}</h2>
          <p>感谢你选择 Aquafish。继续安装表示你确认拥有当前服务器、数据库和域名的合法管理权限，并愿意承担部署与运维责任。</p>

          <h3>一、安装与运行环境</h3>
          <p>你应确保 Java、磁盘、目录权限、数据库和网络环境满足安装要求。安装器会执行真实探测，但无法代替服务器安全审计和日常运维。</p>

          <h3>二、数据与凭据</h3>
          <p>数据库、Redis、超级管理员密码只用于完成连接和初始化。你应使用独立强密码，并妥善保管部署平台、数据库、Redis 和后台管理凭据。</p>

          <h3>三、备份与恢复</h3>
          <p>升级、迁移、修改数据库结构、安装第三方扩展或卸载前，必须同时备份数据库和 Aquafish 工作目录。未验证的备份不能视为可恢复备份。</p>

          <h3>四、主题、插件与第三方服务</h3>
          <p>第三方主题、插件、对象存储、邮件、AI 服务等组件受各自许可证和安全策略约束。安装第三方扩展前，应检查来源、权限和兼容性。</p>

          <h3>五、安装状态与故障处理</h3>
          <p>安装器只在数据库迁移、超级管理员创建和最终安装状态提交成功后关闭安装入口。安装中断时请保留日志，不要反复覆盖配置或直接删除数据库表。</p>

          <h3>六、最终确认</h3>
          <p>点击“同意并继续”表示你已经阅读并理解以上内容，确认输入的信息真实有效，并同意由安装器执行必要的配置写入和数据库迁移。</p>
          <p class="setup-agreement-end">协议已阅读完毕</p>
        </div>

        <div class="setup-agreement-hint" :class="{ 'is-ready': agreementReady }">
          <CircleCheckFilled v-if="agreementReady" aria-hidden="true" />
          <Clock v-else aria-hidden="true" />
          <span>{{ agreementReady ? agreementReadyReason : '请继续阅读，或等待 10 秒倒计时结束。' }}</span>
        </div>

        <footer class="setup-agreement-actions">
          <button type="button" class="setup-secondary" @click="rejectAgreement">拒绝并返回</button>
          <button type="button" class="setup-primary" :disabled="!agreementReady || environmentRefreshing" @click="acceptAgreement">
            {{ environmentRefreshing ? '正在检测环境...' : agreementReady ? '同意并继续' : `同意（${agreementSeconds}）` }}
          </button>
        </footer>
      </section>

      <section v-else class="setup-card setup-card--wizard">
        <header class="setup-header setup-header--with-step-summary">
          <div class="setup-header__intro">
            <div class="setup-header__badges">
              <p class="setup-kicker">Aquafish Setup</p>
              <span class="setup-mode-badge">{{ context.deploymentLabel }}</span>
            </div>
            <h1>系统安装向导</h1>
            <p>每一步都由真实检测结果驱动，安装完成后才会关闭安装入口。</p>
          </div>

          <aside
            v-if="currentStep"
            class="setup-header__step-summary"
            aria-label="当前安装步骤"
          >
            <div class="setup-header__step-copy">
              <span>步骤 {{ currentStepIndex + 1 }} / {{ steps.length }}</span>
              <strong>{{ currentStep.title }}</strong>
              <p>{{ currentStep.description }}</p>
            </div>

            <button
              v-if="currentStep.key === 'environment'"
              type="button"
              class="setup-text-button setup-header__refresh"
              :disabled="environmentRefreshing"
              @click="refreshEnvironment"
            >
              <RefreshRight :class="{ 'is-spinning': environmentRefreshing }" aria-hidden="true" />
              {{ environmentRefreshing ? '检测中' : '重新检测' }}
            </button>
          </aside>
        </header>

        <nav class="setup-steps" aria-label="安装步骤">
          <button
            v-for="(step, index) in steps"
            :key="step.key"
            type="button"
            class="setup-step"
            :class="{
              'setup-step--active': index === currentStepIndex,
              'setup-step--done': index < currentStepIndex || (step.key === 'complete' && installationCompleted),
            }"
            :disabled="index > maxVisitedStep || step.key === 'complete' || installing"
            @click="goToStep(index)"
          >
            <span>{{ index + 1 }}</span>
            <strong>{{ step.shortTitle }}</strong>
          </button>
        </nav>

        <form class="setup-form setup-wizard__body" @submit.prevent="nextStep">
          <Transition name="setup-step-panel" mode="out-in">
            <div
              :key="currentStep.key"
              class="setup-step-panel-shell"
            >
              <section v-if="currentStep.key === 'environment'" class="setup-panel">
            <div class="setup-check-list">
              <article v-for="check in context.checks" :key="check.key" class="setup-check-item" :class="check.passed ? 'is-pass' : 'is-fail'">
                <CircleCheckFilled v-if="check.passed" class="setup-check-icon" aria-hidden="true" />
                <CircleCloseFilled v-else class="setup-check-icon" aria-hidden="true" />
                <div>
                  <strong>{{ check.label }}</strong>
                  <p>{{ check.detail }}</p>
                </div>
                <em>{{ check.required ? '必需' : '信息' }}</em>
              </article>
            </div>
            <p class="setup-inline-note" :class="effectiveEnvironmentReady ? 'is-success' : 'is-error'">
              {{ effectiveEnvironmentReady
                ? '全部必需检查已通过，可以继续配置服务。'
                : (status.note || '存在未通过的必需检查，修复后请重新检测。') }}
            </p>
          </section>

                              <section
            ref="serviceFlowRef"
            v-else-if="currentStep.key === 'services'"
            class="setup-panel setup-services setup-service-flow setup-service-flow--two-step"
          >
            <header class="setup-service-flow__header">
              <div>
                <span>分发包服务配置</span>
                <h3>两步完成数据库与缓存配置</h3>
                <p>
                  先选择数据库和可选 Redis，
                  再在同一页完成真实连接测试。
                </p>
              </div>

              <strong>
                {{ serviceStageIndex + 1 }}
                /
                {{ serviceStages.length }}
              </strong>
            </header>

            <nav
              class="setup-service-flow__steps"
              aria-label="服务配置子步骤"
            >
              <button
                v-for="(stage, stageIndex) in serviceStages"
                :key="stage.key"
                type="button"
                :class="{
                  'is-active':
                    serviceStage === stage.key,
                  'is-complete':
                    stageIndex < serviceStageIndex,
                  'is-available':
                    stageIndex <= serviceStageVisited,
                }"
                :disabled="
                  stageIndex > serviceStageVisited
                "
                @click="
                  goToServiceStage(stage.key)
                "
              >
                <span>
                  <CircleCheckFilled
                    v-if="
                      stageIndex < serviceStageIndex
                    "
                    aria-hidden="true"
                  />

                  <b v-else>
                    {{ stageIndex + 1 }}
                  </b>
                </span>

                <div>
                  <strong>
                    {{ stage.title }}
                  </strong>

                  <small>
                    {{ stage.description }}
                  </small>
                </div>
              </button>
            </nav>

            <div
              :key="serviceStage"
              class="setup-service-stage-panel"
            >
              <!-- 2-1：选择数据库和可选 Redis -->
              <section
                v-if="
                  serviceStage === 'database-type'
                "
                class="setup-service-stage setup-service-stage--database-type setup-service-stage--service-selection"
              >
                <div
                  class="setup-service-stage__heading"
                >
                  <span>第一步</span>
                  <h3>选择数据库与 Redis</h3>

                  <p>
                    数据库必须三选一；
                    Redis 是可选增强，不选也能正常安装。
                  </p>
                </div>

                  <div
                    class="setup-service-database-grid"
                    role="radiogroup"
                    aria-label="选择数据库类型"
                  >
                    <button
                      v-for="database in databaseTypes"
                      :key="database.value"
                      type="button"
                      role="radio"
                      class="setup-service-database-card"
                      :class="[
                        'is-' + database.value,
                        {
                          'is-selected':
                            selectedDatabaseType
                              === database.value,
                        },
                      ]"
                      :aria-checked="
                        selectedDatabaseType
                          === database.value
                      "
                      @click="
                        selectDatabaseType(
                          database.value,
                        )
                      "
                    >
                      <span
                        class="setup-service-database-card__icon"
                        aria-hidden="true"
                      >
                        <svg
                          v-if="database.value === 'mysql'"
                          viewBox="0 0 64 64"
                          focusable="false"
                        >
                          <ellipse
                            cx="29"
                            cy="17"
                            rx="17"
                            ry="7"
                          />
                          <path
                            d="M12 17v23c0 4 7.6 7 17 7s17-3 17-7V17"
                          />
                          <path
                            d="M12 28c0 4 7.6 7 17 7s17-3 17-7"
                          />
                          <path
                            d="m41 11 8 6-8 6"
                          />
                        </svg>

                        <svg
                          v-else-if="
                            database.value
                              === 'mariadb'
                          "
                          viewBox="0 0 64 64"
                          focusable="false"
                        >
                          <path
                            d="M8 42c9-16 19-24 32-27 6-1 11 0 16 4-9 2-15 6-20 13-6 7-15 12-28 10Z"
                          />
                          <path
                            d="M14 42c8-3 15-8 21-16"
                          />
                          <path d="M11 50h42" />
                        </svg>

                        <svg
                          v-else
                          viewBox="0 0 64 64"
                          focusable="false"
                        >
                          <path
                            d="M19 19c0-8 5-13 13-13s13 5 13 13v11c0 8-5 13-13 13s-13-5-13-13Z"
                          />
                          <path
                            d="M19 22h-7c-3 0-5 2-5 5 0 4 3 6 8 6h4"
                          />
                          <path
                            d="M45 22h7c3 0 5 2 5 5 0 4-3 6-8 6h-4"
                          />
                          <path
                            d="M32 27v18c0 6 3 10 9 10"
                          />
                          <circle
                            cx="27"
                            cy="20"
                            r="1.7"
                          />
                          <circle
                            cx="37"
                            cy="20"
                            r="1.7"
                          />
                        </svg>
                      </span>

                      <span
                        class="setup-service-database-card__copy"
                      >
                        <strong>
                          {{ database.label }}
                        </strong>

                        <small>
                          {{ database.description }}
                        </small>

                        <em>
                          {{ database.recommendation }}
                        </em>
                      </span>

                      <span
                        class="setup-service-database-card__port"
                      >
                        默认端口 {{ database.port }}
                      </span>

                      <Transition
                        name="setup-service-check"
                      >
                        <CircleCheckFilled
                          v-if="
                            selectedDatabaseType
                              === database.value
                          "
                          class="setup-service-database-card__check"
                          aria-hidden="true"
                        />
                      </Transition>
                    </button>
                  </div>


                <div
                  class="setup-service-optional-divider"
                >
                  <span>可选服务</span>
                </div>

                <button
                  type="button"
                  class="setup-service-redis-option-card"
                  :class="{
                    'is-selected':
                      form.redisEnabled,
                  }"
                  :aria-pressed="
                    form.redisEnabled
                  "
                  @click="
                    chooseRedis(
                      !form.redisEnabled,
                    )
                  "
                >
                  <span
                    class="setup-service-redis-option-card__icon"
                    aria-hidden="true"
                  >
                    <svg
                      viewBox="0 0 48 48"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2.2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path
                        d="M24 7 40 15 24 23 8 15 24 7Z"
                      />

                      <path
                        d="M8 22l16 8 16-8"
                      />

                      <path
                        d="M8 29l16 8 16-8"
                      />
                    </svg>
                  </span>

                  <span
                    class="setup-service-redis-option-card__copy"
                  >
                    <span>
                      <b>可选增强</b>

                      <em>
                        {{
                          form.redisEnabled
                            ? '已选中'
                            : '未选中'
                        }}
                      </em>
                    </span>

                    <strong>
                      Redis 缓存与会话
                    </strong>

                    <small>
                      正式运营、论坛高频访问和登录会话较多时推荐启用；
                      本地测试和小型站点可以暂不选择。
                    </small>
                  </span>

                  <span
                    class="setup-service-redis-option-card__selector"
                    :class="{
                      'is-selected':
                        form.redisEnabled,
                    }"
                    aria-hidden="true"
                  >
                    <CircleCheckFilled
                      v-if="form.redisEnabled"
                    />

                    <span v-else></span>
                  </span>
                </button>

                <p
                  class="setup-service-stage__note"
                >
                  选择后仍可返回修改。
                  切换数据库会更新默认端口；
                  取消 Redis 会清除旧测试结果。
                </p>
              </section>

              <!-- 2-2：左右双栏填写并真实测试 -->
              <section
                v-else
                class="setup-service-stage setup-service-stage--connection-pair"
              >

                <div
                  class="setup-service-dual-layout"
                  :class="{
                    'is-database-only':
                      !form.redisEnabled,
                  }"
                >
                  <!-- 左侧数据库 -->
                  <section
                    class="setup-service-column-card setup-service-column-card--database"
                  >
                    <header
                      class="setup-service-column-card__heading setup-service-column-card__heading--database"
                    >
                      <div
                        class="setup-service-column-card__copy"
                      >
                        <div
                          class="setup-service-database-identity"
                        >
                          <span>数据库</span>

                          <strong>
                            {{ selectedDatabaseLabel }}
                          </strong>
                        </div>

                        <h3>数据库连接</h3>

                        <p>
                          填写连接资料并完成真实 R2DBC
                          连接与数据库状态识别。
                        </p>
                      </div>

                      <div
                        class="setup-service-column-card__meta"
                      >
                        <div
                          class="setup-service-database-port"
                        >
                          <span>默认端口</span>

                          <strong>
                            {{
                              defaultDatabasePort(
                                form.databaseType,
                              )
                            }}
                          </strong>
                        </div>

                        <strong
                          class="setup-service-column-card__status"
                          :class="
                            databaseInspection?.mode
                              === 'STATE_UNAVAILABLE'
                              ? 'is-error'
                              : databasePassed
                                ? 'is-success'
                                : 'is-pending'
                          "
                        >
                          {{
                            databaseInspection?.mode
                              === 'STATE_UNAVAILABLE'
                              ? '状态失败'
                              : databasePassed
                                ? '已通过'
                                : '等待测试'
                          }}
                        </strong>
                      </div>
                    </header>

                  <fieldset
                    class="setup-fieldset setup-service-connection-fieldset"
                  >
                    <legend>数据库连接信息</legend>

                    <p>
                      测试只建立真实 R2DBC 连接并识别 Aquafish
                      数据状态，不会写入业务表。
                    </p>

                    <div
                      class="setup-service-connection-grid"
                    >
                      <label>
                        <span>主机</span>
                        <input
                          v-model.trim="
                            form.databaseHost
                          "
                          type="text"
                          required
                          autocomplete="off"
                          @input="invalidateDatabase"
                        >
                        <small>
                          本机数据库通常填写 127.0.0.1。
                        </small>
                      </label>

                      <label>
                        <span>端口</span>
                        <input
                          v-model.number="
                            form.databasePort
                          "
                          type="number"
                          min="1"
                          max="65535"
                          required
                          @input="invalidateDatabase"
                        >
                        <small>
                          已根据数据库类型自动填写。
                        </small>
                      </label>

                      <label>
                        <span>数据库名</span>
                        <input
                          v-model.trim="
                            form.databaseName
                          "
                          type="text"
                          required
                          autocomplete="off"
                          @input="invalidateDatabase"
                        >
                        <small>
                          填写已有空库或需要恢复、重装的目标库。
                        </small>
                      </label>

                      <label>
                        <span>数据库用户</span>
                        <input
                          v-model.trim="
                            form.databaseUsername
                          "
                          type="text"
                          required
                          autocomplete="username"
                          @input="invalidateDatabase"
                        >
                        <small>
                          该账号需要拥有目标库的建表和读写权限。
                        </small>
                      </label>

                      <label>
                        <span>数据库密码</span>

                        <div
                          class="setup-input-shell setup-input-shell--password"
                        >
                          <input
                            v-model="
                              form.databasePassword
                            "
                            class="setup-password-input"
                            :type="
                              databasePasswordVisible
                                ? 'text'
                                : 'password'
                            "
                            autocomplete="current-password"
                            @input="invalidateDatabase"
                          >

                          <button
                            type="button"
                            class="setup-password-toggle"
                            :class="{
                              'is-visible':
                                databasePasswordVisible,
                            }"
                            :aria-label="
                              databasePasswordVisible
                                ? '隐藏数据库密码'
                                : '显示数据库密码'
                            "
                            :aria-pressed="
                              databasePasswordVisible
                            "
                            @mousedown.prevent
                            @click.stop="
                              databasePasswordVisible
                                = !databasePasswordVisible
                            "
                          >
                            <svg
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              stroke-width="1.8"
                              stroke-linecap="round"
                              stroke-linejoin="round"
                              aria-hidden="true"
                            >
                              <path
                                d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"
                              />
                              <circle
                                cx="12"
                                cy="12"
                                r="3"
                              />
                              <path
                                v-if="
                                  databasePasswordVisible
                                "
                                d="M4 4l16 16"
                              />
                            </svg>
                          </button>
                        </div>

                        <small>
                          密码只存在于当前安装会话。
                        </small>
                      </label>

                      <label>
                        <span>表前缀</span>
                        <input
                          v-model.trim="
                            form.tablePrefix
                          "
                          type="text"
                          required
                          autocomplete="off"
                          @input="invalidateDatabase"
                        >
                        <small>
                          例如 <code>aq_</code>。
                        </small>
                      </label>
                    </div>
                  </fieldset>



                  <!-- 数据库身份识别、恢复与安全重装仍然保留。 -->

                  </section>

                  <!-- 右侧 Redis -->
                  <section
                    class="setup-service-column-card setup-service-column-card--redis"
                    :class="{
                      'is-disabled':
                        !form.redisEnabled,
                    }"
                  >
                    <header
                      class="setup-service-column-card__heading setup-service-column-card__heading--redis"
                    >
                      <div
                        class="setup-service-column-card__copy"
                      >
                        <div
                          class="setup-service-redis-identity"
                        >
                          <span>Redis</span>

                          <strong>
                            {{
                              form.redisEnabled
                                ? '已启用'
                                : '基础模式'
                            }}
                          </strong>
                        </div>

                        <h3>Redis 缓存与会话</h3>

                        <p>
                          {{
                            form.redisEnabled
                              ? '填写连接资料并执行 AUTH、SELECT 与 PING。'
                              : '当前未选择 Redis，系统将使用基础模式。'
                          }}
                        </p>
                      </div>

                      <div
                        class="setup-service-column-card__meta"
                      >
                        <div
                          class="setup-service-redis-port"
                        >
                          <span>默认端口</span>

                          <strong>6379</strong>
                        </div>

                        <strong
                          class="setup-service-column-card__status"
                          :class="
                            !form.redisEnabled
                              ? 'is-muted'
                              : redisPassed
                                ? 'is-success'
                                : 'is-pending'
                          "
                        >
                          {{
                            !form.redisEnabled
                              ? '未启用'
                              : redisPassed
                                ? '已通过'
                                : '等待测试'
                          }}
                        </strong>
                      </div>
                    </header>

                    <template
                      v-if="form.redisEnabled"
                    >
                    <fieldset
                      class="setup-fieldset setup-service-redis-fieldset"
                    >
                      <legend>Redis 连接信息</legend>

                      <p>
                        启用后必须通过真实 AUTH、SELECT 和 PING。
                      </p>

                      <div
                        class="setup-service-redis-grid"
                      >
                        <label>
                          <span>主机</span>
                          <input
                            v-model.trim="
                              form.redisHost
                            "
                            type="text"
                            required
                            @input="invalidateRedis"
                          >
                        </label>

                        <label>
                          <span>端口</span>
                          <input
                            v-model.number="
                              form.redisPort
                            "
                            type="number"
                            min="1"
                            max="65535"
                            required
                            @input="invalidateRedis"
                          >
                        </label>

                        <label>
                          <span>数据库编号</span>
                          <select
                            v-model.number="
                              form.redisDatabase
                            "
                            @change="invalidateRedis"
                          >
                            <option
                              v-for="index in 16"
                              :key="index - 1"
                              :value="index - 1"
                            >
                              {{ index - 1 }}
                            </option>
                          </select>
                        </label>

                        <label>
                          <span>用户名（可空）</span>
                          <input
                            v-model.trim="
                              form.redisUsername
                            "
                            type="text"
                            autocomplete="username"
                            @input="invalidateRedis"
                          >
                        </label>

                        <label
                          class="setup-service-redis-password"
                        >
                          <span>密码（可空）</span>

                          <div
                            class="setup-input-shell setup-input-shell--password"
                          >
                            <input
                              v-model="
                                form.redisPassword
                              "
                              class="setup-password-input"
                              :type="
                                redisPasswordVisible
                                  ? 'text'
                                  : 'password'
                              "
                              autocomplete="current-password"
                              @input="invalidateRedis"
                            >

                            <button
                              type="button"
                              class="setup-password-toggle"
                              :class="{
                                'is-visible':
                                  redisPasswordVisible,
                              }"
                              :aria-pressed="
                                redisPasswordVisible
                              "
                              @mousedown.prevent
                              @click.stop="
                                redisPasswordVisible
                                  = !redisPasswordVisible
                              "
                            >
                              <svg
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                stroke-width="1.8"
                                stroke-linecap="round"
                                stroke-linejoin="round"
                                aria-hidden="true"
                              >
                                <path
                                  d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z"
                                />
                                <circle
                                  cx="12"
                                  cy="12"
                                  r="3"
                                />
                                <path
                                  v-if="
                                    redisPasswordVisible
                                  "
                                  d="M4 4l16 16"
                                />
                              </svg>
                            </button>
                          </div>
                        </label>

                        <label
                          class="setup-checkbox-card setup-service-redis-ssl"
                        >
                          <span>安全连接</span>
                          <input
                            v-model="form.redisSsl"
                            type="checkbox"
                            @change="invalidateRedis"
                          >
                          <small>
                            远程 Redis 建议启用 TLS/SSL。
                          </small>
                        </label>
                      </div>


                    </fieldset>
                    </template>

                    <div
                      v-else
                      class="setup-service-redis-disabled-panel"
                    >
                      <span
                        class="setup-service-redis-disabled-panel__icon"
                        aria-hidden="true"
                      >
                        <svg
                          viewBox="0 0 48 48"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="2"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                        >
                          <path
                            d="M10 16h28v20H10z"
                          />

                          <path
                            d="M16 16v-4h16v4"
                          />

                          <path
                            d="M17 26h14"
                          />
                        </svg>
                      </span>

                      <div>
                        <strong>
                          当前未启用 Redis
                        </strong>

                        <p>
                          Aquafish 将以基础模式安装，
                          不会请求 Redis 服务器，
                          也不需要 Redis 测试。
                        </p>
                      </div>

                      <ul>
                        <li>
                          适合本地测试和小型站点
                        </li>

                        <li>
                          不影响数据库迁移和后台初始化
                        </li>

                        <li>
                          安装完成后可在后台服务配置中接入
                        </li>
                      </ul>

                      <button
                        type="button"
                        @click="
                          goToServiceStage(
                            'database-type',
                          )
                        "
                      >
                        返回选择并启用 Redis
                      </button>
                    </div>
                  </section>
                </div>


                <p
                  class="setup-service-stage__note setup-service-stage__note--pair"
                >
                  数据库必须通过真实测试；
                  已选择 Redis 时也必须通过真实 Redis
                  测试后才能继续。
                </p>
              </section>
            </div>
          </section>

<section
            v-else-if="currentStep.key === 'identity'"
            class="setup-panel setup-identity-grid"
          >
            <fieldset class="setup-fieldset setup-fieldset--identity">
              <legend>站点信息</legend>

              <p class="setup-fieldset-intro">
                这些信息用于生成站点公开配置，安装后仍可在后台修改。
              </p>

              <div class="setup-form-grid setup-form-grid--identity">
                <label
                  class="setup-field setup-field--half"
                  :class="fieldStateClass(siteNameState)"
                >
                  <span>站点名称</span>

                  <div class="setup-input-shell">
                    <input
                      v-model.trim="form.siteName"
                      type="text"
                      maxlength="100"
                      required
                      autocomplete="organization"
                      :aria-invalid="siteNameState === 'invalid'"
                      @blur="markIdentityTouched('siteName')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="siteNameState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="siteNameState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ siteNameMessage }}
                  </small>
                </label>

                <label
                  class="setup-field setup-field--half"
                  :class="fieldStateClass(siteUrlState)"
                >
                  <span>站点地址</span>

                  <div class="setup-input-shell">
                    <input
                      v-model.trim="form.siteUrl"
                      type="url"
                      required
                      placeholder="https://example.com"
                      :aria-invalid="siteUrlState === 'invalid'"
                      @blur="markIdentityTouched('siteUrl')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="siteUrlState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="siteUrlState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ siteUrlMessage }}
                  </small>
                </label>

                <label class="setup-field setup-field--half setup-field--neutral">
                  <span>默认语言</span>

                  <select v-model="form.locale">
                    <option value="zh-CN">简体中文</option>
                    <option value="en-US">English</option>
                  </select>

                  <small class="setup-field-message">
                    决定系统安装后的默认界面语言。
                  </small>
                </label>

                <label class="setup-field setup-field--half setup-field--neutral">
                  <span>时区</span>

                  <select v-model="form.timezone">
                    <option value="Asia/Shanghai">Asia/Shanghai</option>
                    <option value="UTC">UTC</option>
                  </select>

                  <small class="setup-field-message">
                    用于文章、日志和后台任务时间。
                  </small>
                </label>
              </div>
            </fieldset>

            <fieldset class="setup-fieldset setup-fieldset--identity">
              <legend>超级管理员</legend>

              <p class="setup-fieldset-intro">
                该账号拥有最高权限，请使用独立密码并妥善保存。
              </p>

              <div class="setup-form-grid setup-form-grid--identity">
                <label
                  class="setup-field setup-field--half"
                  :class="fieldStateClass(adminUsernameState)"
                >
                  <span>用户名</span>

                  <div class="setup-input-shell">
                    <input
                      v-model.trim="form.adminUsername"
                      type="text"
                      minlength="1"
                      maxlength="64"
                      required
                      autocomplete="username"
                      :aria-invalid="adminUsernameState === 'invalid'"
                      @blur="markIdentityTouched('adminUsername')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="adminUsernameState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="adminUsernameState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ adminUsernameMessage }}
                  </small>
                </label>

                <label
                  class="setup-field setup-field--half"
                  :class="fieldStateClass(adminDisplayNameState)"
                >
                  <span>显示名称</span>

                  <div class="setup-input-shell">
                    <input
                      v-model.trim="form.adminDisplayName"
                      type="text"
                      maxlength="100"
                      required
                      autocomplete="name"
                      :aria-invalid="adminDisplayNameState === 'invalid'"
                      @blur="markIdentityTouched('adminDisplayName')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="adminDisplayNameState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="adminDisplayNameState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ adminDisplayNameMessage }}
                  </small>
                </label>

                <label
                  class="setup-field setup-field--full"
                  :class="fieldStateClass(adminEmailState)"
                >
                  <span>邮箱（可空）</span>

                  <div class="setup-input-shell">
                    <input
                      v-model.trim="form.adminEmail"
                      type="email"
                      maxlength="191"
                      autocomplete="email"
                      :aria-invalid="adminEmailState === 'invalid'"
                      @blur="markIdentityTouched('adminEmail')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="adminEmailState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="adminEmailState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ adminEmailMessage }}
                  </small>
                </label>

                <label
                  class="setup-field setup-field--half"
                  :class="fieldStateClass(adminPasswordState)"
                >
                  <span>密码</span>

                  <div class="setup-input-shell setup-input-shell--password">
                    <input
                      v-model="form.adminPassword"
                      class="setup-password-input"
                      :type="adminPasswordVisible ? 'text' : 'password'"
                      minlength="8"
                      maxlength="128"
                      required
                      autocomplete="new-password"
                      :aria-invalid="adminPasswordState === 'invalid'"
                      @blur="markIdentityTouched('adminPassword')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="adminPasswordState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="adminPasswordState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>

                    <button
                      type="button"
                      class="setup-password-toggle"
                      :class="{ 'is-visible': adminPasswordVisible }"
                      :aria-label="adminPasswordVisible ? '隐藏密码' : '显示密码'"
                      :aria-pressed="adminPasswordVisible"
                      :title="adminPasswordVisible ? '隐藏密码' : '显示密码'"
                      @mousedown.prevent
                      @click.stop="adminPasswordVisible = !adminPasswordVisible"
                    >
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.8"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        aria-hidden="true"
                      >
                        <path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z" />
                        <circle cx="12" cy="12" r="3" />
                        <path
                          v-if="adminPasswordVisible"
                          d="M4 4l16 16"
                        />
                      </svg>
                    </button>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ adminPasswordMessage }}
                  </small>
                </label>

                <label
                  class="setup-field setup-field--half"
                  :class="fieldStateClass(adminPasswordConfirmState)"
                >
                  <span>确认密码</span>

                  <div class="setup-input-shell setup-input-shell--password">
                    <input
                      v-model="form.adminPasswordConfirm"
                      class="setup-password-input"
                      :type="adminPasswordConfirmVisible ? 'text' : 'password'"
                      minlength="8"
                      maxlength="128"
                      required
                      autocomplete="new-password"
                      :aria-invalid="adminPasswordConfirmState === 'invalid'"
                      @blur="markIdentityTouched('adminPasswordConfirm')"
                    >

                    <Transition name="setup-field-status">
                      <CircleCheckFilled
                        v-if="adminPasswordConfirmState === 'valid'"
                        class="setup-field-status-icon is-valid"
                        aria-hidden="true"
                      />

                      <CircleCloseFilled
                        v-else-if="adminPasswordConfirmState === 'invalid'"
                        class="setup-field-status-icon is-invalid"
                        aria-hidden="true"
                      />
                    </Transition>

                    <button
                      type="button"
                      class="setup-password-toggle"
                      :class="{ 'is-visible': adminPasswordConfirmVisible }"
                      :aria-label="adminPasswordConfirmVisible ? '隐藏确认密码' : '显示确认密码'"
                      :aria-pressed="adminPasswordConfirmVisible"
                      :title="adminPasswordConfirmVisible ? '隐藏确认密码' : '显示确认密码'"
                      @mousedown.prevent
                      @click.stop="adminPasswordConfirmVisible = !adminPasswordConfirmVisible"
                    >
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.8"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        aria-hidden="true"
                      >
                        <path d="M2.5 12s3.4-6 9.5-6 9.5 6 9.5 6-3.4 6-9.5 6-9.5-6-9.5-6Z" />
                        <circle cx="12" cy="12" r="3" />
                        <path
                          v-if="adminPasswordConfirmVisible"
                          d="M4 4l16 16"
                        />
                      </svg>
                    </button>
                  </div>

                  <small
                    class="setup-field-message"
                    aria-live="polite"
                  >
                    {{ adminPasswordConfirmMessage }}
                  </small>
                </label>

                <section
                  class="setup-password-strength"
                  :class="'is-' + passwordStrength.level"
                  aria-live="polite"
                >
                  <div class="setup-password-strength__heading">
                    <div>
                      <span>密码强度</span>
                      <strong>{{ passwordStrength.label }}</strong>
                    </div>

                    <em>
                      {{ passwordStrength.score }} / 5
                    </em>
                  </div>

                  <div
                    class="setup-password-strength__bars"
                    role="meter"
                    aria-label="管理员密码强度"
                    :aria-valuenow="passwordStrength.score"
                    aria-valuemin="0"
                    aria-valuemax="5"
                  >
                    <i
                      v-for="index in 3"
                      :key="index"
                      :class="{
                        'is-active': index <= passwordStrength.bars,
                      }"
                    ></i>
                  </div>

                  <ul class="setup-password-rules">
                    <li
                      v-for="rule in passwordRules"
                      :key="rule.key"
                      :class="{ 'is-pass': rule.passed }"
                    >
                      <CircleCheckFilled
                        v-if="rule.passed"
                        aria-hidden="true"
                      />

                      <span
                        v-else
                        class="setup-password-rule-dot"
                        aria-hidden="true"
                      ></span>

                      <span>{{ rule.label }}</span>
                    </li>
                  </ul>

                  <p>
                    字母、数字和符号用于提高强度，不会额外改变后端的
                    8–128 位安全底线。
                  </p>
                </section>
              </div>
            </fieldset>
          </section>

          <section v-else-if="currentStep.key === 'install'" class="setup-panel setup-panel--install">
            <template v-if="!installing && progressItems.length === 0">
              <div class="setup-review-sections">
                <section class="setup-review-section">
                  <div class="setup-review-section__heading">
                    <span>01</span>
                    <div>
                      <h3>部署与数据库</h3>
                      <p>重新核对数据库目标，密码只显示填写状态。</p>
                    </div>
                  </div>

                  <div class="setup-review-grid setup-review-grid--four">
                    <article><span>部署方式</span><strong>{{ context.deploymentLabel }}</strong></article>
                    <article><span>数据库类型</span><strong>{{ context.databaseManaged ? `${context.database.type}（平台托管）` : databaseLabel }}</strong></article>
                    <article><span>数据库主机</span><strong>{{ context.databaseManaged ? context.database.host : form.databaseHost }}</strong></article>
                    <article><span>数据库端口</span><strong>{{ context.databaseManaged ? context.database.port : form.databasePort }}</strong></article>
                    <article><span>数据库名称</span><strong>{{ effectiveDatabaseName }}</strong></article>
                    <article><span>数据库用户</span><strong>{{ context.databaseManaged ? context.database.username : form.databaseUsername }}</strong></article>
                    <article><span>表前缀</span><strong>{{ context.databaseManaged ? context.database.tablePrefix : form.tablePrefix }}</strong></article>
                    <article>
                      <span>数据库密码</span>
                      <strong>
                        {{
                          context.databaseManaged
                            ? context.database.passwordConfigured
                              ? '平台已注入（不显示）'
                              : '未配置'
                            : form.databasePassword
                              ? '已填写（不显示）'
                              : '未填写'
                        }}
                      </strong>
                    </article>
                  </div>
                </section>

                <section class="setup-review-section">
                  <div class="setup-review-section__heading">
                    <span>02</span>
                    <div>
                      <h3>Redis</h3>
                      <p>未启用时保持基础模式，启用后显示全部非敏感连接信息。</p>
                    </div>
                  </div>

                  <div class="setup-review-grid setup-review-grid--four">
                    <article><span>启用状态</span><strong>{{ redisSummary }}</strong></article>

                    <template v-if="!context.redisManaged && form.redisEnabled">
                      <article><span>Redis 主机</span><strong>{{ form.redisHost }}</strong></article>
                      <article><span>Redis 端口</span><strong>{{ form.redisPort }}</strong></article>
                      <article><span>数据库编号</span><strong>{{ form.redisDatabase }}</strong></article>
                      <article><span>Redis 用户名</span><strong>{{ form.redisUsername || '未填写' }}</strong></article>
                      <article><span>安全连接</span><strong>{{ form.redisSsl ? '已启用 TLS/SSL' : '未启用' }}</strong></article>
                      <article><span>Redis 密码</span><strong>{{ form.redisPassword ? '已填写（不显示）' : '未填写' }}</strong></article>
                    </template>

                    <article v-else-if="context.redisManaged">
                      <span>配置来源</span>
                      <strong>{{ context.redisConfigured ? '部署平台注入' : '平台未启用' }}</strong>
                    </article>
                  </div>
                </section>

                <section class="setup-review-section">
                  <div class="setup-review-section__heading">
                    <span>03</span>
                    <div>
                      <h3>站点与超级管理员</h3>
                      <p>确认公开信息和管理员身份，管理员密码不会显示明文。</p>
                    </div>
                  </div>

                  <div class="setup-review-grid setup-review-grid--four">
                    <article><span>站点名称</span><strong>{{ form.siteName }}</strong></article>
                    <article><span>站点地址</span><strong>{{ form.siteUrl }}</strong></article>
                    <article><span>默认语言</span><strong>{{ form.locale === 'zh-CN' ? '简体中文' : form.locale }}</strong></article>
                    <article><span>时区</span><strong>{{ form.timezone }}</strong></article>
                    <article><span>管理员用户名</span><strong>{{ form.adminUsername }}</strong></article>
                    <article><span>管理员显示名称</span><strong>{{ form.adminDisplayName }}</strong></article>
                    <article><span>管理员邮箱</span><strong>{{ form.adminEmail || '未填写' }}</strong></article>
                    <article><span>管理员密码</span><strong>{{ form.adminPassword ? '已填写（不显示）' : '未填写' }}</strong></article>
                  </div>
                </section>
              </div>

              <div
                class="setup-warning"
                :class="{
                  'is-danger': reinstallRequested,
                }"
              >
                <Lock aria-hidden="true" />

                <div>
                  <strong>
                    {{
                      reinstallRequested
                        ? '即将永久清理当前 Aquafish 并重新安装'
                        : '即将执行不可跳过的初始化'
                    }}
                  </strong>

                  <p>
                    {{
                      reinstallRequested
                        ? '点击开始安装后只会打开最终确认弹窗；确认前不会切换进度、不会调用清理接口。确认后，后端会重新核验并只删除 Aquafish 白名单表。'
                        : context.databaseManaged
                          ? '安装器将直接使用部署平台提供的服务配置，执行数据库迁移、创建超级管理员并提交安装状态。'
                          : '安装器会再次验证连接、写入运行配置、预检并执行数据库迁移、创建超级管理员，最后提交安装状态。'
                    }}
                  </p>
                </div>
              </div>
            </template>

            <div
              v-else
              class="setup-install-progress"
              :class="{ 'is-reinstall': reinstallRequested }"
              aria-live="polite"
            >
              <div class="setup-progress-heading">
                <div>
                  <span>{{ reinstallRequested ? '系统重装进度' : '系统安装进度' }}</span>
                  <strong>{{ Math.round(installProgress) }}%</strong>
                </div>
                <p>{{ currentProgressMessage }}</p>
              </div>
              <div class="setup-progress-track" role="progressbar" aria-label="安装进度" :aria-valuenow="installProgress" aria-valuemin="0" aria-valuemax="100">
                <span :style="{ transform: 'scaleX(' + (installProgress / 100) + ')' }"></span>
              </div>
              <div class="setup-progress-list">
                <article v-for="(item, index) in progressItems" :key="`${index}-${item.message}`" :class="`is-${item.state}`">
                  <CircleCheckFilled v-if="item.state === 'done'" aria-hidden="true" />
                  <CircleCloseFilled v-else-if="item.state === 'error'" aria-hidden="true" />
                  <Loading v-else class="is-spinning" aria-hidden="true" />
                  <span>{{ item.message }}</span>
                </article>
              </div>
            </div>
          </section>

          <section v-else-if="currentStep.key === 'complete'" class="setup-complete">
            <CircleCheckFilled class="setup-complete__icon" aria-hidden="true" />
            <div><p class="setup-eyebrow">INSTALLATION COMPLETE</p><h2>Aquafish 安装完成</h2><p>站点和超级管理员已经创建，安装入口已安全锁定。</p></div>
            <div class="setup-summary setup-summary--four">
              <div><span>站点</span><strong>{{ form.siteName }}</strong></div>
              <div><span>管理员</span><strong>{{ form.adminUsername }}</strong></div>
              <div><span>数据库状态</span><strong>{{ status.databaseState }}</strong></div>
              <div><span>部署方式</span><strong>{{ context.deploymentLabel }}</strong></div>
            </div>
            <div class="setup-complete-actions">
              <RouterLink class="setup-primary" to="/login?redirect=%2Fadmin&installed=1" @click="rememberDestination('/login?redirect=%2Fadmin&installed=1')">进入管理后台</RouterLink>
              <RouterLink class="setup-secondary" to="/admin/themes" @click="rememberDestination('/admin/themes')">配置主题与插件</RouterLink>
              <a class="setup-link-button" :href="form.siteUrl || '/'" @click="rememberDestination(form.siteUrl)">直接访问站点</a>
            </div>
          </section>
            </div>
          </Transition>

          <Transition name="setup-feedback">
            <div
              v-if="errorMessage"
              class="setup-message setup-message--error"
            >
              {{ errorMessage }}
            </div>
          </Transition>

          <footer
            v-if="currentStep.key !== 'complete'"
            class="setup-actions"
            :class="{
              'has-service-test-actions':
                currentStep.key === 'services'
                && serviceStage === 'database-connection',
            }"
          >
            <button
              type="button"
              class="setup-secondary"
              :disabled="installing || submitting"
              @click="previousStep"
            >
              {{ previousLabel }}
            </button>

            <div
              v-if="
                currentStep.key === 'services'
                && serviceStage === 'database-connection'
              "
              class="setup-service-action-cluster"
              aria-label="服务连接测试与下一步操作"
              aria-live="polite"
            >
              <button
                type="button"
                class="setup-secondary setup-service-reselect-action"
                :disabled="
                  installing
                  || submitting
                  || databaseTesting
                  || redisTesting
                "
                @click="
                  goToServiceStage(
                    'database-type',
                  )
                "
              >
                重新选择
              </button>

              <button
                type="button"
                class="setup-database-status-action"
                :class="databaseStatusActionClass"
                :disabled="
                  !databaseStatusDialogReady
                  || installing
                  || submitting
                  || databaseTesting
                  || redisTesting
                "
                :aria-label="databaseStatusActionLabel"
                :title="
                  databaseStatusDialogReady
                    ? '查看本次真实数据库检测结果'
                    : databaseTesting
                      ? '正在等待真实数据库检测完成'
                      : '请先执行真实数据库连接测试'
                "
                @click="openDatabaseStatusDialog"
              >
                <Loading
                  v-if="databaseTesting"
                  class="is-spinning"
                  aria-hidden="true"
                />

                <CircleCheckFilled
                  v-else-if="
                    databaseInspection?.mode
                      === 'NEW_INSTALL'
                  "
                  aria-hidden="true"
                />

                <Clock
                  v-else-if="
                    databaseInspection?.mode
                      === 'INCOMPLETE_INSTALLATION'
                  "
                  aria-hidden="true"
                />

                <CircleCloseFilled
                  v-else-if="
                    databaseInspection?.mode
                      === 'INCOMPATIBLE_DATABASE'
                    || databaseInspection?.mode
                      === 'STATE_UNAVAILABLE'
                  "
                  aria-hidden="true"
                />

                <Monitor
                  v-else
                  aria-hidden="true"
                />

                <span>
                  {{ databaseStatusActionLabel }}
                </span>
              </button>

              <button
                type="button"
                class="setup-footer-test-button setup-footer-test-button--database"
                :class="{
                  'is-testing': databaseTesting,
                  'is-success': databasePassed,
                  'is-error':
                    databaseInspection?.mode
                      === 'STATE_UNAVAILABLE',
                }"
                :disabled="
                  installing
                  || submitting
                  || databaseTesting
                  || redisTesting
                "
                :aria-label="
                  databaseInspection?.mode
                    === 'STATE_UNAVAILABLE'
                    ? '数据库状态检测失败，重新测试'
                    : databasePassed
                      ? '重新测试数据库连接'
                      : '测试数据库连接'
                "
                :title="
                  databaseInspection?.mode
                    === 'STATE_UNAVAILABLE'
                    ? '数据库网络已连接，但安装状态无法识别'
                    : databasePassed
                      ? '数据库已经通过，可点击重新测试'
                      : '执行真实数据库连接与状态检测'
                "
                @click="runDatabaseTest"
              >
                <Loading
                  v-if="databaseTesting"
                  class="is-spinning"
                  aria-hidden="true"
                />

                <CircleCloseFilled
                  v-else-if="
                    databaseInspection?.mode
                      === 'STATE_UNAVAILABLE'
                  "
                  aria-hidden="true"
                />

                <CircleCheckFilled
                  v-else-if="databasePassed"
                  aria-hidden="true"
                />

                <Connection
                  v-else
                  aria-hidden="true"
                />

                <span class="setup-footer-test-button__copy">
                  <strong>
                    {{
                      databaseTesting
                        ? '正在测试数据库'
                        : databaseInspection?.mode
                            === 'STATE_UNAVAILABLE'
                          ? '状态检测失败'
                          : databasePassed
                            ? '数据库已通过'
                            : '测试数据库连接'
                    }}
                  </strong>

                  <small v-if="databaseResult">
                    耗时
                    {{
                      formatElapsedSeconds(
                        databaseResult.elapsedMillis,
                      )
                    }}
                  </small>

                  <small v-else-if="databaseTesting">
                    正在等待服务响应
                  </small>
                </span>
              </button>

              <button
                type="button"
                class="setup-footer-test-button setup-footer-test-button--redis"
                :class="{
                  'is-skipped': !form.redisEnabled,
                  'is-testing': redisTesting,
                  'is-success': redisPassed,
                }"
                :disabled="
                  !form.redisEnabled
                  || installing
                  || submitting
                  || databaseTesting
                  || redisTesting
                "
                :aria-label="
                  !form.redisEnabled
                    ? 'Redis 已跳过'
                    : redisPassed
                      ? '重新测试 Redis 连接'
                      : '测试 Redis 连接'
                "
                :title="
                  !form.redisEnabled
                    ? '当前使用基础模式，无需连接 Redis'
                    : redisPassed
                      ? 'Redis 已经通过，可点击重新测试'
                      : '执行真实 AUTH、SELECT 和 PING'
                "
                @click="runRedisTest"
              >
                <CircleCheckFilled
                  v-if="
                    !form.redisEnabled
                    || redisPassed
                  "
                  aria-hidden="true"
                />

                <Loading
                  v-else-if="redisTesting"
                  class="is-spinning"
                  aria-hidden="true"
                />

                <Connection
                  v-else
                  aria-hidden="true"
                />

                <span class="setup-footer-test-button__copy">
                  <strong>
                    {{
                      !form.redisEnabled
                        ? 'Redis 已跳过'
                        : redisTesting
                          ? '正在测试 Redis'
                          : redisPassed
                            ? 'Redis 已通过'
                            : '测试 Redis 连接'
                    }}
                  </strong>

                  <small v-if="!form.redisEnabled">
                    基础模式
                  </small>

                  <small v-else-if="redisResult">
                    耗时
                    {{
                      formatElapsedSeconds(
                        redisResult.elapsedMillis,
                      )
                    }}
                  </small>

                  <small v-else-if="redisTesting">
                    正在等待服务响应
                  </small>
                </span>
              </button>

              <button
                class="setup-primary"
                type="submit"
                :disabled="actionDisabled"
              >
                {{ actionLabel }}
              </button>
            </div>

            <button
              v-else
              class="setup-primary"
              type="submit"
              :disabled="actionDisabled"
            >
              {{ actionLabel }}
            </button>
          </footer>
        </form>
      </section>
    </template>

        <Transition name="setup-reinstall-dialog">
      <div
        v-if="
          databaseStatusDialogOpen
          && databaseInspection
        "
        class="setup-dialog-backdrop setup-dialog-backdrop--database-status"
        role="presentation"
        @click.self="closeDatabaseStatusDialog"
      >
        <section
          ref="databaseStatusDialogRef"
          class="setup-confirm-dialog setup-confirm-dialog--database-status"
          role="dialog"
          aria-modal="true"
          aria-labelledby="setup-database-status-title"
          aria-describedby="setup-database-status-description"
          tabindex="-1"
          @keydown.esc="closeDatabaseStatusDialog"
        >
          <div
            class="setup-confirm-dialog__icon setup-confirm-dialog__icon--database-status"
            :class="databaseStatusActionClass"
          >
            <Loading
              v-if="databaseTesting"
              class="is-spinning"
              aria-hidden="true"
            />

            <CircleCheckFilled
              v-else-if="
                databaseInspection.mode
                  === 'NEW_INSTALL'
              "
              aria-hidden="true"
            />

            <Clock
              v-else-if="
                databaseInspection.mode
                  === 'INCOMPLETE_INSTALLATION'
              "
              aria-hidden="true"
            />

            <CircleCloseFilled
              v-else-if="
                databaseInspection.mode
                  === 'INCOMPATIBLE_DATABASE'
                || databaseInspection.mode
                  === 'STATE_UNAVAILABLE'
              "
              aria-hidden="true"
            />

            <Monitor
              v-else
              aria-hidden="true"
            />
          </div>

          <div
            class="setup-confirm-dialog__content setup-database-status-dialog__content"
          >
            <p class="setup-confirm-dialog__eyebrow">
              DATABASE INSPECTION
            </p>

            <h2 id="setup-database-status-title">
              数据库检测结果
            </h2>

            <p id="setup-database-status-description">
              以下状态来自本次真实数据库连接与身份检测。
              修改任何数据库参数后，本结果会立即失效。
            </p>

            <section
                                v-if="databaseInspection"
                                class="setup-service-inspection"
                              >
                                <header>
                                  <span>
                                    {{ databaseInspection.mode }}
                                  </span>

                                  <div>
                                    <h3>
                                      {{ databaseStatusActionLabel }}
                                    </h3>

                                    <p>
                                      {{ databaseInspection.note }}
                                    </p>
                                  </div>
                                </header>

                                <div
                                  class="setup-summary setup-summary--three"
                                >
                                  <div>
                                    <span>安装状态</span>
                                    <strong>
                                      {{
                                        databaseInspection
                                          .installationState
                                      }}
                                    </strong>
                                  </div>

                                  <div>
                                    <span>数据库版本</span>
                                    <strong>
                                      {{
                                        databaseInspection
                                          .currentVersion
                                        || '未初始化'
                                      }}
                                      /
                                      {{
                                        databaseInspection
                                          .latestVersion
                                        || '未知'
                                      }}
                                    </strong>
                                  </div>

                                  <div>
                                    <span>Aquafish 表</span>
                                    <strong>
                                      {{
                                        databaseInspection
                                          .existingAquafishTables
                                      }}
                                      /
                                      {{
                                        databaseInspection
                                          .expectedAquafishTables
                                      }}
                                    </strong>
                                  </div>
                                </div>

                                <div
                                  v-if="
                                    databaseInspection.mode
                                      === 'EXISTING_INSTALLED'
                                    ||
                                    databaseInspection.mode
                                      === 'INCOMPLETE_INSTALLATION'
                                  "
                                  class="setup-database-choice"
                                >
                                  <p
                                    v-if="
                                      databaseNeedsRecovery
                                      && !reinstallRequested
                                    "
                                    class="setup-inline-note is-success"
                                  >
                                    默认保留全部数据。最终完成 Redis
                                    选择后，可恢复当前电脑配置并进入系统。
                                  </p>

                                  <label
                                    class="setup-switch-row setup-switch-row--danger"
                                  >
                                    <span>
                                      <strong>
                                        {{
                                          databaseNeedsRecovery
                                            ? '清空当前 Aquafish 并重新安装'
                                            : '清理安装残留并重新安装'
                                        }}
                                      </strong>

                                      <small>
                                        仅处理当前表前缀下的 Aquafish
                                        正式白名单表。
                                      </small>
                                    </span>

                                    <input
                                      v-model="reinstallRequested"
                                      type="checkbox"
                                      @change="
                                        handleReinstallChoice
                                      "
                                    >
                                  </label>

                                  <Teleport to="body">
                                    <Transition name="setup-reinstall-dialog">
                                      <div
                                        v-if="
                                          reinstallRequested
                                          && reinstallPreparationDialogOpen
                                        "
                                        class="setup-dialog-backdrop setup-dialog-backdrop--preparation"
                                        role="presentation"
                                        @click.self="
                                          cancelReinstallPreparation
                                        "
                                      >
                                        <section
                                          ref="reinstallPreparationDialogRef"
                                          class="setup-confirm-dialog setup-confirm-dialog--preparation"
                                          role="dialog"
                                          aria-modal="true"
                                          aria-labelledby="setup-reinstall-preparation-title"
                                          aria-describedby="setup-reinstall-preparation-description"
                                          tabindex="-1"
                                          @keydown.esc="
                                            cancelReinstallPreparation
                                          "
                                        >
                                          <div class="setup-confirm-dialog__icon">
                                            <Lock aria-hidden="true" />
                                          </div>

                                          <div
                                            class="setup-confirm-dialog__content setup-confirm-dialog__content--preparation"
                                          >
                                            <p class="setup-confirm-dialog__eyebrow">
                                              REINSTALL MODE
                                            </p>

                                            <h2 id="setup-reinstall-preparation-title">
                                              确认使用危险重装模式？
                                            </h2>

                                            <p id="setup-reinstall-preparation-description">
                                              重新安装会永久删除当前表前缀下的 Aquafish
                                              用户、内容、论坛、插件和主题设置。
                                              此处只确认模式，真正执行前仍会再次确认。
                                            </p>

                                            <label
                                              class="setup-danger-checkbox setup-danger-checkbox--dialog"
                                            >
                                              <input
                                                v-model="dataLossConfirmed"
                                                type="checkbox"
                                              >

                                              <span>
                                                我确认永久删除当前表前缀下的全部
                                                Aquafish 数据
                                              </span>
                                            </label>

                                            <label
                                              class="setup-confirmation-field setup-confirmation-field--dialog"
                                            >
                                              <span>
                                                请输入数据库名
                                                <code>{{ effectiveDatabaseName }}</code>
                                                或输入
                                                <code>重新安装</code>
                                              </span>

                                              <div
                                                class="setup-confirmation-input"
                                                :class="{
                                                  'is-valid':
                                                    reinstallConfirmationReady,
                                                  'is-invalid':
                                                    Boolean(
                                                      reinstallConfirmation,
                                                    )
                                                    && !reinstallConfirmationReady,
                                                }"
                                              >
                                                <input
                                                  v-model.trim="
                                                    reinstallConfirmation
                                                  "
                                                  type="text"
                                                  autocomplete="off"
                                                  spellcheck="false"
                                                  placeholder="输入数据库名或重新安装"
                                                  :aria-invalid="
                                                    Boolean(
                                                      reinstallConfirmation,
                                                    )
                                                    && !reinstallConfirmationReady
                                                  "
                                                >

                                                <Transition
                                                  name="setup-field-status"
                                                >
                                                  <CircleCheckFilled
                                                    v-if="
                                                      reinstallConfirmationReady
                                                    "
                                                    class="setup-confirmation-input__status is-valid"
                                                    aria-hidden="true"
                                                  />

                                                  <CircleCloseFilled
                                                    v-else-if="
                                                      Boolean(
                                                        reinstallConfirmation,
                                                      )
                                                      || dataLossConfirmed
                                                    "
                                                    class="setup-confirmation-input__status is-invalid"
                                                    aria-hidden="true"
                                                  />
                                                </Transition>
                                              </div>

                                              <small
                                                class="setup-confirmation-field__message"
                                                :class="
                                                  reinstallConfirmationReady
                                                    ? 'is-valid'
                                                    : 'is-pending'
                                                "
                                              >
                                                {{
                                                  reinstallConfirmationReady
                                                    ? '确认信息已匹配，正式执行时后端仍会重新核验。'
                                                    : '勾选数据丢失确认后，输入上面的数据库名或“重新安装”。'
                                                }}
                                              </small>
                                            </label>
                                          </div>

                                          <div class="setup-confirm-dialog__actions">
                                            <button
                                              type="button"
                                              class="setup-secondary"
                                              @click="
                                                cancelReinstallPreparation
                                              "
                                            >
                                              取消
                                            </button>

                                            <button
                                              type="button"
                                              class="setup-primary setup-primary--danger"
                                              :disabled="
                                                !reinstallConfirmationReady
                                              "
                                              @click="
                                                confirmReinstallPreparation
                                              "
                                            >
                                              确认使用重装模式
                                            </button>
                                          </div>
                                        </section>
                                      </div>
                                    </Transition>
                                  </Teleport>

                                  <div
                                    v-if="
                                      reinstallRequested
                                      && !reinstallPreparationDialogOpen
                                    "
                                    class="setup-reinstall-confirmed-inline"
                                  >
                                    <CircleCheckFilled aria-hidden="true" />

                                    <span>
                                      危险重装模式已确认，正式执行前仍会再次弹窗确认。
                                    </span>

                                    <button
                                      type="button"
                                      @click="
                                        openReinstallPreparation(
                                          false,
                                        )
                                      "
                                    >
                                      修改确认
                                    </button>
                                  </div>
                                </div>
                              </section>
          </div>

          <div class="setup-confirm-dialog__actions">
            <button
              type="button"
              class="setup-secondary"
              @click="closeDatabaseStatusDialog"
            >
              完成
            </button>
          </div>
        </section>
      </div>
    </Transition>

<Transition name="setup-reinstall-dialog">
      <div
        v-if="reinstallDialogOpen"
        class="setup-dialog-backdrop"
        role="presentation"
        @click.self="closeReinstallDialog"
      >
        <section
          ref="reinstallDialogRef"
          class="setup-confirm-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="setup-reinstall-dialog-title"
          aria-describedby="setup-reinstall-dialog-description"
          tabindex="-1"
          @keydown.esc="closeReinstallDialog"
        >
          <div class="setup-confirm-dialog__icon">
            <Lock aria-hidden="true" />
          </div>

          <div class="setup-confirm-dialog__content">
            <p class="setup-confirm-dialog__eyebrow">FINAL CONFIRMATION</p>
            <h2 id="setup-reinstall-dialog-title">确认清空并重新安装？</h2>
            <p id="setup-reinstall-dialog-description">
              即将永久删除数据库
              <strong>{{ effectiveDatabaseName }}</strong>
              中表前缀
              <strong>{{ context?.databaseManaged ? context.database.tablePrefix : form.tablePrefix }}</strong>
              下的 Aquafish 数据。其他数据库和非白名单表不会处理。
            </p>

            <div class="setup-confirm-dialog__summary">
              <div><span>目标数据库</span><strong>{{ effectiveDatabaseName }}</strong></div>
              <div><span>数据库主机</span><strong>{{ context?.databaseManaged ? context.database.host : form.databaseHost }}</strong></div>
              <div><span>数据库端口</span><strong>{{ context?.databaseManaged ? context.database.port : form.databasePort }}</strong></div>
              <div><span>新管理员</span><strong>{{ form.adminUsername }}</strong></div>
            </div>

            <p class="setup-confirm-dialog__warning">
              点击“确认并开始”后才会进入安装进度，并调用服务器端精确清理接口。此操作无法撤销。
            </p>
          </div>

          <div class="setup-confirm-dialog__actions">
            <button
              type="button"
              class="setup-secondary"
              :disabled="submitting || installing"
              @click="closeReinstallDialog"
            >
              取消
            </button>

            <button
              type="button"
              class="setup-primary setup-primary--danger"
              :disabled="submitting || installing"
              @click="confirmReinstallDialog"
            >
              {{ submitting || installing ? '正在启动重装...' : '确认并开始重装' }}
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </main>
</template>

<script setup lang="ts">
/**
 * Aquafish 首次安装主流程。
 *
 * 功能与关联：
 * 1. 欢迎页和十秒协议门槛负责安装入口体验，拒绝协议会返回欢迎页；
 * 2. /api/setup/context 提供真实环境探测与可信部署模式，不接受 URL 覆盖；
 * 3. 分发安装使用真实数据库/Redis 连接测试；1Panel/Docker 跳过服务配置页，
 *    直接使用服务端环境变量执行安装，平台密码不会进入浏览器；
 * 4. 站点与超级管理员在同一步校验，减少重复页面；
 * 5. 安装阶段按真实 API 边界展示配置写入、迁移预检、建表迁移和最终提交进度；
 * 6. 只有后端权威状态确认已安装后，才展示完成页并保存用户选择的后续入口；
 * 7. 已安装实例不会读取安装上下文，而是立即退出向导并进入专用提示页。
 */
import {
  ArrowRight,
  CircleCheckFilled,
  CircleCloseFilled,
  Clock,
  Connection,
  Loading,
  Lock,
  Monitor,
  RefreshRight,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  setupApi,
  setupPost,
  type ConnectionTestResult,
  type DatabaseMigrationPreview,
  type DatabaseMigrationResult,
  type InstallStatus,
  type SetupDatabaseInspection,
  type SetupDatabaseResetResult,
  type SetupDeploymentContext,
  type SetupExistingInstallationRecoveryResult,
} from '../../api/setup'
import {
  installedSetupNoticeLocation,
  rememberSetupDestination,
} from '../../router/setup-completion-destination'
import {
  isDevelopmentReinstallMaintenanceLocation,
} from '../../router/setup-maintenance-mode'
import {
  buildSetupSteps,
  canAcceptAgreement,
  databaseAllowsNewInstall,
  databaseCanContinue,
  databaseInspectionAllowsConnectionPass,
  databaseReinstallConfirmationReady,
  databaseRequiresRecovery,
  defaultDatabasePort,
  defaultSiteUrl,
  formatElapsedSeconds,
  type SetupStepKey,
} from './setup-wizard'
import './setup-page.css'
import './setup-page-v2.css'
import './setup-page-interactions.css'

type EntryScreen = 'welcome' | 'agreement' | 'wizard'
type DatabaseType = 'mysql' | 'mariadb' | 'postgresql'
type ServiceStage = 'database-type' | 'database-connection'
type ProgressState = 'active' | 'done' | 'error'
type FieldValidationState = 'idle' | 'valid' | 'invalid'
type PasswordStrengthLevel = 'empty' | 'weak' | 'medium' | 'strong'
type IdentityFieldKey =
  | 'siteName'
  | 'siteUrl'
  | 'adminUsername'
  | 'adminDisplayName'
  | 'adminEmail'
  | 'adminPassword'
  | 'adminPasswordConfirm'

interface SetupForm {
  siteName: string
  siteUrl: string
  locale: string
  timezone: string
  databaseType: DatabaseType
  databaseHost: string
  databasePort: number
  databaseName: string
  databaseUsername: string
  databasePassword: string
  tablePrefix: string
  redisEnabled: boolean
  redisHost: string
  redisPort: number
  redisDatabase: number
  redisUsername: string
  redisPassword: string
  redisSsl: boolean
  adminUsername: string
  adminEmail: string
  adminPassword: string
  adminPasswordConfirm: string
  adminDisplayName: string
}

interface InstallProgressItem {
  message: string
  state: ProgressState
}

interface DatabaseTypeOption {
  value: DatabaseType
  label: string
  port: number
  description: string
  recommendation: string
}

const databaseTypes: DatabaseTypeOption[] = [
  {
    value: 'mysql',
    label: 'MySQL',
    port: 3306,
    description: '生态成熟，默认首选',
    recommendation: '默认推荐',
  },
  {
    value: 'mariadb',
    label: 'MariaDB',
    port: 3306,
    description: '适合常见面板环境',
    recommendation: '面板兼容',
  },
  {
    value: 'postgresql',
    label: 'PostgreSQL',
    port: 5432,
    description: '适合复杂查询与扩展',
    recommendation: '复杂业务',
  },
]

const serviceStages: Array<{
  key: ServiceStage
  title: string
  description: string
}> = [
  {
    key: 'database-type',
    title: '选择服务',
    description: '选择数据库与可选 Redis',
  },
  {
    key: 'database-connection',
    title: '填写连接信息',
    description: '左右双栏完成真实测试',
  },
]

const status = ref<InstallStatus | null>(null)
const context = ref<SetupDeploymentContext | null>(null)
const loading = ref(true)
const submitting = ref(false)
const installing = ref(false)
const environmentRefreshing = ref(false)
const databaseTesting = ref(false)
const redisTesting = ref(false)
const errorMessage = ref('')
const entryScreen = ref<EntryScreen>('welcome')
const agreementSeconds = ref(10)
const agreementReadToEnd = ref(false)
const agreementContent = ref<HTMLElement | null>(null)
const reinstallDialogRef = ref<HTMLElement | null>(null)
const reinstallPreparationDialogRef = ref<HTMLElement | null>(null)
const databaseStatusDialogRef = ref<HTMLElement | null>(null)
const serviceFlowRef = ref<HTMLElement | null>(null)
const databasePassed = ref(false)
const redisPassed = ref(false)
const databaseResult = ref<ConnectionTestResult | null>(null)
const databaseInspection = ref<SetupDatabaseInspection | null>(null)
const reinstallRequested = ref(false)
const reinstallDialogOpen = ref(false)
const reinstallPreparationDialogOpen = ref(false)
const databaseStatusDialogOpen = ref(false)

/** 密码明文与遮罩状态只由用户点击控制。 */
const databasePasswordVisible = ref(false)
const redisPasswordVisible = ref(false)
const adminPasswordVisible = ref(false)
const adminPasswordConfirmVisible = ref(false)

const dataLossConfirmed = ref(false)
const reinstallConfirmation = ref('')
const redisResult = ref<ConnectionTestResult | null>(null)
const installationCompleted = ref(false)
const currentStepIndex = ref(0)
const serviceStage = ref<ServiceStage>('database-type')
const serviceStageVisited = ref(0)
const selectedDatabaseType = ref<DatabaseType | null>(null)
const maxVisitedStep = ref(0)
const installProgress = ref(0)
const currentProgressMessage = ref('等待开始安装')
const progressItems = ref<InstallProgressItem[]>([])

/**
 * 自适应安装进度只负责显示层。
 * 后端真实接口、数据库状态和完成条件保持原样。
 */
const installProgressTimingStorageKey =
  'aquafish.setup.install-progress-timings.v1'

const installProgressStageCeilings: Record<number, number> = {
  0: 5.5,
  6: 11.5,
  12: 17.5,
  18: 33.5,
  34: 47.5,
  48: 75.5,
  76: 85.5,
  86: 99.2,
  100: 100,
}

/**
 * 首次运行的保守估算。
 * 后续运行会使用当前设备记录的真实耗时进行覆盖。
 */
const installProgressStageBaselines: Record<number, number> = {
  0: 420,
  6: 900,
  12: 2200,
  18: 850,
  34: 750,
  48: 4200,
  76: 450,
  86: 1500,
  100: 260,
}

let installProgressFrame: number | null = null
let installProgressStage = 0
let installProgressStageStartedAt = 0
let installProgressStageStartValue = 0
let installProgressStageCeiling = 0
let installProgressExpectedDuration = 900
let installProgressLastFrameAt = 0
let installProgressPerformanceFactor = 1

let agreementTimer: number | null = null
const route = useRoute()
const router = useRouter()
const developmentReinstallMaintenance = computed(() =>
  isDevelopmentReinstallMaintenanceLocation(
    route.path,
    route.query.maintenance,
  ),
)

/**
 * 站点地址默认使用用户当前打开安装器的来源，其余身份信息保持空白。
 * 这样分发包、反向代理和带端口部署都能得到真实公开入口，而不是示例占位符。
 */
const form = reactive<SetupForm>({
  siteName: '',
  siteUrl: defaultSiteUrl(typeof window === 'undefined' ? '' : window.location.origin),
  locale: 'zh-CN',
  timezone: 'Asia/Shanghai',
  databaseType: 'mysql',
  databaseHost: '127.0.0.1',
  databasePort: 3306,
  databaseName: '',
  databaseUsername: '',
  databasePassword: '',
  tablePrefix: 'aq_',
  redisEnabled: false,
  redisHost: '127.0.0.1',
  redisPort: 6379,
  redisDatabase: 0,
  redisUsername: '',
  redisPassword: '',
  redisSsl: false,
  adminUsername: '',
  adminEmail: '',
  adminPassword: '',
  adminPasswordConfirm: '',
  adminDisplayName: '',
})

/**
 * 记录第 3 步字段是否已经被用户操作过。
 *
 * 未触碰且为空时保持中性，不会刚进入页面就显示整片红色；
 * 用户输入非法值或离开必填空字段后，才显示错误状态。
 */
const identityTouched = reactive<Record<IdentityFieldKey, boolean>>({
  siteName: false,
  siteUrl: false,
  adminUsername: false,
  adminDisplayName: false,
  adminEmail: false,
  adminPassword: false,
  adminPasswordConfirm: false,
})

/*
 * 向导派生状态：步骤由可信部署上下文生成；“可以继续”只由真实环境、真实连接测试和
 * 当前表单状态共同决定，模板不自行猜测。
 */
const steps = computed(() => context.value ? buildSetupSteps(context.value) : [])
const currentStep = computed(() => steps.value[currentStepIndex.value])
const serviceStageIndex = computed(() =>
  serviceStages.findIndex(
    stage => stage.key === serviceStage.value,
  ),
)
const selectedDatabaseLabel = computed(() =>
  databaseTypes.find(
    database =>
      database.value === selectedDatabaseType.value,
  )?.label
  || '尚未选择',
)
const agreementReady = computed(() => canAcceptAgreement(agreementSeconds.value, agreementReadToEnd.value))
const agreementReadyReason = computed(() => agreementReadToEnd.value ? '已阅读到协议末尾，可以继续。' : '倒计时已结束，可以继续。')
const effectiveEnvironmentReady = computed(() =>
  Boolean(
    context.value?.environmentReady
      && (
        status.value?.canInstall
        || developmentReinstallMaintenance.value
      ),
  ),
)
const redisTestAvailable = computed(() => context.value?.redisManaged ? context.value.redisConfigured : form.redisEnabled)
const redisReady = computed(() => {
  if (!context.value) return false
  if (context.value.redisManaged) return !context.value.redisConfigured || redisPassed.value
  return !form.redisEnabled || redisPassed.value
})
const databaseReadyForNewInstall = computed(() =>
  databaseAllowsNewInstall(
    databaseInspection.value,
  ),
)

const databaseNeedsRecovery = computed(() =>
  databaseRequiresRecovery(
    databaseInspection.value,
  ),
)

const effectiveDatabaseName = computed(() =>
  context.value?.databaseManaged
    ? (context.value.database.name || '')
    : form.databaseName,
)

const reinstallConfirmationReady = computed(() =>
  databaseReinstallConfirmationReady(
    dataLossConfirmed.value,
    reinstallConfirmation.value,
    effectiveDatabaseName.value,
  ),
)

const databaseRecoverySelected = computed(() =>
  databaseNeedsRecovery.value
    && !reinstallRequested.value,
)

const databaseChoiceReady = computed(() =>
  databaseCanContinue(
    databaseInspection.value,
    reinstallRequested.value,
    reinstallConfirmationReady.value,
  ),
)

/**
 * 数据库状态按钮只有在本次真实测试同时取得：
 *
 * 1. databaseResult；
 * 2. databaseInspection；
 * 3. 测试请求已经结束；
 *
 * 后才能启用。
 *
 * 页面初始化、输入变化和请求失败时均保持禁用。
 */
const databaseStatusDialogReady = computed(() =>
  Boolean(
    databaseResult.value
    && databaseInspection.value
    && !databaseTesting.value,
  ),
)

/**
 * 底部数据库状态按钮按本次真实检测结果动态显示。
 */
const databaseStatusActionLabel = computed(() => {
  if (databaseTesting.value) {
    return '正在检测数据库'
  }

  if (
    !databaseStatusDialogReady.value
    || !databaseInspection.value
  ) {
    return '请先测试数据库'
  }

  switch (
    databaseInspection.value.mode
  ) {
    case 'NEW_INSTALL':
      return '可进行全新安装'

    case 'EXISTING_INSTALLED':
      return '检测到已有 Aquafish'

    case 'INCOMPLETE_INSTALLATION':
      return '检测到不完整安装'

    case 'INCOMPATIBLE_DATABASE':
      return '数据库不兼容'

    case 'STATE_UNAVAILABLE':
      return '状态检测失败'

    default:
      return '查看数据库状态'
  }
})

/**
 * 不同数据库识别结果使用不同的低饱和状态色。
 */
const databaseStatusActionClass = computed(() => {
  if (databaseTesting.value) {
    return 'is-testing'
  }

  if (
    !databaseStatusDialogReady.value
    || !databaseInspection.value
  ) {
    return 'is-disabled'
  }

  switch (
    databaseInspection.value.mode
  ) {
    case 'NEW_INSTALL':
      return 'is-new-install'

    case 'EXISTING_INSTALLED':
      return 'is-existing'

    case 'INCOMPLETE_INSTALLATION':
      return 'is-incomplete'

    case 'INCOMPATIBLE_DATABASE':
      return 'is-incompatible'

    case 'STATE_UNAVAILABLE':
      return 'is-error'

    default:
      return 'is-ready'
  }
})

const servicesReady = computed(() =>
  databasePassed.value
    && databaseChoiceReady.value
    && redisReady.value,
)
const databaseLabel = computed(() => databaseTypes.find(item => item.value === form.databaseType)?.label || form.databaseType)
const redisSummary = computed(() => {
  if (!context.value) return '未启用'
  if (context.value.redisManaged) return context.value.redisConfigured ? '平台托管' : '未启用'
  return form.redisEnabled ? `${form.redisHost}:${form.redisPort}` : '未启用'
})
const redisStatusLabel = computed(() => {
  if (!redisTestAvailable.value) return '未启用'
  return redisPassed.value ? '已通过' : '等待测试'
})
const redisStatusClass = computed(() => {
  if (!redisTestAvailable.value) return 'is-muted'
  return redisPassed.value ? 'is-pass' : 'is-pending'
})

const adminUsernamePattern = /^[\p{L}\p{N}_-]{1,64}$/u
const optionalEmailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const siteNameState = computed<FieldValidationState>(() =>
  validationState(
    'siteName',
    Boolean(form.siteName.trim()),
    Boolean(form.siteName.trim())
      && form.siteName.trim().length <= 100,
  ),
)

const siteUrlState = computed<FieldValidationState>(() =>
  validationState(
    'siteUrl',
    Boolean(form.siteUrl.trim()),
    validHttpUrl(form.siteUrl),
  ),
)

const adminUsernameState = computed<FieldValidationState>(() =>
  validationState(
    'adminUsername',
    Boolean(form.adminUsername),
    adminUsernamePattern.test(form.adminUsername),
  ),
)

const adminDisplayNameState = computed<FieldValidationState>(() =>
  validationState(
    'adminDisplayName',
    Boolean(form.adminDisplayName.trim()),
    Boolean(form.adminDisplayName.trim())
      && form.adminDisplayName.trim().length <= 100,
  ),
)

const adminEmailState = computed<FieldValidationState>(() => {
  const value = form.adminEmail.trim()

  if (!value) return 'idle'

  return optionalEmailPattern.test(value)
    && value.length <= 191
    ? 'valid'
    : 'invalid'
})

const adminPasswordState = computed<FieldValidationState>(() =>
  validationState(
    'adminPassword',
    Boolean(form.adminPassword),
    form.adminPassword.length >= 8
      && form.adminPassword.length <= 128,
  ),
)

const adminPasswordConfirmState = computed<FieldValidationState>(() =>
  validationState(
    'adminPasswordConfirm',
    Boolean(form.adminPasswordConfirm),
    adminPasswordState.value === 'valid'
      && form.adminPasswordConfirm === form.adminPassword,
  ),
)

const identityReady = computed(() =>
  siteNameState.value === 'valid'
    && siteUrlState.value === 'valid'
    && adminUsernameState.value === 'valid'
    && adminDisplayNameState.value === 'valid'
    && adminEmailState.value !== 'invalid'
    && adminPasswordState.value === 'valid'
    && adminPasswordConfirmState.value === 'valid',
)

const passwordStrength = computed<{
  level: PasswordStrengthLevel
  label: string
  score: number
  bars: number
}>(() => {
  const value = form.adminPassword

  if (!value) {
    return {
      level: 'empty',
      label: '等待输入',
      score: 0,
      bars: 0,
    }
  }

  let score = 0

  if (value.length >= 8 && value.length <= 128) score += 1
  if (value.length >= 12) score += 1
  if (/[\p{L}]/u.test(value)) score += 1
  if (/\p{N}/u.test(value)) score += 1
  if (/[^\p{L}\p{N}\s]/u.test(value)) score += 1

  if (score <= 2) {
    return {
      level: 'weak',
      label: '较弱',
      score,
      bars: 1,
    }
  }

  if (score <= 4) {
    return {
      level: 'medium',
      label: '中等',
      score,
      bars: 2,
    }
  }

  return {
    level: 'strong',
    label: '强',
    score,
    bars: 3,
  }
})

const passwordRules = computed(() => [
  {
    key: 'length',
    label: '长度为 8–128 位',
    passed:
      form.adminPassword.length >= 8
      && form.adminPassword.length <= 128,
  },
  {
    key: 'letter',
    label: '包含字母或中文字符',
    passed: /[\p{L}]/u.test(form.adminPassword),
  },
  {
    key: 'number',
    label: '包含数字',
    passed: /\p{N}/u.test(form.adminPassword),
  },
  {
    key: 'symbol',
    label: '包含符号，强度更高',
    passed: /[^\p{L}\p{N}\s]/u.test(form.adminPassword),
  },
])

const siteNameMessage = computed(() => {
  if (siteNameState.value === 'valid') return '站点名称已填写。'
  if (siteNameState.value === 'invalid') return '站点名称不能为空，且不能超过 100 个字符。'
  return '用于前台标题，安装完成后仍可修改。'
})

const siteUrlMessage = computed(() => {
  if (siteUrlState.value === 'valid') return '站点地址格式正确。'
  if (siteUrlState.value === 'invalid') return '请输入完整的 http:// 或 https:// 地址。'
  return '填写用户最终访问站点的公开地址。'
})

const adminUsernameMessage = computed(() => {
  if (adminUsernameState.value === 'valid') return '用户名可以使用。'
  if (adminUsernameState.value === 'invalid') {
    return '必须为 1–64 位中文、字母、数字、下划线或短横线。'
  }

  return '支持中文，最少 1 个字符。'
})

const adminDisplayNameMessage = computed(() => {
  if (adminDisplayNameState.value === 'valid') return '显示名称已填写。'
  if (adminDisplayNameState.value === 'invalid') {
    return '显示名称不能为空，且不能超过 100 个字符。'
  }

  return '用于后台导航、审计记录和公开展示。'
})

const adminEmailMessage = computed(() => {
  if (adminEmailState.value === 'valid') return '邮箱格式正确。'
  if (adminEmailState.value === 'invalid') return '邮箱格式不正确。'
  return '可不填写；填写后可用于账号通知和找回。'
})

const adminPasswordMessage = computed(() => {
  if (adminPasswordState.value === 'valid') {
    return '长度符合要求，当前强度：' + passwordStrength.value.label + '。'
  }

  if (adminPasswordState.value === 'invalid') {
    return '管理员密码长度必须为 8–128 位。'
  }

  return '输入后会实时计算密码强度。'
})

const adminPasswordConfirmMessage = computed(() => {
  if (adminPasswordConfirmState.value === 'valid') {
    return '两次输入的密码一致。'
  }

  if (adminPasswordConfirmState.value === 'invalid') {
    return '两次输入的密码不一致。'
  }

  return '请再次输入管理员密码。'
})

const previousLabel = computed(() => {
  if (
    currentStep.value?.key === 'services'
    && serviceStageIndex.value > 0
  ) {
    return '返回上一步'
  }

  return currentStepIndex.value === 0
    ? '返回协议'
    : '上一步'
})

const installHasError = computed(() =>
  progressItems.value.some(item => item.state === 'error'),
)
const actionLabel = computed(() => {
  if (installing.value) {
    return reinstallRequested.value
      ? '正在重装系统...'
      : '正在安装系统...'
  }

  if (submitting.value) {
    if (
      currentStep.value?.key === 'install'
      && reinstallRequested.value
    ) {
      return reinstallDialogOpen.value
        ? '等待最终确认...'
        : '正在准备重装...'
    }

    return '正在处理...'
  }

  if (
    currentStep.value?.key === 'environment'
  ) {
    return '环境通过，下一步'
  }

  if (
    currentStep.value?.key === 'services'
  ) {
    if (serviceStage.value === 'database-type') {
      if (!selectedDatabaseType.value) {
        return '请选择数据库'
      }

      return form.redisEnabled
        ? '数据库与 Redis 已选择，下一步'
        : '使用基础模式，下一步'
    }

    if (databaseRecoverySelected.value) {
      return servicesReady.value
        ? '恢复配置并进入系统'
        : '请完成连接测试'
    }

    if (reinstallRequested.value) {
      return servicesReady.value
        ? '确认重装，下一步'
        : '请完成连接测试'
    }

    if (!databasePassed.value) {
      return '请先测试数据库连接'
    }

    if (
      form.redisEnabled
      && !redisPassed.value
    ) {
      return '请先测试 Redis 连接'
    }

    return '连接已验证，下一步'
  }

  if (
    currentStep.value?.key === 'install'
  ) {
    if (installHasError.value) {
      return reinstallRequested.value
        ? '重新尝试重装'
        : '重新尝试安装'
    }

    return reinstallRequested.value
      ? '开始重装系统'
      : '开始安装系统'
  }

  return '下一步'
})

const actionDisabled = computed(() => {
  if (
    submitting.value
    || installing.value
    || databaseTesting.value
    || redisTesting.value
  ) {
    return true
  }

  if (
    currentStep.value?.key === 'environment'
  ) {
    return !effectiveEnvironmentReady.value
  }

  if (
    currentStep.value?.key === 'services'
  ) {
    if (serviceStage.value === 'database-type') {
      return selectedDatabaseType.value === null
    }

    return !servicesReady.value
  }

  if (
    currentStep.value?.key === 'identity'
  ) {
    return !identityReady.value
  }

  return false
})

/**
 * 将已触碰和实时输入结果转换成 idle / valid / invalid。
 */

function validationState(
  field: IdentityFieldKey,
  hasValue: boolean,
  valid: boolean,
): FieldValidationState {
  if (!hasValue && !identityTouched[field]) return 'idle'
  return valid ? 'valid' : 'invalid'
}

/** 标记字段已经离开，必填空值从此显示错误状态。 */
function markIdentityTouched(field: IdentityFieldKey): void {
  identityTouched[field] = true
}

/** 返回模板需要的字段状态类名。 */
function fieldStateClass(state: FieldValidationState): string {
  return state === 'idle'
    ? 'is-idle'
    : 'is-' + state
}

/** 校验完整 HTTP/HTTPS 站点地址。 */
function validHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:'
      || url.protocol === 'https:'
  } catch {
    return false
  }
}

/** 组装分发安装的数据库请求；托管部署不会把该对象提交给后端。 */
function databaseSettings() {
  return {
    type: form.databaseType,
    host: form.databaseHost,
    port: form.databasePort,
    name: form.databaseName,
    username: form.databaseUsername,
    password: form.databasePassword,
    tablePrefix: form.tablePrefix,
  }
}

/** 组装可选 Redis 请求；密码只存在于当前内存表单和请求体。 */
function redisSettings() {
  return {
    enabled: form.redisEnabled,
    host: form.redisHost,
    port: form.redisPort,
    database: form.redisDatabase,
    username: form.redisUsername,
    password: form.redisPassword,
    ssl: form.redisSsl,
  }
}

/** 组装站点公开配置，供运行配置写入和最终安装提交复用。 */
function siteSettings() {
  return {
    name: form.siteName,
    url: form.siteUrl,
    locale: form.locale,
    timezone: form.timezone,
  }
}

/** 保存完成页选择；该偏好只决定以后离开 /setup 的去向，不改变安装安全状态。 */
function rememberDestination(destination: string): void {
  rememberSetupDestination(destination)
}

/** 从欢迎页进入协议或正式向导；免协议部署仍会先刷新真实环境。 */
function startInstall(): void {
  errorMessage.value = ''
  if (!context.value?.licenseRequired) {
    entryScreen.value = 'wizard'
    void refreshEnvironment()
    return
  }
  entryScreen.value = 'agreement'
  startAgreementTimer()
}

/** 启动十秒协议门槛并在内容无需滚动时自动判定为已阅读到底。 */
function startAgreementTimer(): void {
  clearAgreementTimer()
  agreementSeconds.value = 10
  agreementReadToEnd.value = false
  agreementTimer = window.setInterval(() => {
    agreementSeconds.value = Math.max(0, agreementSeconds.value - 1)
    if (agreementSeconds.value === 0) clearAgreementTimer()
  }, 1000)
  void nextTick(checkAgreementOverflow)
}

/** 清理协议定时器，防止返回欢迎页或组件卸载后继续修改状态。 */
function clearAgreementTimer(): void {
  if (agreementTimer !== null) {
    window.clearInterval(agreementTimer)
    agreementTimer = null
  }
}

/** 协议内容没有滚动条时直接满足“阅读到底”，避免用户无法触发 scroll。 */
function checkAgreementOverflow(): void {
  const element = agreementContent.value
  if (element && element.scrollHeight <= element.clientHeight + 4) agreementReadToEnd.value = true
}

/** 记录用户已滚动到协议末尾；一旦满足，本次阅读不再回退。 */
function handleAgreementScroll(event: Event): void {
  const element = event.currentTarget as HTMLElement
  if (element.scrollTop + element.clientHeight >= element.scrollHeight - 8) agreementReadToEnd.value = true
}

/** 拒绝协议后清空计时状态并返回欢迎页，不能继续任何安装步骤。 */
function rejectAgreement(): void {
  clearAgreementTimer()
  agreementReadToEnd.value = false
  entryScreen.value = 'welcome'
  errorMessage.value = ''
}

/** 协议门槛满足后进入向导并立即重新执行真实环境检测。 */
async function acceptAgreement(): Promise<void> {
  if (!agreementReady.value) return
  clearAgreementTimer()
  entryScreen.value = 'wizard'
  currentStepIndex.value = 0
  maxVisitedStep.value = Math.max(maxVisitedStep.value, 0)
  await refreshEnvironment()
}

/** 切换数据库卡片时同步设置标准端口，并让旧连接测试立即失效。 */
function selectDatabaseType(type: DatabaseType): void {
  const changed =
    selectedDatabaseType.value !== type
    || form.databaseType !== type

  selectedDatabaseType.value = type
  form.databaseType = type
  form.databasePort = defaultDatabasePort(type)

  if (changed) {
    invalidateDatabase()
  }
}

/**
 * 只允许返回已经访问过的服务子步骤。
 * Docker 和 1Panel 不会创建 services 正式步骤，因此不会进入这里。
 */
/**
 * 子页面切换后，把服务区域重新定位到滚动容器顶部。
 * 使用即时定位，不使用平滑滚动，避免页面来回晃动。
 */
function resetServiceStageViewport(): void {
  void nextTick(() => {
    const element = serviceFlowRef.value
    if (!element) return

    let parent = element.parentElement

    while (parent) {
      const style =
        window.getComputedStyle(parent)

      const scrollable =
        /auto|scroll/.test(style.overflowY)
        && parent.scrollHeight
          > parent.clientHeight

      if (scrollable) {
        const parentRect =
          parent.getBoundingClientRect()

        const elementRect =
          element.getBoundingClientRect()

        const target =
          parent.scrollTop
          + elementRect.top
          - parentRect.top
          - 6

        parent.scrollTop = Math.max(
          0,
          target,
        )

        return
      }

      parent = parent.parentElement
    }
  })
}

/** 只允许返回已经访问过的服务子步骤。 */
function goToServiceStage(
  stage: ServiceStage,
): void {
  const targetIndex =
    serviceStages.findIndex(
      item => item.key === stage,
    )

  if (
    targetIndex < 0
    || targetIndex
      > serviceStageVisited.value
  ) {
    return
  }

  serviceStage.value = stage
  errorMessage.value = ''
  resetServiceStageViewport()
}

/** 推进子步骤并记录允许返回的最远位置。 */
function advanceServiceStage(
  stage: ServiceStage,
): void {
  const targetIndex =
    serviceStages.findIndex(
      item => item.key === stage,
    )

  if (targetIndex < 0) return

  serviceStage.value = stage

  serviceStageVisited.value = Math.max(
    serviceStageVisited.value,
    targetIndex,
  )

  errorMessage.value = ''
  resetServiceStageViewport()
}

/**
 * Redis 卡片是可选开关。
 * 关闭或重新启用都会清除旧测试。
 */
function chooseRedis(
  enabled: boolean,
): void {
  if (
    form.redisEnabled === enabled
  ) {
    return
  }

  form.redisEnabled = enabled
  invalidateRedis()
}

/** 清空用户对数据库恢复或重新安装的选择。 */
function resetDatabaseChoice(): void {
  reinstallRequested.value = false
  reinstallDialogOpen.value = false
  reinstallPreparationDialogOpen.value = false
  dataLossConfirmed.value = false
  reinstallConfirmation.value = ''
}

/**
 * 只有本次真实测试已经取得数据库识别结果，
 * 才允许打开状态弹窗。
 */
async function openDatabaseStatusDialog(): Promise<void> {
  if (
    installing.value
    || submitting.value
    || databaseTesting.value
    || redisTesting.value
    || !databaseStatusDialogReady.value
    || !databaseInspection.value
  ) {
    return
  }

  databaseStatusDialogOpen.value = true

  await nextTick()

  databaseStatusDialogRef.value?.focus()
}

/** 关闭数据库状态弹窗，不清除本次测试结果。 */
function closeDatabaseStatusDialog(): void {
  if (
    installing.value
    || submitting.value
  ) {
    return
  }

  databaseStatusDialogOpen.value = false
}

/**
 * 打开第一层危险重装确认。
 * 这里只记录重装模式，不调用任何数据库写接口。
 */
async function openReinstallPreparation(
  resetConfirmation: boolean,
): Promise<void> {
  if (submitting.value || installing.value) return

  if (resetConfirmation) {
    dataLossConfirmed.value = false
    reinstallConfirmation.value = ''
  }

  errorMessage.value = ''
  reinstallPreparationDialogOpen.value = true
  await nextTick()
  reinstallPreparationDialogRef.value?.focus()
}

/** 勾选或取消危险重装选项。 */
async function handleReinstallChoice(): Promise<void> {
  if (!reinstallRequested.value) {
    reinstallPreparationDialogOpen.value = false
    dataLossConfirmed.value = false
    reinstallConfirmation.value = ''
    return
  }

  await openReinstallPreparation(true)
}

/** 取消第一层确认，同时退回安全默认模式。 */
function cancelReinstallPreparation(): void {
  if (submitting.value || installing.value) return
  reinstallRequested.value = false
  reinstallPreparationDialogOpen.value = false
  dataLossConfirmed.value = false
  reinstallConfirmation.value = ''
}

/** 第一层确认通过后，仅关闭弹窗并保留重装选择。 */
function confirmReinstallPreparation(): void {
  if (
    submitting.value
    || installing.value
    || !reinstallConfirmationReady.value
  ) {
    return
  }

  reinstallPreparationDialogOpen.value = false
}

/** 清除数据库测试结果；任何连接参数变化都必须再次真实测试。 */
function invalidateDatabase(): void {
  databasePassed.value = false
  databaseResult.value = null
  databaseInspection.value = null
  databaseStatusDialogOpen.value = false
  resetDatabaseChoice()
}

/** 清除 Redis 测试结果；启用状态或连接参数变化后必须重新测试。 */
function invalidateRedis(): void {
  redisPassed.value = false
  redisResult.value = null
}

/** 只允许返回已经访问过的非完成步骤，防止点击步骤条跳过必填校验。 */
function goToStep(index: number): void {
  if (index <= maxVisitedStep.value && steps.value[index]?.key !== 'complete') {
    reinstallDialogOpen.value = false
    reinstallPreparationDialogOpen.value = false
    databaseStatusDialogOpen.value = false
    currentStepIndex.value = index
    errorMessage.value = ''
  }
}

/** 返回上一步；第一步根据部署要求返回协议页或欢迎页。 */
function previousStep(): void {
  reinstallDialogOpen.value = false
  reinstallPreparationDialogOpen.value = false
  databaseStatusDialogOpen.value = false
  errorMessage.value = ''

  if (
    currentStep.value?.key === 'services'
    && serviceStageIndex.value > 0
  ) {
    const previous =
      serviceStages[
        serviceStageIndex.value - 1
      ]

    if (previous) {
      serviceStage.value = previous.key
      resetServiceStageViewport()
    }

    return
  }

  if (currentStepIndex.value > 0) {
    currentStepIndex.value -= 1
    return
  }

  if (context.value?.licenseRequired) {
    entryScreen.value = 'agreement'
    startAgreementTimer()
  } else {
    entryScreen.value = 'welcome'
  }
}

/** 完成当前步骤后推进索引并记录可返回的最远位置。 */
function advance(): void {
  currentStepIndex.value += 1
  maxVisitedStep.value = Math.max(maxVisitedStep.value, currentStepIndex.value)
  errorMessage.value = ''
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/** 在发请求前校验分发安装数据库必填项和安全表前缀格式。 */
function validateDatabase(): void {
  if (!form.databaseHost || !form.databaseName || !form.databaseUsername) {
    throw new Error('请完整填写数据库主机、端口、数据库名和用户。')
  }
  if (!/^[a-z][a-z0-9_]{0,38}_$/.test(form.tablePrefix)) {
    throw new Error('表前缀必须以小写字母开头、以下划线结尾，且只能包含小写字母、数字和下划线。')
  }
}

/** 校验站点 URL 与超级管理员身份/密码，两组信息在同一步完成。 */
function validateSiteAndAdmin(): void {
  if (!form.siteName) throw new Error('站点名称不能为空。')
  try {
    const url = new URL(form.siteUrl)
    if (!['http:', 'https:'].includes(url.protocol)) throw new Error()
  } catch {
    throw new Error('站点地址必须是完整的 http:// 或 https:// 地址。')
  }
  if (!/^[\p{L}\p{N}_-]{1,64}$/u.test(form.adminUsername)) {
    throw new Error('管理员用户名必须为 1-64 位中文、字母、数字、下划线或短横线。')
  }
  if (!form.adminDisplayName) throw new Error('管理员显示名称不能为空。')
  if (form.adminPassword.length < 8 || form.adminPassword.length > 128) {
    throw new Error('管理员密码长度必须为 8-128 位。')
  }
  if (form.adminPassword !== form.adminPasswordConfirm) throw new Error('两次输入的管理员密码不一致。')
}

/**
 * 按托管/分发模式选择真实数据库测试接口；只有后端明确成功才设置 databasePassed。
 */
async function testDatabase(): Promise<ConnectionTestResult> {
  if (!context.value) throw new Error('安装上下文尚未加载。')
  if (!context.value.databaseManaged) validateDatabase()
  databaseStatusDialogOpen.value = false
  databaseTesting.value = true
  databasePassed.value = false
  databaseResult.value = null
  databaseInspection.value = null
  try {
    const endpoint = context.value.databaseManaged
      ? '/api/setup/database/managed/test'
      : '/api/setup/database/test'
    const result = await setupPost<ConnectionTestResult>(
      endpoint,
      context.value.databaseManaged ? undefined : databaseSettings(),
    )
    databaseResult.value = result

    const inspectEndpoint =
      context.value.databaseManaged
        ? '/api/setup/database/managed/inspect'
        : '/api/setup/database/inspect'

    databaseInspection.value =
      await setupPost<SetupDatabaseInspection>(
        inspectEndpoint,
        context.value.databaseManaged
          ? undefined
          : databaseSettings(),
      )

    if (
      ![
        'EXISTING_INSTALLED',
        'INCOMPLETE_INSTALLATION',
      ].includes(databaseInspection.value.mode)
    ) {
      resetDatabaseChoice()
    }

    if (
      !databaseInspectionAllowsConnectionPass(
        databaseInspection.value,
      )
    ) {
      throw new Error(
        databaseInspection.value.note
        || '数据库连接成功，但安装状态无法识别，请检查后端日志后重试。',
      )
    }

    databasePassed.value = true
    return result
  } finally {
    databaseTesting.value = false
  }
}

/** 数据库“测试连接”按钮入口，把异常转换为页面错误但不继续步骤。 */
async function runDatabaseTest(): Promise<void> {
  errorMessage.value = ''
  try {
    await testDatabase()
  } catch (error) {
    databasePassed.value = false
    errorMessage.value = messageOf(error, '数据库连接测试失败。')
  }
}

/** 测试已启用 Redis；未启用时返回 null，托管模式不向浏览器暴露连接凭据。 */
async function testRedis(): Promise<ConnectionTestResult | null> {
  if (!context.value) throw new Error('安装上下文尚未加载。')
  if (!redisTestAvailable.value) return null
  redisTesting.value = true
  redisPassed.value = false
  redisResult.value = null
  try {
    const endpoint = context.value.redisManaged
      ? '/api/setup/redis/managed/test'
      : '/api/setup/redis/test'
    const result = await setupPost<ConnectionTestResult>(
      endpoint,
      context.value.redisManaged ? undefined : redisSettings(),
    )
    redisResult.value = result
    redisPassed.value = true
    return result
  } finally {
    redisTesting.value = false
  }
}

/** Redis“测试连接”按钮入口，把异常转换为页面错误并保持未通过状态。 */
async function runRedisTest(): Promise<void> {
  errorMessage.value = ''
  try {
    await testRedis()
  } catch (error) {
    redisPassed.value = false
    errorMessage.value = messageOf(error, 'Redis 连接测试失败。')
  }
}

/**
 * 表单统一提交入口：按当前步骤执行门槛校验、前进或启动不可跳过的安装事务链。
 */
async function nextStep(): Promise<void> {
  if (
    !currentStep.value
    || submitting.value
    || installing.value
  ) {
    return
  }

  submitting.value = true
  errorMessage.value = ''

  try {
    const key: SetupStepKey =
      currentStep.value.key

    if (key === 'environment') {
      if (!effectiveEnvironmentReady.value) {
        throw new Error(
          '真实环境检查未全部通过，暂时不能继续。',
        )
      }

      advance()
    } else if (key === 'services') {
      if (serviceStage.value === 'database-type') {
        if (!selectedDatabaseType.value) {
          throw new Error(
            '请先选择一种数据库。',
          )
        }

        advanceServiceStage(
          'database-connection',
        )

        return
      }

      if (!databasePassed.value) {
        throw new Error(
          '请先完成数据库真实连接和状态检测。',
        )
      }

      if (!databaseChoiceReady.value) {
        throw new Error(
          '请根据数据库状态完成恢复或重装确认。',
        )
      }

      if (
        form.redisEnabled
        && !redisPassed.value
      ) {
        throw new Error(
          '已选择 Redis，请先完成真实 Redis 连接测试。',
        )
      }

      if (
        databaseRecoverySelected.value
      ) {
        await recoverExistingInstallation()
      } else {
        advance()
      }
    } else if (key === 'identity') {
      validateSiteAndAdmin()
      advance()
    } else if (key === 'install') {
      validateSiteAndAdmin()

      if (reinstallRequested.value) {
        await openReinstallDialog()
      } else {
        await install(false)
      }
    }
  } catch (error) {
    errorMessage.value = messageOf(
      error,
      '当前步骤执行失败。',
    )

    if (installing.value) {
      markProgressError(
        errorMessage.value,
      )
    }
  } finally {
    submitting.value = false

    if (!installationCompleted.value) {
      installing.value = false
    }
  }
}

/**
 * 恢复完整已安装数据库到当前电脑。
 *
 * 后端会重新检测 INSTALLED、读取原站点设置、
 * 写入 application.yaml 和 install.lock。
 * 不创建管理员，也不执行数据库迁移。
 */
async function recoverExistingInstallation(): Promise<void> {
  if (!context.value) {
    throw new Error('安装上下文尚未加载。')
  }

  await testDatabase()

  if (!databaseRecoverySelected.value) {
    throw new Error(
      '数据库状态已经变化，请重新确认操作。',
    )
  }

  if (redisTestAvailable.value) {
    await testRedis()
  }

  await setupPost<SetupExistingInstallationRecoveryResult>(
    '/api/setup/recovery/existing',
    {
      serverPort: 8080,
      database: context.value.databaseManaged
        ? null
        : databaseSettings(),
      redis: context.value.redisManaged
        ? null
        : redisSettings(),
    },
  )

  form.databasePassword = ''
  form.redisPassword = ''

  window.location.assign(
    '/login?redirect=%2Fadmin&recovered=1',
  )
}

/**
 * 打开自定义危险操作确认弹窗。
 *
 * 这里只展示确认界面，不设置安装状态、不显示进度，也不调用任何写接口。
 */
async function openReinstallDialog(): Promise<void> {
  const mode = databaseInspection.value?.mode

  if (
    !reinstallConfirmationReady.value
    || (
      mode !== 'EXISTING_INSTALLED'
      && mode !== 'INCOMPLETE_INSTALLATION'
    )
  ) {
    throw new Error(
      '重装确认已经失效，请返回服务配置重新确认。',
    )
  }

  errorMessage.value = ''
  reinstallDialogOpen.value = true
  await nextTick()
  reinstallDialogRef.value?.focus()
}

/**
 * 关闭最终确认弹窗。
 *
 * 取消只关闭界面，不写错误提示，不改变数据库，也不会残留安装进度。
 */
function closeReinstallDialog(): void {
  if (submitting.value || installing.value) return
  reinstallDialogOpen.value = false
}

/**
 * 用户在自定义弹窗中完成最后确认后才真正启动安装。
 */
async function confirmReinstallDialog(): Promise<void> {
  if (submitting.value || installing.value) return

  reinstallDialogOpen.value = false
  submitting.value = true
  errorMessage.value = ''

  try {
    validateSiteAndAdmin()
    await install(true)
  } catch (error) {
    errorMessage.value = messageOf(
      error,
      '重新安装执行失败。',
    )

    if (installing.value) {
      markProgressError(errorMessage.value)
    }
  } finally {
    submitting.value = false

    if (!installationCompleted.value) {
      installing.value = false
    }
  }
}

/**
 * 依据后端实际操作边界更新进度。数据库表名来自迁移预检响应，不在前端伪造。
 *
 * @param dangerousReinstallConfirmed 只有自定义最终确认弹窗可以传入 true
 */
async function install(
  dangerousReinstallConfirmed: boolean,
): Promise<void> {
  if (!context.value) throw new Error('安装上下文尚未加载。')

  if (
    reinstallRequested.value
    && !dangerousReinstallConfirmed
  ) {
    throw new Error(
      '必须先在最终确认弹窗中确认重新安装。',
    )
  }

  installing.value = true
  resetAdaptiveInstallProgress()
  progressItems.value = []

  setInstallStage(
    6,
    context.value.databaseManaged
      ? '正在重新核验平台数据库状态...'
      : '正在重新验证数据库与缓存连接...',
  )
  await repaint()
  await testDatabase()

  let databasePreparedForNewInstall =
    databaseReadyForNewInstall.value

  if (reinstallRequested.value) {
    const mode = databaseInspection.value?.mode

    if (
      !reinstallConfirmationReady.value
      || (
        mode !== 'EXISTING_INSTALLED'
        && mode !== 'INCOMPLETE_INSTALLATION'
      )
    ) {
      throw new Error(
        '重装确认已经失效，请返回服务配置重新确认。',
      )
    }

    setInstallStage(
      12,
      '已再次确认危险操作，正在精确清理当前 Aquafish 表...',
    )
    await repaint()

    const resetResult =
      await setupPost<SetupDatabaseResetResult>(
        '/api/setup/database/reset',
        {
          database: context.value.databaseManaged
            ? null
            : databaseSettings(),
          expectedMode: mode,
          dataLossConfirmed: dataLossConfirmed.value,
          confirmationText: reinstallConfirmation.value,
        },
      )

    /*
     * reset 接口内部已经在删表后重新执行只读识别。
     * 这里直接核验后端返回的最终 NEW_INSTALL 状态，
     * 避免清理完成后再次发起检测时与总闸门产生竞态。
     */
    databasePreparedForNewInstall =
      resetResult.reset
      && resetResult.currentMode === 'NEW_INSTALL'

    if (!databasePreparedForNewInstall) {
      throw new Error(
        resetResult.message
          || '数据库清理后的状态不是 NEW_INSTALL，已停止安装。',
      )
    }
  }

  if (!databasePreparedForNewInstall) {
    throw new Error(
      databaseInspection.value?.note
        || '当前数据库状态不允许首次安装。',
    )
  }

  if (redisTestAvailable.value) {
    await testRedis()
  }

  setInstallStage(
    18,
    context.value.databaseManaged
      ? '平台配置已接管，正在写入站点运行设置...'
      : '连接验证通过，正在写入运行配置...',
  )
  await repaint()
  await setupPost('/api/setup/config/write', {
    serverPort: 8080,
    database: context.value.databaseManaged ? null : databaseSettings(),
    redis: context.value.redisManaged ? null : redisSettings(),
    site: siteSettings(),
    activeTheme: 'default',
  })

  setInstallStage(34, '运行配置已写入，正在预检数据库迁移计划...')
  await repaint()
  const preview = await setupPost<DatabaseMigrationPreview>('/api/setup/database/init/preview')
  const pendingTables = preview.tables.filter(table => !table.exists).map(table => table.tableName)

  setInstallStage(
    48,
    pendingTables.length
      ? `正在创建核心数据表：${pendingTables.join('、')}`
      : `数据库结构已存在，正在核对 ${preview.pendingMigrations} 个待执行迁移。`,
  )
  await repaint()
  const migration = await setupPost<DatabaseMigrationResult>('/api/setup/database/init')

  const applied = Math.max(0, migration.pendingBefore - migration.pendingAfter)
  setInstallStage(76, `数据库迁移完成：当前版本 ${migration.currentVersion || '已初始化'}，本次执行 ${applied} 个迁移。`)
  await repaint()

  setInstallStage(86, '正在创建超级管理员并原子提交站点安装状态...')
  await repaint()
  await setupPost('/api/setup/finish', {
    admin: {
      username: form.adminUsername,
      email: form.adminEmail,
      password: form.adminPassword,
      displayName: form.adminDisplayName,
    },
    site: siteSettings(),
  })

  setInstallStage(100, '站点初始化完成，安装入口已安全锁定。')
  await settleAdaptiveInstallProgress(100, 360)
  markActiveProgressDone()
  form.databasePassword = ''
  form.redisPassword = ''
  form.adminPassword = ''
  form.adminPasswordConfirm = ''
  status.value = await setupApi<InstallStatus>('/api/setup/status')
  installationCompleted.value = true
  installing.value = false
  const completeIndex = steps.value.findIndex(step => step.key === 'complete')
  currentStepIndex.value = completeIndex
  maxVisitedStep.value = completeIndex
  await repaint()
}

/**
 * 结束上一项并进入新的真实后端操作边界。
 *
 * 百分比不会直接跳到 progress，而是从当前显示值平滑经过该边界，
 * 再根据本机真实耗时继续向当前阶段上限移动。
 */
function setInstallStage(progress: number, message: string): void {
  recordCompletedInstallStageTiming()
  markActiveProgressDone()
  currentProgressMessage.value = message
  progressItems.value.push({ message, state: 'active' })
  startAdaptiveInstallProgress(progress)
}

/**
 * 开始一个新的显示阶段。
 * 真实边界决定下限和上限，动画永远不会跨越尚未完成的后端阶段。
 */
function startAdaptiveInstallProgress(stage: number): void {
  cancelAdaptiveInstallProgressFrame()

  const now = performance.now()
  installProgressStage = stage
  installProgressStageStartedAt = now
  installProgressStageStartValue = installProgress.value
  installProgressStageCeiling =
    installProgressStageCeilings[stage]
    ?? Math.min(99.2, stage + 5)
  installProgressExpectedDuration =
    expectedInstallStageDuration(stage)
  installProgressLastFrameAt = now

  installProgressFrame =
    window.requestAnimationFrame(
      updateAdaptiveInstallProgress,
    )
}

/**
 * 每一帧根据真实已耗时间计算显示目标。
 *
 * 曲线前段较慢，中段逐渐加快；超过预计耗时后继续缓慢逼近上限，
 * 但在后端进入下一阶段前绝不会提前越界。
 */
function updateAdaptiveInstallProgress(now: number): void {
  const elapsed = Math.max(
    0,
    now - installProgressStageStartedAt,
  )

  const frameDelta = Math.min(
    64,
    Math.max(1, now - installProgressLastFrameAt),
  )

  installProgressLastFrameAt = now

  const confirmedProgress = Math.min(
    installProgressStageCeiling,
    installProgressStage,
  )

  const confirmedDuration = Math.min(
    360,
    Math.max(150, installProgressExpectedDuration * 0.2),
  )

  const confirmedRatio = clampProgressRatio(
    elapsed / confirmedDuration,
  )

  const confirmedEasing = easeOutCubic(
    confirmedRatio,
  )

  const confirmedTarget =
    installProgressStageStartValue
    + (
      confirmedProgress
      - installProgressStageStartValue
    ) * confirmedEasing

  const operationRatio = clampProgressRatio(
    elapsed / installProgressExpectedDuration,
  )

  /* 用户要求的“开始慢、随后逐渐加快”。 */
  const operationEasing = Math.pow(
    operationRatio,
    1.75,
  )

  const operationTarget =
    confirmedProgress
    + (
      installProgressStageCeiling
      - confirmedProgress
    ) * operationEasing

  let target = Math.max(
    confirmedTarget,
    operationTarget,
  )

  /*
   * 后端实际耗时超过估算后不冻结。
   * 继续非常小幅地逼近上限，但永远不提前进入下一真实阶段。
   */
  if (elapsed > installProgressExpectedDuration) {
    const overtime =
      elapsed - installProgressExpectedDuration

    const overtimeRatio =
      1 - Math.exp(-overtime / 3600)

    target += (
      installProgressStageCeiling - target
    ) * overtimeRatio
  }

  target = Math.min(
    installProgressStageCeiling,
    Math.max(installProgress.value, target),
  )

  const distance =
    target - installProgress.value

  const smoothing = Math.min(
    1,
    frameDelta / 72,
  )

  if (distance > 0.001) {
    installProgress.value = Math.min(
      installProgressStageCeiling,
      installProgress.value
      + Math.max(0.008, distance * smoothing),
    )
  }

  if (
    installing.value
    && installProgressStage < 100
  ) {
    installProgressFrame =
      window.requestAnimationFrame(
        updateAdaptiveInstallProgress,
      )
    return
  }

  if (
    installProgressStage === 100
    && installProgress.value < 99.995
  ) {
    installProgressFrame =
      window.requestAnimationFrame(
        updateAdaptiveInstallProgress,
      )
    return
  }

  if (installProgressStage === 100) {
    installProgress.value = 100
  }

  installProgressFrame = null
}

/**
 * 读取当前设备过去记录的阶段耗时。
 * 不保存站点、数据库或账号等任何业务信息。
 */
function readInstallProgressTimings(): Record<string, number> {
  try {
    const raw = window.localStorage.getItem(
      installProgressTimingStorageKey,
    )

    if (!raw) return {}

    const value = JSON.parse(raw) as Record<string, unknown>
    const result: Record<string, number> = {}

    for (const [key, duration] of Object.entries(value)) {
      if (
        typeof duration === 'number'
        && Number.isFinite(duration)
        && duration >= 80
        && duration <= 120000
      ) {
        result[key] = duration
      }
    }

    return result
  } catch {
    return {}
  }
}

/** 保存当前阶段的真实耗时，并使用移动平均防止偶发抖动。 */
function saveInstallProgressTiming(
  stage: number,
  duration: number,
): void {
  if (
    stage >= 100
    || !Number.isFinite(duration)
    || duration < 80
    || duration > 120000
  ) {
    return
  }

  try {
    const timings = readInstallProgressTimings()
    const key = String(stage)
    const previous = timings[key]

    timings[key] = previous
      ? previous * 0.62 + duration * 0.38
      : duration

    window.localStorage.setItem(
      installProgressTimingStorageKey,
      JSON.stringify(timings),
    )
  } catch {
    /* localStorage 被禁用时直接使用本次运行估算。 */
  }
}

/** 使用历史真实耗时和本次运行性能系数生成当前阶段估算。 */
function expectedInstallStageDuration(
  stage: number,
): number {
  const baseline =
    installProgressStageBaselines[stage]
    ?? 1000

  const stored =
    readInstallProgressTimings()[String(stage)]

  const expected = stored
    ?? baseline * installProgressPerformanceFactor

  return Math.min(
    15000,
    Math.max(180, expected),
  )
}

/**
 * 阶段结束时记录真实耗时，并更新本次安装的整体性能系数。
 */
function recordCompletedInstallStageTiming(): void {
  if (
    installProgressStageStartedAt <= 0
    || installProgressStage >= 100
  ) {
    return
  }

  const duration = Math.max(
    0,
    performance.now()
    - installProgressStageStartedAt,
  )

  saveInstallProgressTiming(
    installProgressStage,
    duration,
  )

  const baseline =
    installProgressStageBaselines[
      installProgressStage
    ]

  if (baseline && duration >= 80) {
    const ratio = Math.min(
      2.6,
      Math.max(0.42, duration / baseline),
    )

    installProgressPerformanceFactor =
      installProgressPerformanceFactor * 0.68
      + ratio * 0.32
  }
}

/** 安装重新开始时清空显示引擎，不删除历史真实性能数据。 */
function resetAdaptiveInstallProgress(): void {
  cancelAdaptiveInstallProgressFrame()
  installProgress.value = 0
  installProgressStage = 0
  installProgressStageStartedAt = performance.now()
  installProgressStageStartValue = 0
  installProgressStageCeiling = 5.5
  installProgressExpectedDuration =
    expectedInstallStageDuration(0)
  installProgressLastFrameAt =
    installProgressStageStartedAt
}

/** 请求失败或组件卸载时停止显示动画。 */
function stopAdaptiveInstallProgress(): void {
  recordCompletedInstallStageTiming()
  cancelAdaptiveInstallProgressFrame()
}

function cancelAdaptiveInstallProgressFrame(): void {
  if (installProgressFrame === null) return
  window.cancelAnimationFrame(installProgressFrame)
  installProgressFrame = null
}

/** 后端完成以后，最多等待数百毫秒补齐最终 100%。 */
async function settleAdaptiveInstallProgress(
  target: number,
  maximumWaitMs: number,
): Promise<void> {
  const startedAt = performance.now()

  while (
    installProgress.value < target - 0.01
    && performance.now() - startedAt < maximumWaitMs
  ) {
    await new Promise<void>(resolve =>
      window.requestAnimationFrame(() => resolve()),
    )
  }

  installProgress.value = target
}

function clampProgressRatio(value: number): number {
  return Math.min(1, Math.max(0, value))
}

function easeOutCubic(value: number): number {
  return 1 - Math.pow(1 - value, 3)
}

/** 将最近一个活动进度项标记为成功。 */
function markActiveProgressDone(): void {
  const active = activeProgressItem()
  if (active) active.state = 'done'
}

/** 把当前活动项改为失败并保留原操作文案，便于定位安装停止位置。 */
function markProgressError(message: string): void {
  stopAdaptiveInstallProgress()
  const active = activeProgressItem()
  if (active) {
    active.state = 'error'
    active.message = `${active.message}（${message}）`
  } else {
    progressItems.value.push({ message, state: 'error' })
  }
  currentProgressMessage.value = '安装已停止，请根据错误修复后重试。'
}

/** 从后向前查找唯一活动项，避免错误修改已经完成的历史记录。 */
function activeProgressItem(): InstallProgressItem | undefined {
  for (let index = progressItems.value.length - 1; index >= 0; index -= 1) {
    const item = progressItems.value[index]
    if (item?.state === 'active') return item
  }
  return undefined
}

/** 等待 Vue DOM 更新和一帧浏览器绘制，确保长安装链中用户能看到进度变化。 */
async function repaint(): Promise<void> {
  await nextTick()
  await new Promise<void>(resolve => window.requestAnimationFrame(() => resolve()))
}

/** 把后端托管数据库摘要写入只读表单；平台密码始终不会进入浏览器。 */
function applyManagedContext(value: SetupDeploymentContext): void {
  if (!value.databaseManaged) return
  form.databaseType = value.database.type as DatabaseType
  form.databaseHost = value.database.host
  form.databasePort = value.database.port
  form.databaseName = value.database.name
  form.databaseUsername = value.database.username
  form.tablePrefix = value.database.tablePrefix
}

/**
 * 从环境步骤重新读取权威安装状态，再获取真实环境与部署上下文；已安装时立即离开向导。
 */
async function refreshEnvironment(): Promise<void> {
  environmentRefreshing.value = true
  errorMessage.value = ''
  try {
    const installStatus = await setupApi<InstallStatus>('/api/setup/status')
    status.value = installStatus
    if (
      installStatus.installed
      && !developmentReinstallMaintenance.value
    ) {
      await router.replace(installedSetupNoticeLocation())
      return
    }
    const deploymentContext = await setupApi<SetupDeploymentContext>('/api/setup/context')
    context.value = deploymentContext
    applyManagedContext(deploymentContext)
  } catch (error) {
    errorMessage.value = messageOf(error, '真实环境检测失败。')
  } finally {
    environmentRefreshing.value = false
  }
}

/**
 * 页面首次加载入口：先查安装状态，只有未安装时才读取敏感度更高的安装上下文。
 */
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const installStatus = await setupApi<InstallStatus>('/api/setup/status')
    status.value = installStatus
    if (
      installStatus.installed
      && !developmentReinstallMaintenance.value
    ) {
      await router.replace(installedSetupNoticeLocation())
      return
    }
    const deploymentContext = await setupApi<SetupDeploymentContext>('/api/setup/context')
    context.value = deploymentContext
    applyManagedContext(deploymentContext)
  } catch (error) {
    errorMessage.value = messageOf(error, '安装上下文读取失败。')
  } finally {
    loading.value = false
  }
}

/** 从未知异常提取用户可读信息，空异常统一使用当前操作的回退文案。 */
function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

onMounted(load)
onBeforeUnmount(() => {
  clearAgreementTimer()
  cancelAdaptiveInstallProgressFrame()
})
</script>
