<template>
  <div class="admin-page orchestration-page">
    <section class="admin-hero orchestration-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Task Governance</span>
        <h1 class="admin-hero__title">任务治理台</h1>
        <p class="admin-hero__desc">
          面向后台任务推进、人工复核与子任务分发，集中查看风险、保护状态和处理流向。
        </p>
        <div class="admin-hero__chips">
          <el-tag round effect="plain" type="primary">活跃任务 {{ overview.activeTaskCount || 0 }}</el-tag>
          <el-tag round effect="plain" type="warning">待复核 {{ overview.awaitingReviewTaskCount || 0 }}</el-tag>
          <el-tag round effect="plain" type="danger">熔断 {{ overview.circuitBreakerTaskCount || 0 }}</el-tag>
        </div>
      </div>
      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="tickLoading" @click="triggerTick">手动推进一次</el-button>
        <el-button class="admin-hero__ghost" :loading="loading" @click="loadDashboard">刷新面板</el-button>
      </div>
    </section>

    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
      class="orchestration-alert"
    />

    <section class="admin-stat-grid admin-stat-grid--four">
      <article class="admin-stat-card admin-stat-card--blue">
        <span>自动推进</span>
        <strong>{{ overview.autoAdvanceTaskCount || 0 }}</strong>
        <p>当前无需人工干预、可由系统继续推进的任务数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>人工复核建议</span>
        <strong>{{ overview.manualReviewRecommendedTaskCount || 0 }}</strong>
        <p>存在低置信度转派建议或触发人工审核的任务数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--red">
        <span>24h 待复核新增</span>
        <strong>{{ overview.recent24hAwaitingReviewCount || 0 }}</strong>
        <p>最近 24 小时进入待复核状态的任务数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>24h 已确认</span>
        <strong>{{ overview.recent24hReviewAckedCount || 0 }}</strong>
        <p>最近 24 小时人工确认后恢复自动推进资格的任务数。</p>
      </article>
    </section>

    <section class="admin-stat-grid admin-stat-grid--four">
      <article class="admin-stat-card admin-stat-card--green">
        <span>契约已验证</span>
        <strong>{{ overview.validatedContractTaskCount || 0 }}</strong>
        <p>最近活跃任务中，最新交接契约通过 schema 与兼容性校验的任务数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--red">
        <span>契约异常</span>
        <strong>{{ overview.invalidContractTaskCount || 0 }}</strong>
        <p>最近活跃任务中，最新交接契约存在 schema 或版本兼容异常的任务数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>主兼容状态</span>
        <strong>{{ dominantContractCompatibilityLabel }}</strong>
        <p>按任务最新契约摘要聚合出的主导兼容状态。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--blue">
        <span>主校验异常</span>
        <strong>{{ dominantContractErrorLabel }}</strong>
        <p>最近活跃任务里最常见的契约 schema/version 校验错误。</p>
      </article>
    </section>

    <section class="agent-health-grid">
      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head admin-section-head--compact">
            <div>
              <span class="admin-section-head__eyebrow">Service Health</span>
              <h2>实现级健康榜</h2>
              <p>这里查看的是最近 24 小时真实落地执行的服务实现与提供方表现。</p>
            </div>
          </div>
        </template>
        <div class="agent-health-list">
          <article
            v-for="item in topUnhealthyResolvedAgents"
            :key="`health-${item.resolvedAgentName || item.agentName || item.agent}`"
            class="agent-health-card"
          >
            <div class="agent-health-card__head">
              <strong>{{ agentDisplayName(item) }}</strong>
              <div class="agent-health-card__tags">
                <el-tag size="small" :type="agentHealthTagType(item)">
                  {{ agentHealthStateLabel(item.healthState) }}
                </el-tag>
                <el-tag v-if="item.routingPreferred" size="small" effect="plain" type="primary">当前主实现</el-tag>
              </div>
            </div>
            <div class="agent-health-card__meta">
              <span>Route {{ item.routeKey || '--' }}</span>
              <span>提供方 {{ item.provider || '--' }}</span>
              <span>提供方状态 {{ agentHealthStateLabel(item.providerHealthState) }}</span>
              <span>成功率 {{ formatRatio(item.successRate) }}</span>
              <span>稳定度 {{ formatRatio(item.stabilityScore) }}</span>
              <span v-if="item.recentConsecutiveFailureCount > 0">连续失败 {{ item.recentConsecutiveFailureCount }} 次</span>
              <span>执行 {{ item.executionCount || 0 }}</span>
            </div>
          </article>
          <el-empty v-if="topUnhealthyResolvedAgents.length === 0" description="最近 24h 暂无实现级健康数据" />
        </div>
      </el-card>

      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head admin-section-head--compact">
            <div>
              <span class="admin-section-head__eyebrow">Service Issues</span>
              <h2>实现级异常榜</h2>
              <p>按真实执行实现聚合最近 24 小时的失败类别与缺失能力信号。</p>
            </div>
          </div>
        </template>
        <div class="agent-health-list">
          <article
            v-for="item in topIssueHeavyResolvedAgents"
            :key="`issue-${item.resolvedAgentName || item.agentName || item.agent}`"
            class="agent-health-card"
          >
            <div class="agent-health-card__head">
              <strong>{{ agentDisplayName(item) }}</strong>
              <div class="agent-health-card__tags">
                <el-tag size="small" type="danger">异常 {{ item.issueCount || 0 }}</el-tag>
                <el-tag
                  v-if="item.healthState"
                  size="small"
                  effect="plain"
                  :type="agentHealthTagType(item)"
                >
                  {{ agentHealthStateLabel(item.healthState) }}
                </el-tag>
              </div>
            </div>
            <div class="agent-health-card__meta">
              <span>Route {{ item.routeKey || '--' }}</span>
              <span>提供方 {{ item.provider || '--' }}</span>
              <span>提供方状态 {{ agentHealthStateLabel(item.providerHealthState) }}</span>
              <span>主失败 {{ topSignalLabel(item.topFailureCategories) }}</span>
              <span>主缺口 {{ topSignalLabel(item.topMissingCapabilities) }}</span>
              <span v-if="item.recentConsecutiveFailureCount > 0">连续失败 {{ item.recentConsecutiveFailureCount }} 次</span>
            </div>
          </article>
          <el-empty v-if="topIssueHeavyResolvedAgents.length === 0" description="最近 24h 暂无实现级异常聚合" />
        </div>
      </el-card>
    </section>

    <section class="admin-grid admin-grid--sidebar orchestration-grid">
      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Filters</span>
              <h2>任务筛选</h2>
              <p>按控制状态、审核触发器、任务类型与熔断状态收窄任务列表。</p>
            </div>
          </div>
        </template>

        <div class="admin-toolbar">
          <el-input v-model.trim="filters.keyword" clearable placeholder="筛选任务 ID / session / goal" class="toolbar-keyword" />
          <el-select v-model="filters.controlStatus" clearable placeholder="控制状态" class="toolbar-short">
            <el-option v-for="item in controlStatusOptions" :key="item.key" :label="`${item.key} (${item.count})`" :value="item.key" />
          </el-select>
          <el-select v-model="filters.reviewTrigger" clearable placeholder="复核触发器" class="toolbar-role">
            <el-option v-for="item in reviewTriggerOptions" :key="item.key" :label="`${item.key} (${item.count})`" :value="item.key" />
          </el-select>
          <el-select v-model="filters.taskType" clearable placeholder="任务类型" class="toolbar-role">
            <el-option v-for="item in taskTypeOptions" :key="item.key" :label="`${item.key} (${item.count})`" :value="item.key" />
          </el-select>
          <el-select v-model="filters.contractCompatibility" clearable placeholder="契约兼容状态" class="toolbar-role">
            <el-option v-for="item in contractCompatibilityOptions" :key="item.key" :label="`${contractCompatibilityLabel(item.key)} (${item.count})`" :value="item.key" />
          </el-select>
          <el-select v-model="filters.contractValidationState" clearable placeholder="契约校验状态" class="toolbar-role">
            <el-option label="已验证" value="validated" />
            <el-option label="存在异常" value="invalid" />
            <el-option label="仅异常详情" value="errors_only" />
          </el-select>
          <el-select v-model="filters.sortBy" placeholder="排序方式" class="toolbar-role">
            <el-option label="运维优先级" value="priority" />
            <el-option label="最近更新" value="updatedAt" />
            <el-option label="最近运行" value="nextRunAt" />
            <el-option label="失败连续次数" value="failureStreak" />
          </el-select>
          <el-switch v-model="filters.circuitBreakerOnly" inline-prompt active-text="仅熔断" inactive-text="全部" />
          <el-button :loading="loading" @click="resetFilters">重置</el-button>
          <el-button type="primary" :loading="loading" @click="loadCards">应用筛选</el-button>
        </div>

        <div class="highlight-grid">
          <article class="highlight-card">
            <strong>主导复核触发器</strong>
            <p>{{ dominantReviewTriggerLabel }}</p>
          </article>
          <article class="highlight-card">
            <strong>需优先关注任务</strong>
            <p>{{ actionRequiredSummary }}</p>
          </article>
          <article class="highlight-card">
            <strong>不稳定服务</strong>
            <p>{{ unstableAgentSummary }}</p>
          </article>
          <article class="highlight-card">
            <strong>契约兼容分布</strong>
            <p>{{ contractCompatibilitySummary }}</p>
          </article>
        </div>
      </el-card>

      <div class="admin-aside-stack">
        <el-card shadow="never" class="admin-card admin-sticky-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Task Detail</span>
                <h2>任务详情</h2>
                <p>点击左侧任务卡后，可查看后台任务处理时间线、子任务和人工控制动作。这些按钮控制的是任务推进，不是聊天内容显示。</p>
              </div>
            </div>
          </template>
          <div v-if="detail.taskId" class="detail-stack">
            <div class="detail-head">
              <div>
                <strong>{{ detail.taskId }}</strong>
                <p>{{ detail.goal || '--' }}</p>
              </div>
              <el-tag size="small" :type="controlStatusTag(detail.controlStatus)">{{ detail.controlStatus || '--' }}</el-tag>
            </div>

            <div class="detail-meta">
              <span>类型 {{ detail.taskType || '--' }}</span>
              <span>状态 {{ detail.status || '--' }}</span>
              <span>负责人 {{ detail.assignedAgent || '--' }}</span>
            </div>

            <div class="detail-overview-grid">
              <section class="detail-panel detail-panel--status">
                <div class="detail-panel__eyebrow">运行状态</div>
                <div class="detail-panel__head">
                  <strong>{{ detailStatusHeadline }}</strong>
                  <el-tag size="small" :type="detailStatusTagType">{{ detailStatusTagText }}</el-tag>
                </div>
                <p class="detail-panel__desc">{{ detail.currentStatusSummary || '--' }}</p>
                <div class="detail-panel__grid">
                  <div class="detail-panel__metric">
                    <span>下一步</span>
                    <strong>{{ detail.pendingReviewAction || detail.nextAction || '--' }}</strong>
                  </div>
                  <div class="detail-panel__metric">
                    <span>当前进度</span>
                    <strong>{{ detail.progressSummary || '--' }}</strong>
                  </div>
                  <div class="detail-panel__metric">
                    <span>失败连续次数</span>
                    <strong>{{ detail.watchdogFailureStreak || 0 }}</strong>
                  </div>
                  <div class="detail-panel__metric">
                    <span>熔断状态</span>
                    <strong>{{ detail.circuitBreakerOpen ? `开启至 ${formatTime(detail.circuitBreakerOpenUntil)}` : '未开启' }}</strong>
                  </div>
                </div>
                <div v-if="detail.circuitBreakerOpenReason" class="detail-panel__note detail-panel__note--warning">
                  熔断原因：{{ detail.circuitBreakerOpenReason }}
                </div>
              </section>

              <section class="detail-panel detail-panel--actions">
                <div class="detail-panel__eyebrow">运维操作</div>
                <div class="detail-panel__head">
                  <strong>管理员处置入口</strong>
                  <span class="detail-panel__microcopy">仅保留当前业务场景下真正有意义的操作。</span>
                </div>
                <div class="detail-actions detail-actions--stacked">
                  <el-button size="small" :loading="actionLoading === 'pause'" @click="runTaskAction('pause')">暂停</el-button>
                  <el-button size="small" type="success" :loading="actionLoading === 'resume'" @click="runTaskAction('resume')">恢复</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="actionLoading === 'ack'"
                    :disabled="detail.controlStatus !== 'AWAITING_REVIEW'"
                    @click="runTaskAction('ack')"
                  >
                    复核后继续
                  </el-button>
                  <el-button size="small" type="danger" :loading="actionLoading === 'retry'" @click="runTaskAction('retry')">强制重跑</el-button>
                </div>
                <div class="detail-panel__note">
                  {{ operationGuidanceText }}
                </div>
              </section>
            </div>

            <div v-if="latestControlOutcome.actionType" class="detail-section">
              <h3>{{ latestControlOutcomeTitle }}</h3>
              <div class="control-outcome-card" :class="`control-outcome-card--${latestControlOutcome.status || 'active'}`">
                <div class="control-outcome-card__head">
                  <div>
                    <strong>{{ latestControlOutcome.summary || '暂无结果摘要' }}</strong>
                    <p>{{ latestControlOutcomeMeta }}</p>
                  </div>
                  <el-tag size="small" :type="controlOutcomeTagType(latestControlOutcome.status)">
                    {{ controlOutcomeText(latestControlOutcome.status) }}
                  </el-tag>
                </div>
                <div class="control-outcome-card__chips">
                  <span class="control-outcome-card__chip">{{ formatAuditType(latestControlOutcome.actionType) }}</span>
                  <span v-if="latestControlOutcome.operator" class="control-outcome-card__chip">操作人 {{ latestControlOutcome.operator }}</span>
                  <span v-if="latestControlOutcome.actionAt" class="control-outcome-card__chip">{{ formatTime(latestControlOutcome.actionAt) }}</span>
                </div>
                <div class="control-outcome-card__stats">
                  <article class="control-outcome-card__stat">
                    <span>系统反馈</span>
                    <strong>{{ latestControlOutcome.followUpEventCount || 0 }}</strong>
                  </article>
                  <article class="control-outcome-card__stat">
                    <span>剩余问题</span>
                    <strong>{{ controlOutcomeBlockerCount }}</strong>
                  </article>
                </div>
                <div v-if="latestControlOutcome.reason" class="control-outcome-card__reason">
                  本次处理说明：{{ latestControlOutcome.reason }}
                </div>
                <div v-if="controlOutcomePrimaryBlocker" class="control-outcome-card__reason control-outcome-card__reason--warning">
                  当前最关键问题：{{ controlOutcomePrimaryBlocker }}
                </div>
                <div v-if="controlOutcomeBlockerOverflow > 0" class="control-outcome-card__reason">
                  另有 {{ controlOutcomeBlockerOverflow }} 个待处理问题未展开显示。
                </div>
                <div v-if="latestControlOutcome.nextAction" class="control-outcome-card__next">
                  建议下一步：{{ latestControlOutcome.nextAction }}
                </div>
                <div v-if="controlOutcomeHighlights.length > 0" class="control-outcome-card__timeline">
                  <article
                    v-for="(item, index) in controlOutcomeHighlights"
                    :key="`control-outcome-${index}`"
                    class="control-outcome-card__timeline-item"
                  >
                    <strong>{{ item.summary || '--' }}</strong>
                    <span>{{ formatTime(item.occurredAt) }}</span>
                  </article>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h3>契约健康</h3>
              <div class="audit-summary-grid">
                <article class="audit-summary-card">
                  <span>最近契约</span>
                  <strong>{{ detailContractHeadline }}</strong>
                  <p>{{ detailContractMeta }}</p>
                </article>
                <article class="audit-summary-card">
                  <span>建议接力</span>
                  <strong>{{ detail.latestContractSuggestedHandoffAgent || '--' }}</strong>
                  <p>最近一次交接契约里记录的建议转派目标。</p>
                </article>
                <article class="audit-summary-card">
                  <span>缺失能力</span>
                  <strong>{{ detailContractMissingCapabilitiesSummary }}</strong>
                  <p>最近一次交接契约里显式声明的能力缺口。</p>
                </article>
              </div>
              <div v-if="normalizeArray(detail.latestContractValidationErrors).length > 0" class="contract-error-list">
                <article
                  v-for="(item, index) in normalizeArray(detail.latestContractValidationErrors)"
                  :key="`detail-contract-error-${index}`"
                  class="contract-error-item"
                >
                  {{ item }}
                </article>
              </div>
            </div>

            <div class="detail-section">
              <h3>控制动作审计</h3>
              <div class="audit-summary-grid">
                <article class="audit-summary-card">
                  <span>最近控制</span>
                  <strong>{{ latestControlSummary.label }}</strong>
                  <p>{{ latestControlSummary.meta }}</p>
                </article>
                <article class="audit-summary-card">
                  <span>操作人分布</span>
                  <strong>{{ auditOperatorSummary }}</strong>
                  <p>按时间线里的控制动作事件聚合最近操作人。</p>
                </article>
                <article class="audit-summary-card">
                  <span>控制动作数</span>
                  <strong>{{ auditEvents.length }}</strong>
                  <p>当前任务内 pause / resume / ack / retry / cancel 等控制事件总数。</p>
                </article>
              </div>

              <div class="audit-toolbar">
                <el-select v-model="auditFilters.actionType" clearable placeholder="动作筛选" class="toolbar-role">
                  <el-option v-for="item in auditActionOptions" :key="item" :label="item" :value="item" />
                </el-select>
                <el-input
                  v-model.trim="auditFilters.operator"
                  clearable
                  placeholder="筛选操作人"
                  class="toolbar-short"
                />
                <el-input
                  v-model.trim="auditFilters.keyword"
                  clearable
                  placeholder="筛选原因/摘要"
                  class="toolbar-keyword"
                />
                <el-button @click="resetAuditFilters">重置</el-button>
              </div>

              <div class="audit-list">
                <article v-for="(item, index) in filteredAuditEvents" :key="`audit-${item.occurredAt}-${index}`" class="audit-item">
                  <div class="audit-item__head">
                    <div class="audit-item__title">
                      <el-tag size="small" :type="auditTagType(item.type)">{{ formatAuditType(item.type) }}</el-tag>
                      <strong>{{ item.operator || '系统' }}</strong>
                    </div>
                    <span>{{ formatTime(item.occurredAt) }}</span>
                  </div>
                  <div class="audit-item__meta">
                    <span v-if="item.targetAgent">目标 {{ item.targetAgent }}</span>
                    <span v-if="item.resolvedAgentName">执行 {{ item.resolvedAgentName }}</span>
                    <span v-if="item.resolvedAgentProvider">提供方 {{ item.resolvedAgentProvider }}</span>
                    <span v-if="item.subTaskId">子任务 {{ item.subTaskId }}</span>
                    <span>{{ item.type || '--' }}</span>
                  </div>
                  <div class="audit-item__text">{{ item.reason || item.displaySummary || item.summary || '--' }}</div>
                </article>
                <el-empty v-if="filteredAuditEvents.length === 0" description="当前筛选下没有控制动作" />
              </div>
            </div>

            <div class="detail-section">
              <h3>子任务</h3>
              <div class="subtask-list">
                <article v-for="item in detailSubTasks" :key="item.subTaskId" class="subtask-card">
                  <div class="subtask-card__head">
                    <strong>{{ item.subTaskId || '--' }}</strong>
                    <el-tag size="small" :type="item.recommendedAgentNeedsReview ? 'warning' : 'success'">
                      {{ item.recommendedAgentNeedsReview ? '需复核' : '可自动接力' }}
                    </el-tag>
                  </div>
                  <div class="subtask-card__meta">
                    <span>当前 {{ item.targetAgent || '--' }}</span>
                    <span v-if="item.recentResolvedAgentName">执行 {{ item.recentResolvedAgentName }}</span>
                    <span v-if="item.recentResolvedAgentProvider">提供方 {{ item.recentResolvedAgentProvider }}</span>
                    <span>建议 {{ item.recommendedAgent || '--' }}</span>
                    <span>触发 {{ item.recommendedAgentReviewTrigger || '--' }}</span>
                  </div>
                  <div class="subtask-card__desc">{{ item.recommendedAgentReason || item.description || '--' }}</div>
                  <div class="subtask-card__meta">
                    <span>波动 {{ toFixed(item.recommendedAgentVolatility) }}</span>
                    <span>置信 {{ toFixed(item.recommendedAgentConfidence) }}</span>
                    <span v-if="item.recentFailureCategory">失败 {{ item.recentFailureCategory }}</span>
                  </div>
                  <div class="subtask-card__meta">
                    <span>契约 {{ contractCompatibilityLabel(item.recentContractCompatibility) }}</span>
                    <span v-if="item.recentContractSchema">schema {{ item.recentContractSchema }}</span>
                    <span v-if="item.recentContractVersion">version {{ item.recentContractVersion }}</span>
                  </div>
                  <div class="subtask-card__chips">
                    <el-tag
                      v-for="cap in normalizeArray(item.recentMissingCapabilities)"
                      :key="`${item.subTaskId}-${cap}`"
                      size="small"
                      effect="plain"
                    >
                      {{ cap }}
                    </el-tag>
                    <el-tag
                      v-for="(error, index) in normalizeArray(item.recentContractValidationErrors)"
                      :key="`${item.subTaskId}-contract-error-${index}`"
                      size="small"
                      effect="plain"
                      type="danger"
                    >
                      {{ error }}
                    </el-tag>
                  </div>
                  <div class="subtask-card__actions">
                    <el-button size="small" :disabled="!canRunSubTaskAction(item, 'reset')" @click="runSubTaskAction(item, 'reset')">
                      重置
                    </el-button>
                    <el-button size="small" type="warning" :disabled="!canRunSubTaskAction(item, 'skip')" @click="runSubTaskAction(item, 'skip')">
                      跳过
                    </el-button>
                    <el-button size="small" type="primary" :disabled="!canRunSubTaskAction(item, 'handoff')" @click="runSubTaskAction(item, 'handoff')">
                      转派建议服务
                    </el-button>
                    <el-button size="small" type="success" :disabled="!canRunSubTaskAction(item, 'capability')" @click="runSubTaskAction(item, 'capability')">
                      按能力补位
                    </el-button>
                  </div>
                  <div class="subtask-card__hint">
                    {{ subTaskOperationHint(item) }}
                  </div>
                </article>
                <el-empty v-if="detailSubTasks.length === 0" description="暂无子任务详情" />
              </div>
            </div>

            <div class="detail-section">
              <h3>时间线</h3>
              <div class="timeline-toolbar">
                <el-select v-model="timelineFilters.stateLabel" clearable placeholder="状态筛选" class="toolbar-short">
                  <el-option label="awaiting_review" value="awaiting_review" />
                  <el-option label="review_acked" value="review_acked" />
                  <el-option label="其他事件" value="other" />
                </el-select>
                <el-input
                  v-model.trim="timelineFilters.operator"
                  clearable
                  placeholder="筛选操作人"
                  class="toolbar-short"
                />
                <el-input
                  v-model.trim="timelineFilters.reason"
                  clearable
                  placeholder="筛选原因/摘要"
                  class="toolbar-keyword"
                />
                <el-button @click="resetTimelineFilters">重置</el-button>
              </div>
              <div class="timeline-list">
                <article
                  v-for="(item, index) in filteredTimeline"
                  :key="`${item.occurredAt}-${index}`"
                  :class="['timeline-item', timelineItemClass(item)]"
                >
                  <div class="timeline-item__head">
                    <strong>{{ item.displaySummary || item.summary || '--' }}</strong>
                    <span>{{ formatTime(item.occurredAt) }}</span>
                  </div>
                  <div class="timeline-item__meta">
                    <el-tag size="small" :type="timelineTagType(item)">{{ timelineStateText(item) }}</el-tag>
                    <span>{{ item.type || '--' }}</span>
                    <span v-if="item.targetAgent">目标 {{ item.targetAgent }}</span>
                    <span v-if="item.resolvedAgentName">执行 {{ item.resolvedAgentName }}</span>
                    <span v-if="item.resolvedAgentProvider">提供方 {{ item.resolvedAgentProvider }}</span>
                    <span v-if="item.operator">操作人 {{ item.operator }}</span>
                  </div>
                  <div v-if="item.reason" class="timeline-item__reason">原因：{{ item.reason }}</div>
                  <div v-if="item.message && item.message !== item.reason" class="timeline-item__reason">消息：{{ item.message }}</div>
                </article>
                <el-empty v-if="filteredTimeline.length === 0" description="当前筛选下暂无时间线" />
              </div>
            </div>
          </div>
          <div v-else class="admin-empty-block">
            <el-empty description="请选择左侧任务查看详情" />
          </div>
        </el-card>
      </div>
    </section>

    <el-card shadow="never" class="admin-card admin-card--table">
      <template #header>
        <div class="admin-section-head">
          <div>
            <span class="admin-section-head__eyebrow">Active Tasks</span>
            <h2>任务卡片</h2>
            <p>展示每个任务的控制状态、保护情况、建议接力服务与复核摘要。</p>
          </div>
          <div class="admin-section-head__meta">
            <el-tag effect="plain" type="warning">排序 {{ sortLabel }}</el-tag>
            <el-tag effect="plain">当前 {{ filteredCards.length }} 项</el-tag>
          </div>
        </div>
      </template>

      <div v-if="filteredCards.length > 0" class="task-card-grid">
        <article
          v-for="item in filteredCards"
          :key="item.taskId"
          :class="['task-card', { 'task-card--active': item.taskId === detail.taskId, 'task-card--danger': item.circuitBreakerOpen }]"
          @click="selectTask(item)"
        >
          <div class="task-card__head">
            <div>
              <strong>{{ item.taskId }}</strong>
              <p>{{ item.goal || '--' }}</p>
            </div>
            <el-tag size="small" :type="controlStatusTag(item.controlStatus)">{{ item.controlStatus || '--' }}</el-tag>
          </div>
          <div class="task-card__meta">
            <span>{{ item.taskType || '--' }}</span>
            <span>{{ item.status || '--' }}</span>
            <span>子任务 {{ item.subTaskCount || 0 }}</span>
          </div>
          <div class="task-card__audit">
            <el-tag
              v-if="item.latestControlAction"
              size="small"
              :type="auditTagType(item.latestControlAction)"
            >
              {{ formatAuditType(item.latestControlAction) }}
            </el-tag>
            <span v-if="item.latestControlBy">操作人 {{ item.latestControlBy }}</span>
            <span v-if="item.latestControlAt">控制于 {{ formatTime(item.latestControlAt) }}</span>
          </div>
          <div v-if="item.latestControlReason" class="task-card__audit-reason">
            {{ item.latestControlReason }}
          </div>
          <div class="task-card__summary">{{ item.manualReviewSummary || watchdogDecisionLabel(item.watchdogDecision) }}</div>
          <div class="task-card__chips">
            <el-tag
              size="small"
              effect="plain"
              :type="contractCompatibilityTag(item.latestContractCompatibility, item.latestContractValidated)"
            >
              {{ contractCompatibilityLabel(item.latestContractCompatibility, item.latestContractValidated) }}
            </el-tag>
            <el-tag v-for="trigger in normalizeArray(item.reviewTriggers)" :key="`${item.taskId}-${trigger}`" size="small" effect="plain" type="warning">
              {{ trigger }}
            </el-tag>
            <el-tag v-for="agent in normalizeArray(item.recommendedAgents)" :key="`${item.taskId}-${agent}`" size="small" effect="plain">
              {{ agent }}
            </el-tag>
            <el-tag
              v-for="(error, index) in normalizeArray(item.latestContractValidationErrors).slice(0, 2)"
              :key="`${item.taskId}-contract-error-${index}`"
              size="small"
              effect="plain"
              type="danger"
            >
              {{ error }}
            </el-tag>
          </div>
          <div class="task-card__foot">
            <span>更新 {{ formatTime(item.updatedAt) }}</span>
            <span v-if="item.circuitBreakerOpen">熔断至 {{ formatTime(item.circuitBreakerOpenUntil) }}</span>
            <span v-else>失败连续 {{ item.watchdogFailureStreak || 0 }}</span>
          </div>
        </article>
      </div>
      <div v-else class="admin-empty-block">
        <el-empty description="当前筛选条件下没有任务" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import { adminApi } from '../../api'
import { echarts } from '../../utils/echarts-basic'

const loading = ref(false)
const tickLoading = ref(false)
const actionLoading = ref('')
const loadError = ref('')
const overview = ref({})
const filterOptions = ref({})
const cards = ref([])
const detail = ref({})
const hourlyTrendChartRef = ref(null)
const failureCategoryChartRef = ref(null)
const hourlyTrendChart = ref(null)
const failureCategoryChart = ref(null)
const route = useRoute()

const filters = reactive({
  keyword: '',
  controlStatus: '',
  reviewTrigger: '',
  taskType: '',
  contractCompatibility: '',
  contractValidationState: '',
  sortBy: 'priority',
  circuitBreakerOnly: false
})
const timelineFilters = reactive({
  stateLabel: '',
  operator: '',
  reason: ''
})
const auditFilters = reactive({
  actionType: '',
  operator: '',
  keyword: ''
})

const controlStatusOptions = computed(() => normalizeArray(filterOptions.value.controlStatuses))
const reviewTriggerOptions = computed(() => normalizeArray(filterOptions.value.reviewTriggers))
const taskTypeOptions = computed(() => normalizeArray(filterOptions.value.taskTypes))
const contractCompatibilityOptions = computed(() => normalizeArray(filterOptions.value.contractCompatibilities))
const topManualReasons = computed(() => normalizeArray(overview.value.topManualReviewReasons))
const topFailureCategories = computed(() => normalizeArray(overview.value.topFailureCategories))
const topContractValidationErrors = computed(() => normalizeArray(overview.value.topContractValidationErrors))
const detailSubTasks = computed(() => normalizeArray(detail.value.subTasks))
const detailTimeline = computed(() => normalizeArray(detail.value.timeline))
const dashboardHighlights = computed(() => normalizeObject(overview.value.dashboardHighlights))
const recentHourlyTrend = computed(() => normalizeArray(overview.value.recent24hHourlyTrend))
const recentTopFailureCategories = computed(() => normalizeArray(overview.value.recent24hTopFailureCategories))
const unstableAgents = computed(() => normalizeArray(dashboardHighlights.value.unstableAgents))
const topUnhealthyResolvedAgents = computed(() => normalizeArray(overview.value.topUnhealthyResolvedAgents))
const topIssueHeavyResolvedAgents = computed(() => normalizeArray(overview.value.topIssueHeavyResolvedAgents))
const auditEvents = computed(() => detailTimeline.value.filter(item => isAuditEvent(item)))
const latestControlOutcome = computed(() => normalizeObject(detail.value.latestControlOutcome))
const latestControlOutcomeTitle = computed(() => {
  return latestControlOutcome.value.actionType === 'MANUAL_RETRIED' ? '重跑结论' : '干预结论'
})
const latestControlOutcomeMeta = computed(() => {
  const parts = []
  if (latestControlOutcome.value.actionType) {
    parts.push(`${formatAuditType(latestControlOutcome.value.actionType)}后`)
  }
  if (latestControlOutcome.value.status) {
    parts.push(controlOutcomeText(latestControlOutcome.value.status))
  }
  return parts.join(' · ')
})
const controlOutcomeHighlights = computed(() => normalizeArray(latestControlOutcome.value.highlights).slice(-3).reverse())
const controlOutcomeBlockerCount = computed(() => normalizeArray(latestControlOutcome.value.blockers).length)
const controlOutcomePrimaryBlocker = computed(() => normalizeArray(latestControlOutcome.value.blockers)[0] || '')
const controlOutcomeBlockerOverflow = computed(() => Math.max(0, controlOutcomeBlockerCount.value - 1))
const auditActionOptions = computed(() => {
  return [...new Set(auditEvents.value.map(item => String(item?.type || '')).filter(Boolean))]
})
const filteredAuditEvents = computed(() => {
  const operatorKeyword = auditFilters.operator.trim().toLowerCase()
  const textKeyword = auditFilters.keyword.trim().toLowerCase()
  return auditEvents.value.filter((item) => {
    if (auditFilters.actionType && item?.type !== auditFilters.actionType) {
      return false
    }
    if (operatorKeyword && !String(item?.operator || '').toLowerCase().includes(operatorKeyword)) {
      return false
    }
    if (textKeyword) {
      const text = [item?.reason, item?.displaySummary, item?.summary, item?.message]
        .map(v => String(v || '').toLowerCase())
        .join(' ')
      if (!text.includes(textKeyword)) {
        return false
      }
    }
    return true
  })
})
const latestControlSummary = computed(() => {
  const latest = auditEvents.value[auditEvents.value.length - 1]
  if (!latest) {
    return { label: '暂无', meta: '当前任务还没有人工控制记录。' }
  }
  return {
    label: `${formatAuditType(latest.type)} / ${latest.operator || '系统'}`,
    meta: `${formatTime(latest.occurredAt)}${latest.reason ? ` · ${latest.reason}` : ''}`
  }
})
const auditOperatorSummary = computed(() => {
  const counts = new Map()
  auditEvents.value.forEach((item) => {
    const key = item?.operator || '系统'
    counts.set(key, (counts.get(key) || 0) + 1)
  })
  const rows = [...counts.entries()].sort((a, b) => b[1] - a[1])
  if (rows.length === 0) return '暂无'
  return rows.slice(0, 2).map(([name, count]) => `${name} (${count})`).join(' / ')
})
const detailStatusTagType = computed(() => controlStatusTag(detail.value.controlStatus))
const detailStatusTagText = computed(() => detail.value.controlStatus || '--')
const detailContractHeadline = computed(() => {
  return contractCompatibilityLabel(detail.value.latestContractCompatibility, detail.value.latestContractValidated)
})
const detailContractMeta = computed(() => {
  const parts = []
  if (detail.value.latestContractSchema) {
    parts.push(`schema ${detail.value.latestContractSchema}`)
  }
  if (detail.value.latestContractVersion) {
    parts.push(`version ${detail.value.latestContractVersion}`)
  }
  if (detail.value.latestContractValidationErrorCount != null) {
    parts.push(`异常 ${detail.value.latestContractValidationErrorCount}`)
  }
  return parts.length > 0 ? parts.join(' · ') : '当前任务还没有可用的契约摘要。'
})
const detailContractMissingCapabilitiesSummary = computed(() => {
  const items = normalizeArray(detail.value.latestContractMissingCapabilities)
  return items.length > 0 ? items.join(' / ') : '--'
})
const detailStatusHeadline = computed(() => {
  if (detail.value.circuitBreakerOpen) return '当前任务已进入熔断保护'
  if (detail.value.controlStatus === 'AWAITING_REVIEW') return '当前任务等待管理员确认'
  if (detail.value.controlStatus === 'PAUSED') return '当前任务已被人工暂停'
  if (detail.value.status === 'BLOCKED') return '当前任务存在业务阻塞'
  if (detail.value.status === 'FAILED') return '当前任务执行失败'
  if (detail.value.status === 'COMPLETED') return '当前任务已完成'
  return '当前任务可继续自动推进'
})
const operationGuidanceText = computed(() => {
  if (detail.value.controlStatus === 'PAUSED') {
    return '恢复会重新交还给系统自动推进；如果只是想先冻结状态，不需要强制重跑。'
  }
  if (detail.value.status === 'BLOCKED') {
    return '强制重跑用于重新验证当前阻塞是否为偶发问题；如果结果仍阻塞，应查看下方结论卡里的剩余问题。'
  }
  if (detail.value.status === 'FAILED') {
    return '失败任务优先看重跑结论；暂停只用于防止它继续被自动推进。'
  }
  return '暂停用于临时冻结任务，恢复用于重新交还自动推进，强制重跑用于重新验证当前处理结果。'
})

const dominantReviewTriggerLabel = computed(() => {
  const item = normalizeObject(dashboardHighlights.value.dominantReviewTrigger)
  return item.key ? `${item.key} (${item.count || 0})` : '暂无'
})
const dominantContractCompatibilityLabel = computed(() => {
  const item = toEntries(overview.value.contractCompatibilityCounts)
    .sort((a, b) => (Number(b.count) || 0) - (Number(a.count) || 0))[0]
  return item?.key ? `${contractCompatibilityLabel(item.key)} (${item.count || 0})` : '暂无'
})
const dominantContractErrorLabel = computed(() => {
  const item = topContractValidationErrors.value[0]
  return item?.key ? `${item.key} (${item.count || 0})` : '暂无'
})
const contractCompatibilitySummary = computed(() => {
  const items = toEntries(overview.value.contractCompatibilityCounts)
    .sort((a, b) => (Number(b.count) || 0) - (Number(a.count) || 0))
    .slice(0, 3)
  if (items.length === 0) return '暂无'
  return items.map(item => `${contractCompatibilityLabel(item.key)} (${item.count || 0})`).join(' / ')
})

const actionRequiredSummary = computed(() => {
  const ids = normalizeArray(dashboardHighlights.value.actionRequiredTaskIds)
  if (ids.length === 0) return '暂无'
  return ids.slice(0, 3).join(' / ') + (ids.length > 3 ? ` 等 ${ids.length} 项` : '')
})

const unstableAgentSummary = computed(() => {
  const agents = normalizeArray(dashboardHighlights.value.unstableAgents)
  if (agents.length === 0) return '暂无'
  return agents.slice(0, 2).map(item => item.agentName || item.agent || '--').join(' / ')
})
const sortLabel = computed(() => {
  if (filters.sortBy === 'updatedAt') return '最近更新'
  if (filters.sortBy === 'nextRunAt') return '最近运行'
  if (filters.sortBy === 'failureStreak') return '失败连续次数'
  return '运维优先级'
})

const filteredCards = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  const result = normalizeArray(cards.value).filter((item) => {
    if (!keyword) return true
    const text = [item.taskId, item.sessionId, item.goal].map(v => String(v || '').toLowerCase()).join(' ')
    return text.includes(keyword)
  })
  return result.slice().sort(compareTaskCard)
})
const filteredTimeline = computed(() => {
  const operatorKeyword = timelineFilters.operator.trim().toLowerCase()
  const reasonKeyword = timelineFilters.reason.trim().toLowerCase()
  return detailTimeline.value.filter((item) => {
    const stateLabel = item?.stateLabel || ''
    if (timelineFilters.stateLabel === 'other' && (stateLabel === 'awaiting_review' || stateLabel === 'review_acked')) {
      return false
    }
    if (timelineFilters.stateLabel && timelineFilters.stateLabel !== 'other' && stateLabel !== timelineFilters.stateLabel) {
      return false
    }
    if (operatorKeyword && !String(item?.operator || '').toLowerCase().includes(operatorKeyword)) {
      return false
    }
    if (reasonKeyword) {
      const text = [item?.reason, item?.displaySummary, item?.summary, item?.message]
        .map(v => String(v || '').toLowerCase())
        .join(' ')
      if (!text.includes(reasonKeyword)) {
        return false
      }
    }
    return true
  })
})

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  hourlyTrendChart.value?.dispose()
  failureCategoryChart.value?.dispose()
  hourlyTrendChart.value = null
  failureCategoryChart.value = null
})

watch(recentHourlyTrend, async () => {
  await nextTick()
  renderHourlyTrendChart()
}, { deep: true })

watch(recentTopFailureCategories, async () => {
  await nextTick()
  renderFailureCategoryChart()
}, { deep: true })

watch(loadError, async (value) => {
  if (value) {
    await nextTick()
    renderHourlyTrendChart()
    renderFailureCategoryChart()
  }
})

async function loadDashboard() {
  loading.value = true
  loadError.value = ''
  try {
    const [overviewData, filtersData, cardsData] = await Promise.all([
      adminApi.getOrchestrationOverview(),
      adminApi.getOrchestrationFilters(),
      adminApi.getOrchestrationCards(buildCardQuery())
    ])
    overview.value = normalizeObject(overviewData)
    filterOptions.value = normalizeObject(filtersData)
    cards.value = normalizeArray(cardsData)
    await syncSelectedDetail()
  } catch (error) {
    loadError.value = error.response?.data?.message || '任务治理面板加载失败'
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
    await nextTick()
    renderHourlyTrendChart()
    renderFailureCategoryChart()
  }
}

async function loadCards() {
  loading.value = true
  loadError.value = ''
  try {
    cards.value = normalizeArray(await adminApi.getOrchestrationCards(buildCardQuery()))
    await syncSelectedDetail()
  } catch (error) {
    loadError.value = error.response?.data?.message || '加载任务卡片失败'
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function buildCardQuery() {
  const contractValidationState = filters.contractValidationState || ''
  return {
    controlStatus: filters.controlStatus || undefined,
    reviewTrigger: filters.reviewTrigger || undefined,
    taskType: filters.taskType || undefined,
    circuitBreakerOnly: filters.circuitBreakerOnly || undefined,
    contractCompatibility: filters.contractCompatibility || undefined,
    contractValidated: contractValidationState === 'validated'
      ? true
      : contractValidationState === 'invalid'
        ? false
        : undefined,
    contractErrorsOnly: contractValidationState === 'errors_only' ? true : undefined
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.controlStatus = ''
  filters.reviewTrigger = ''
  filters.taskType = ''
  filters.contractCompatibility = ''
  filters.contractValidationState = ''
  filters.sortBy = 'priority'
  filters.circuitBreakerOnly = false
  loadCards()
}

async function syncSelectedDetail() {
  if (!cards.value.length) {
    detail.value = {}
    return
  }
  const routeTaskId = String(route.query.taskId || '')
  const targetTaskId = routeTaskId && cards.value.some(item => item.taskId === routeTaskId)
    ? routeTaskId
    : (detail.value.taskId && cards.value.some(item => item.taskId === detail.value.taskId)
      ? detail.value.taskId
      : cards.value[0].taskId)
  await fetchTaskDetail(targetTaskId)
}

async function selectTask(item) {
  if (!item?.taskId) return
  await fetchTaskDetail(item.taskId)
}

async function fetchTaskDetail(taskId) {
  try {
    detail.value = normalizeObject(await adminApi.getOrchestrationTaskDetail(taskId))
    resetTimelineFilters()
    resetAuditFilters()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载任务详情失败')
  }
}

async function triggerTick() {
  tickLoading.value = true
  try {
    const result = await adminApi.triggerOrchestrationTick()
    ElMessage.success(`已触发推进：尝试 ${result.attempted || 0}，待复核 ${result.deferredForManualReview || 0}`)
    await loadDashboard()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '手动推进失败')
  } finally {
    tickLoading.value = false
  }
}

async function runTaskAction(type) {
  if (!detail.value.taskId) return
  const actionMap = {
    pause: { title: '暂停自动推进', api: adminApi.pauseOrchestrationTask, needReason: false, success: '后台任务已暂停' },
    resume: { title: '恢复自动推进', api: adminApi.resumeOrchestrationTask, needReason: false, success: '后台任务已恢复推进' },
    ack: { title: '人工复核后继续', api: adminApi.acknowledgeOrchestrationReview, needReason: true, success: '已确认复核，任务恢复自动推进资格' },
    retry: { title: '强制重新执行任务', api: adminApi.retryOrchestrationTask, needReason: true, success: '后台任务已进入重新执行' }
  }
  const config = actionMap[type]
  if (!config) return
  const reason = await promptReason(config.title, config.needReason)
  if (reason === null) return
  actionLoading.value = type
  try {
    await config.api(detail.value.taskId, reason || undefined)
    ElMessage.success(config.success)
    await loadDashboard()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || `${config.title}失败`)
  } finally {
    actionLoading.value = ''
  }
}

async function runSubTaskAction(item, type) {
  if (!detail.value.taskId || !item?.subTaskId) return
  try {
    if (type === 'reset') {
      const reason = await promptReason('重置子任务', false)
      if (reason === null) return
      await adminApi.resetOrchestrationSubTask(detail.value.taskId, item.subTaskId, reason || undefined)
      ElMessage.success('子任务已重置')
    } else if (type === 'skip') {
      const reason = await promptReason('跳过子任务', true)
      if (reason === null) return
      await adminApi.skipOrchestrationSubTask(detail.value.taskId, item.subTaskId, reason)
      ElMessage.success('子任务已跳过')
    } else if (type === 'handoff') {
      const reason = await promptReason('转派子任务', true)
      if (reason === null) return
      const targetAgent = item.recommendedAgent || ''
      if (!targetAgent) {
        ElMessage.warning('当前没有推荐服务，无法直接转派')
        return
      }
      await adminApi.handoffOrchestrationSubTask(detail.value.taskId, item.subTaskId, targetAgent, reason)
      ElMessage.success(`子任务已转派给 ${targetAgent}`)
    } else if (type === 'capability') {
      const capability = normalizeArray(item.recentMissingCapabilities)[0]
      if (!capability) {
        ElMessage.warning('当前没有可用于补位的缺失能力')
        return
      }
      const reason = await promptReason(`按能力补位：${capability}`, true)
      if (reason === null) return
      await adminApi.handoffOrchestrationSubTaskByCapability(detail.value.taskId, item.subTaskId, capability, reason)
      ElMessage.success(`已按能力 ${capability} 进行补位`)
    }
    await loadDashboard()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '子任务操作失败')
  }
}

function canRunSubTaskAction(item, type) {
  const status = String(item?.status || '')
  if (!item?.subTaskId) return false
  if (type === 'reset') {
    return status !== 'RUNNING'
  }
  if (type === 'skip') {
    return status !== 'SKIPPED' && status !== 'COMPLETED'
  }
  if (type === 'handoff') {
    return Boolean(item?.recommendedAgent) && status !== 'COMPLETED'
  }
  if (type === 'capability') {
    return normalizeArray(item?.recentMissingCapabilities).length > 0 && status !== 'COMPLETED'
  }
  return false
}

function subTaskOperationHint(item) {
  const status = String(item?.status || '--')
  if (status === 'SKIPPED') {
    return '该子任务已被人工跳过。若需要重新纳入处理流程，可执行“重置”。'
  }
  if (status === 'BLOCKED') {
    return '当前子任务处于阻塞状态。可直接跳过、按推荐服务转派，或按缺失能力补位。'
  }
  if (status === 'READY' || status === 'PENDING') {
    return '当前子任务已具备继续执行条件。若目标服务不合适，可提前转派或补位。'
  }
  return '运维动作会写入审计时间线，并在下一轮系统推进时生效。'
}

async function promptReason(title, required) {
  try {
    const { value } = await ElMessageBox.prompt(required ? '请填写操作原因' : '可选填写操作原因', title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: required ? '请输入原因' : '可留空',
      inputValidator: required ? (val) => (String(val || '').trim() ? true : '原因不能为空') : undefined
    })
    return String(value || '').trim()
  } catch {
    return null
  }
}

function watchdogDecisionLabel(value) {
  if (value === 'await_manual_review') return '等待人工复核'
  if (value === 'recommend_manual_review') return '建议人工复核'
  if (value === 'auto_advance') return '允许自动推进'
  return '--'
}

function agentDisplayName(item) {
  return item?.resolvedAgentName || item?.agentName || item?.agent || '--'
}

function formatRatio(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return '--'
  }
  return `${Math.round(numeric * 100)}%`
}

function agentHealthTagType(item) {
  const successRate = Number(item?.successRate)
  const stabilityScore = Number(item?.stabilityScore)
  if (item?.healthState === 'quarantined') {
    return 'danger'
  }
  if (item?.healthState === 'degraded') {
    return 'warning'
  }
  if (item?.healthState === 'healthy') {
    return 'success'
  }
  if ((Number.isFinite(successRate) && successRate < 0.5) || (Number.isFinite(stabilityScore) && stabilityScore < 0.2)) {
    return 'danger'
  }
  if ((Number.isFinite(successRate) && successRate < 0.75) || (Number.isFinite(stabilityScore) && stabilityScore < 0.4)) {
    return 'warning'
  }
  return 'success'
}

function agentHealthStateLabel(value) {
  if (value === 'healthy') return '健康'
  if (value === 'degraded') return '已降级'
  if (value === 'quarantined') return '已隔离'
  return '待观察'
}

function topSignalLabel(items) {
  const first = normalizeArray(items)[0]
  if (!first?.key) {
    return '--'
  }
  return `${first.key} (${first.count || 0})`
}

function contractCompatibilityLabel(value, validated) {
  if (validated === true && (!value || value === 'compatible')) return '契约已验证'
  if (value === 'compatible') return '契约兼容'
  if (value === 'schema_only') return 'schema 兼容'
  if (value === 'version_only') return 'version 兼容'
  if (value === 'incompatible') return '契约不兼容'
  if (validated === false) return '契约异常'
  return '契约未知'
}

function contractCompatibilityTag(value, validated) {
  if (validated === true && (!value || value === 'compatible')) return 'success'
  if (value === 'schema_only' || value === 'version_only') return 'warning'
  if (value === 'incompatible' || validated === false) return 'danger'
  return 'info'
}

function controlStatusTag(value) {
  if (value === 'AWAITING_REVIEW') return 'warning'
  if (value === 'PAUSED') return 'info'
  if (value === 'CANCELLED') return 'danger'
  return 'success'
}

function formatTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function normalizeArray(value) {
  return Array.isArray(value) ? value : []
}

function normalizeObject(value) {
  return value && typeof value === 'object' ? value : {}
}

function toEntries(value) {
  if (Array.isArray(value)) return value
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).map(([key, count]) => ({ key, count }))
}

function toFixed(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(2) : '--'
}

function compareTaskCard(left, right) {
  const sortBy = filters.sortBy
  if (sortBy === 'updatedAt') {
    return compareDateDesc(left?.updatedAt, right?.updatedAt) || comparePriority(left, right)
  }
  if (sortBy === 'nextRunAt') {
    return compareDateDesc(left?.nextRunAt, right?.nextRunAt) || comparePriority(left, right)
  }
  if (sortBy === 'failureStreak') {
    return compareNumberDesc(left?.watchdogFailureStreak, right?.watchdogFailureStreak) || comparePriority(left, right)
  }
  return comparePriority(left, right) || compareDateDesc(left?.updatedAt, right?.updatedAt)
}

function comparePriority(left, right) {
  return compareNumberDesc(taskPriorityScore(left), taskPriorityScore(right))
}

function taskPriorityScore(item) {
  if (!item || typeof item !== 'object') return 0
  let score = 0
  if (item.circuitBreakerOpen) score += 1000
  if (item.controlStatus === 'AWAITING_REVIEW') score += 800
  if (item.manualReviewRecommended) score += 500
  if (item.controlStatus === 'PAUSED') score += 250
  if (item.latestControlAction) score += 40
  score += (Number(item.watchdogFailureStreak) || 0) * 10
  score += normalizeArray(item.reviewTriggers).length * 5
  return score
}

function compareNumberDesc(left, right) {
  return (Number(right) || 0) - (Number(left) || 0)
}

function compareDateDesc(left, right) {
  return toTimestamp(right) - toTimestamp(left)
}

function toTimestamp(value) {
  const time = new Date(value || 0).getTime()
  return Number.isFinite(time) ? time : 0
}

function percent(value) {
  const num = Number(value)
  return Number.isFinite(num) ? `${(num * 100).toFixed(0)}%` : '--'
}

function hourTrendTotal(item) {
  if (!item || typeof item !== 'object') return 0
  return Object.entries(item)
    .filter(([key]) => key !== 'bucket' && key !== 'hour' && key !== 'key')
    .reduce((sum, [, value]) => sum + (Number(value) || 0), 0)
}

function formatHourLabel(value) {
  const text = String(value || '')
  if (!text) return '--'
  return text.length >= 13 ? text.slice(11, 13) + ':00' : text
}

function resetTimelineFilters() {
  timelineFilters.stateLabel = ''
  timelineFilters.operator = ''
  timelineFilters.reason = ''
}

function resetAuditFilters() {
  auditFilters.actionType = ''
  auditFilters.operator = ''
  auditFilters.keyword = ''
}

function timelineTagType(item) {
  const stateLabel = String(item?.stateLabel || '')
  if (stateLabel === 'awaiting_review') return 'warning'
  if (stateLabel === 'review_acked') return 'success'
  const type = String(item?.type || '').toLowerCase()
  if (type.includes('fail') || type.includes('cancel')) return 'danger'
  if (type.includes('pause') || type.includes('skip')) return 'info'
  return 'primary'
}

function timelineStateText(item) {
  const stateLabel = String(item?.stateLabel || '')
  if (stateLabel === 'awaiting_review') return '待复核'
  if (stateLabel === 'review_acked') return '已确认'
  return '普通事件'
}

function timelineItemClass(item) {
  const stateLabel = String(item?.stateLabel || '')
  if (stateLabel === 'awaiting_review') return 'timeline-item--warning'
  if (stateLabel === 'review_acked') return 'timeline-item--success'
  const type = String(item?.type || '').toLowerCase()
  if (type.includes('fail') || type.includes('cancel')) return 'timeline-item--danger'
  return 'timeline-item--neutral'
}

function isAuditEvent(item) {
  const type = String(item?.type || '').toUpperCase()
  return [
    'MANUAL_PAUSED',
    'MANUAL_RESUMED',
    'MANUAL_REVIEW_ACKED',
    'MANUAL_CANCELLED',
    'MANUAL_RETRIED',
    'SUBTASK_SKIPPED',
    'SUBTASK_RESET',
    'SUBTASK_HANDOFF'
  ].includes(type)
}

function formatAuditType(value) {
  const type = String(value || '').toUpperCase()
  if (type === 'TASK_PAUSED' || type === 'MANUAL_PAUSED') return '暂停'
  if (type === 'TASK_RESUMED' || type === 'MANUAL_RESUMED') return '恢复'
  if (type === 'MANUAL_REVIEW_ACKED') return '复核后继续'
  if (type === 'TASK_CANCELLED' || type === 'MANUAL_CANCELLED') return '取消'
  if (type === 'TASK_RETRIED' || type === 'MANUAL_RETRIED') return '重跑'
  if (type === 'SUB_TASK_SKIPPED' || type === 'SUBTASK_SKIPPED') return '跳过子任务'
  if (type === 'SUB_TASK_RESET' || type === 'SUBTASK_RESET') return '重置子任务'
  if (type === 'SUB_TASK_HANDOFF' || type === 'SUBTASK_HANDOFF') return '转派子任务'
  return value || '--'
}

function auditTagType(value) {
  const type = String(value || '').toUpperCase()
  if (type === 'MANUAL_PAUSED') return 'warning'
  if (type === 'MANUAL_RESUMED' || type === 'MANUAL_REVIEW_ACKED') return 'success'
  if (type === 'MANUAL_CANCELLED' || type === 'MANUAL_RETRIED') return 'danger'
  return 'primary'
}

function controlOutcomeTagType(value) {
  if (value === 'completed') return 'success'
  if (value === 'blocked') return 'warning'
  if (value === 'failed' || value === 'cancelled') return 'danger'
  if (value === 'paused') return 'info'
  return 'primary'
}

function controlOutcomeText(value) {
  if (value === 'completed') return '已恢复完成'
  if (value === 'blocked') return '仍需处理'
  if (value === 'failed') return '处理未成功'
  if (value === 'running') return '正在恢复中'
  if (value === 'paused') return '等待恢复'
  if (value === 'cancelled') return '已停止处理'
  return '已记录结果'
}

function renderHourlyTrendChart() {
  if (!hourlyTrendChartRef.value || recentHourlyTrend.value.length === 0) {
    hourlyTrendChart.value?.dispose()
    hourlyTrendChart.value = null
    return
  }
  if (!hourlyTrendChart.value) {
    hourlyTrendChart.value = echarts.init(hourlyTrendChartRef.value)
  }
  const xAxis = recentHourlyTrend.value.map(item => formatHourLabel(item.bucket || item.hour || item.key))
  const totals = recentHourlyTrend.value.map(item => hourTrendTotal(item))
  hourlyTrendChart.value.setOption({
    animationDuration: 400,
    color: ['#2563eb'],
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: 12,
      right: 12,
      top: 18,
      bottom: 12,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: xAxis,
      axisLabel: {
        color: '#64748b',
        fontSize: 11
      },
      axisLine: {
        lineStyle: { color: 'rgba(148, 163, 184, 0.4)' }
      }
    },
    yAxis: {
      type: 'value',
      splitLine: {
        lineStyle: { color: 'rgba(148, 163, 184, 0.18)' }
      },
      axisLabel: {
        color: '#64748b',
        fontSize: 11
      }
    },
    series: [{
      name: '控制事件',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: totals,
      areaStyle: {
        color: 'rgba(37, 99, 235, 0.12)'
      },
      lineStyle: {
        width: 2
      }
    }]
  }, true)
}

function renderFailureCategoryChart() {
  if (!failureCategoryChartRef.value || recentTopFailureCategories.value.length === 0) {
    failureCategoryChart.value?.dispose()
    failureCategoryChart.value = null
    return
  }
  if (!failureCategoryChart.value) {
    failureCategoryChart.value = echarts.init(failureCategoryChartRef.value)
  }
  failureCategoryChart.value.setOption({
    animationDuration: 400,
    color: ['#f59e0b'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: 12,
      right: 12,
      top: 10,
      bottom: 12,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      splitLine: {
        lineStyle: { color: 'rgba(148, 163, 184, 0.18)' }
      },
      axisLabel: {
        color: '#64748b',
        fontSize: 11
      }
    },
    yAxis: {
      type: 'category',
      data: recentTopFailureCategories.value.map(item => item.key),
      axisLabel: {
        color: '#475569',
        fontSize: 11
      },
      axisLine: {
        lineStyle: { color: 'rgba(148, 163, 184, 0.4)' }
      }
    },
    series: [{
      name: '失败次数',
      type: 'bar',
      barWidth: 16,
      data: recentTopFailureCategories.value.map(item => item.count),
      itemStyle: {
        borderRadius: [0, 8, 8, 0]
      }
    }]
  }, true)
}

function resizeCharts() {
  hourlyTrendChart.value?.resize()
  failureCategoryChart.value?.resize()
}
</script>

<style scoped>
.orchestration-page {
  padding-bottom: 6px;
}

.orchestration-alert {
  border-radius: 14px;
}

.orchestration-grid {
  grid-template-columns: minmax(0, 1.38fr) minmax(360px, 0.92fr);
}

.toolbar-keyword {
  flex: 1;
  min-width: 240px;
}

.toolbar-role {
  width: 180px;
}

.toolbar-short {
  width: 160px;
}

.highlight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.agent-health-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.highlight-card,
.signal-panel,
.subtask-card,
.timeline-item,
.task-card {
  border-radius: 16px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.84), rgba(255, 255, 255, 0.98));
}

.highlight-card {
  padding: 14px;
}

.highlight-card strong {
  color: #0f172a;
}

.highlight-card p {
  margin: 8px 0 0;
  line-height: 1.6;
  color: #475569;
  font-size: 12px;
}

.signal-grid {
  display: grid;
  gap: 12px;
}

.trend-stack {
  display: grid;
  gap: 12px;
}

.signal-panel {
  padding: 14px;
}

.trend-panel {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.84), rgba(255, 255, 255, 0.98));
}

.signal-panel__title,
.detail-section h3 {
  margin: 0 0 10px;
  font-size: 13px;
  color: #334155;
}

.signal-list,
.detail-stack,
.subtask-list,
.timeline-list,
.agent-health-list {
  display: grid;
  gap: 10px;
}

.trend-chart {
  height: 188px;
  width: 100%;
}

.trend-chart--short {
  height: 220px;
}

.signal-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: #475569;
}

.signal-row--stack {
  align-items: flex-start;
}

.signal-row strong {
  color: #0f172a;
}

.agent-health-card {
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: rgba(255, 255, 255, 0.92);
}

.agent-health-card__head,
.agent-health-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.agent-health-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.agent-health-card__meta {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-head p,
.subtask-card__desc,
.task-card__summary {
  margin: 6px 0 0;
  line-height: 1.7;
  color: #475569;
  font-size: 12px;
}

.detail-meta,
.task-card__meta,
.task-card__audit,
.task-card__foot,
.timeline-item__meta,
.timeline-item__head,
.subtask-card__meta,
.subtask-card__actions,
.detail-actions,
.timeline-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-meta,
.task-card__meta,
.task-card__audit,
.task-card__foot,
.timeline-item__meta,
.subtask-card__meta {
  color: #64748b;
  font-size: 12px;
}

.task-card__audit {
  margin-top: 8px;
  align-items: center;
}

.subtask-card__actions {
  margin-top: 10px;
}

.subtask-card__hint {
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.65;
}

.task-card__audit-reason {
  margin-top: 8px;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
}

.detail-status,
.detail-section {
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  padding-top: 12px;
}

.detail-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.9fr);
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
}

.detail-panel {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.84), rgba(255, 255, 255, 0.98));
}

.detail-panel__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.detail-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.detail-panel__head strong {
  color: #0f172a;
  font-size: 16px;
}

.detail-panel__microcopy,
.detail-panel__desc,
.detail-panel__note {
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.detail-panel__desc {
  margin: 10px 0 0;
}

.detail-panel__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.detail-panel__metric {
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.detail-panel__metric span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.detail-panel__metric strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.6;
}

.detail-panel__note {
  margin-top: 12px;
}

.detail-panel__note--warning {
  color: #b45309;
}

.detail-actions--stacked {
  margin-top: 14px;
}

.audit-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.audit-summary-card,
.audit-item {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.84), rgba(255, 255, 255, 0.98));
}

.audit-summary-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.audit-summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 18px;
}

.audit-summary-card p,
.audit-item__text {
  margin: 8px 0 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.7;
}

.contract-error-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.contract-error-item {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(220, 38, 38, 0.16);
  background: linear-gradient(180deg, rgba(254, 242, 242, 0.96), rgba(255, 255, 255, 0.98));
  color: #991b1b;
  font-size: 12px;
  line-height: 1.65;
}

.audit-toolbar,
.audit-list,
.audit-item__title,
.audit-item__head,
.audit-item__meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.audit-toolbar,
.audit-list {
  margin-top: 12px;
}

.audit-list {
  display: grid;
}

.audit-item__head {
  align-items: center;
  justify-content: space-between;
}

.audit-item__title {
  align-items: center;
}

.audit-item__meta {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.control-outcome-card {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.84), rgba(255, 255, 255, 0.98));
  display: grid;
  gap: 10px;
}

.control-outcome-card--completed {
  border-color: rgba(22, 163, 74, 0.34);
  background: linear-gradient(180deg, rgba(236, 253, 245, 1), rgba(255, 255, 255, 0.98));
  box-shadow: inset 0 0 0 1px rgba(34, 197, 94, 0.08);
}

.control-outcome-card--blocked {
  border-color: rgba(217, 119, 6, 0.34);
  background: linear-gradient(180deg, rgba(255, 247, 237, 1), rgba(255, 255, 255, 0.98));
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.08);
}

.control-outcome-card--failed,
.control-outcome-card--cancelled {
  border-color: rgba(220, 38, 38, 0.3);
  background: linear-gradient(180deg, rgba(254, 242, 242, 1), rgba(255, 255, 255, 0.98));
  box-shadow: inset 0 0 0 1px rgba(220, 38, 38, 0.08);
}

.control-outcome-card__head,
.control-outcome-card__timeline-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.control-outcome-card__head strong {
  display: block;
  color: #0f172a;
}

.control-outcome-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.control-outcome-card__chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.22);
  font-size: 12px;
  color: #475569;
}

.control-outcome-card__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.control-outcome-card__stat {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.control-outcome-card__stat span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.control-outcome-card__stat strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
  color: #0f172a;
}

.control-outcome-card__head p,
.control-outcome-card__reason,
.control-outcome-card__next,
.control-outcome-card__timeline-item span {
  margin: 6px 0 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.65;
}

.control-outcome-card__reason--warning {
  color: #9a6700;
}

.control-outcome-card__timeline {
  display: grid;
  gap: 8px;
}

.control-outcome-card__timeline-item {
  padding-top: 8px;
  border-top: 1px dashed rgba(148, 163, 184, 0.28);
}

.subtask-card,
.timeline-item,
.task-card {
  padding: 14px;
}

.timeline-toolbar {
  margin-bottom: 10px;
}

.subtask-card__head,
.task-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.subtask-card__chips,
.task-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.task-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.task-card {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.task-card:hover,
.task-card--active {
  transform: translateY(-1px);
  box-shadow: 0 14px 28px rgba(51, 65, 85, 0.1);
}

.task-card--active {
  border-color: rgba(37, 99, 235, 0.32);
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.92), rgba(255, 255, 255, 0.98));
}

.task-card--danger {
  border-color: rgba(220, 38, 38, 0.2);
  background: linear-gradient(180deg, rgba(254, 242, 242, 0.96), rgba(255, 255, 255, 0.98));
}

.timeline-item--warning {
  border-color: rgba(245, 158, 11, 0.28);
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.98), rgba(255, 255, 255, 0.98));
}

.timeline-item--success {
  border-color: rgba(34, 197, 94, 0.24);
  background: linear-gradient(180deg, rgba(240, 253, 244, 0.98), rgba(255, 255, 255, 0.98));
}

.timeline-item--danger {
  border-color: rgba(220, 38, 38, 0.22);
  background: linear-gradient(180deg, rgba(254, 242, 242, 0.98), rgba(255, 255, 255, 0.98));
}

.timeline-item--neutral {
  border-color: rgba(148, 163, 184, 0.18);
}

.timeline-item__reason {
  margin-top: 8px;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 1320px) {
  .orchestration-grid,
  .detail-overview-grid,
  .highlight-grid,
  .agent-health-grid,
  .audit-summary-grid,
  .control-outcome-card__stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .detail-panel__grid,
  .toolbar-keyword,
  .toolbar-role,
  .toolbar-short {
    width: 100%;
  }

  .detail-panel__grid {
    grid-template-columns: 1fr;
  }
}
</style>
