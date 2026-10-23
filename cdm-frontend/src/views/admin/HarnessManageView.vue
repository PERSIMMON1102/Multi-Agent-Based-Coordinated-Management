<template>
  <div class="admin-page harness-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">System Diagnostics</span>
        <h1 class="admin-hero__title">系统诊断看板</h1>
        <p class="admin-hero__desc">
          聚合对话轨迹、规则执行与处理记录，用于管理员快速定位边界异常、证据覆盖和流程可见性问题。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round type="primary">最近 {{ query.days }} 天</el-tag>
          <el-tag effect="plain" round :type="statusTagType(overallScore)">总分 {{ overallScore }}</el-tag>
          <el-tag effect="plain" round>{{ summaryStatusLabel }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="loading" @click="loadOverview">刷新看板</el-button>
      </div>
    </section>

    <section class="harness-switcher">
      <el-segmented v-model="activeSection" :options="sectionOptions" class="harness-switcher__control" />
      <p class="harness-switcher__hint">
        {{ activeSection === 'overview' ? '先看整体评分、护栏和推荐案例。' : '聚焦轨迹、规则日志和处理消息明细。' }}
      </p>
    </section>

    <el-alert
      v-if="loadError"
      class="harness-error-alert"
      type="error"
      :closable="false"
      :title="loadError"
      show-icon
    >
      <template #default>
        <el-button size="small" type="danger" plain :loading="loading" @click="loadOverview">
          重新加载
        </el-button>
      </template>
    </el-alert>

    <div v-show="activeSection === 'overview'" class="harness-pane">
      <section class="admin-stat-grid admin-stat-grid--four harness-stat-grid">
        <article class="admin-stat-card admin-stat-card--blue">
          <span>会话数</span>
          <strong>{{ summary.sessionCount }}</strong>
          <p>统计窗口内纳入分析的会话总数。</p>
        </article>
        <article class="admin-stat-card admin-stat-card--green">
          <span>轨迹覆盖</span>
          <strong>{{ formatPercent(summary.traceCoverageRate) }}</strong>
          <p>助手回复中带处理过程记录的覆盖比例。</p>
        </article>
        <article class="admin-stat-card admin-stat-card--orange">
          <span>边界异常</span>
          <strong>{{ summary.boundedViolationCount }}</strong>
          <p>超过预设执行边界或步骤顺序异常的轨迹数。</p>
        </article>
        <article class="admin-stat-card admin-stat-card--red">
          <span>高风险轨迹</span>
          <strong>{{ summary.highRiskTraceCount }}</strong>
          <p>被高风险安全标记命中的轨迹数量。</p>
        </article>
      </section>

      <section class="admin-grid admin-grid--sidebar">
        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head">
              <div>
                <span class="admin-section-head__eyebrow">Query</span>
                <h2>统计窗口设置</h2>
                <p>手动控制统计窗口和返回行数，避免自动触发重复请求。</p>
              </div>
            </div>
          </template>

          <div class="query-layout">
            <el-form label-width="124px" class="query-form">
              <el-form-item label="最近天数">
                <el-input-number v-model="query.days" :min="1" :max="180" :step="1" />
              </el-form-item>
              <el-form-item label="轨迹行数">
                <el-input-number v-model="query.traceLimit" :min="4" :max="30" :step="1" />
              </el-form-item>
              <el-form-item label="规则日志行数">
                <el-input-number v-model="query.ruleLimit" :min="4" :max="30" :step="1" />
              </el-form-item>
              <el-form-item label="消息日志行数">
                <el-input-number v-model="query.messageLimit" :min="4" :max="30" :step="1" />
              </el-form-item>
              <el-form-item class="query-form__actions">
                <div class="query-actions">
                  <el-button :loading="loading" @click="resetQuery">重置参数</el-button>
                  <el-button type="primary" :loading="loading" @click="loadOverview">应用并刷新</el-button>
                </div>
              </el-form-item>
            </el-form>

            <div class="query-status-panel">
              <div class="admin-status-stack">
                <div class="admin-status-line">
                  <span>最新轨迹时间</span>
                  <span>{{ formatTime(summary.latestTraceAt) }}</span>
                </div>
                <div class="admin-status-line">
                  <span>最新规则时间</span>
                  <span>{{ formatTime(summary.latestRuleExecutionAt) }}</span>
                </div>
                <div class="admin-status-line">
                  <span>最新消息时间</span>
                  <span>{{ formatTime(summary.latestAgentMessageAt) }}</span>
                </div>
                <div class="admin-status-item">工具种类：{{ summary.uniqueToolCount }}</div>
                <div class="admin-status-item">平均步骤数：{{ summary.averageTraceSteps }}</div>
                <div class="admin-status-item">平均工具数：{{ summary.averageToolCount }}</div>
              </div>
            </div>
          </div>
        </el-card>

        <div class="admin-aside-stack">
          <el-card shadow="never" class="admin-card">
            <template #header>
              <div class="admin-section-head admin-section-head--compact">
                <div>
                  <span class="admin-section-head__eyebrow">Evaluation</span>
                  <h2>评分卡</h2>
                  <p>分维度展示可观测性、边界约束、证据附着和协同留痕。</p>
                </div>
              </div>
            </template>
            <div class="score-grid">
              <article v-for="item in scorecards" :key="item.key" class="score-item">
                <div class="score-item__head">
                  <strong>{{ item.title }}</strong>
                  <el-tag size="small" :type="statusTagType(item.score)">{{ item.status }}</el-tag>
                </div>
                <div class="score-item__score">{{ item.score }}</div>
                <div class="score-item__signal">{{ item.signal || '--' }}</div>
              </article>
            </div>
            <div class="eval-coverage">
              <h3>阶段覆盖</h3>
              <div class="eval-coverage-list">
                <div v-for="item in stageCoverage" :key="item.key" class="eval-coverage-item">
                  <div class="eval-coverage-item__head">
                    <strong>{{ item.title }}</strong>
                    <span>{{ formatPercent(item.rate) }}</span>
                  </div>
                  <el-progress :percentage="toNumber(item.rate)" :stroke-width="6" :show-text="false" />
                  <div class="eval-coverage-item__desc">
                    {{ item.count ?? 0 }} / {{ item.total ?? 0 }} · {{ item.description || '--' }}
                  </div>
                </div>
                <el-empty v-if="stageCoverage.length === 0" description="暂无阶段覆盖数据" />
              </div>
            </div>
            <div class="eval-scenario">
              <h3>场景拆解</h3>
              <div class="eval-scenario-list">
                <div v-for="item in scenarioBreakdown" :key="item.key" class="eval-scenario-item">
                  <div class="eval-scenario-item__head">
                    <strong>{{ item.title || item.key }}</strong>
                    <el-tag size="small" :type="statusTagType(item.score)">{{ item.status || '--' }}</el-tag>
                  </div>
                  <div class="eval-scenario-item__meta">
                    <span>轨迹 {{ item.traceCount ?? 0 }}</span>
                    <span>高风险 {{ item.highRiskTraceCount ?? 0 }}</span>
                    <span>边界异常 {{ item.boundedViolationCount ?? 0 }}</span>
                  </div>
                  <div class="eval-scenario-item__desc">{{ item.recommendation || '--' }}</div>
                </div>
                <el-empty v-if="scenarioBreakdown.length === 0" description="暂无场景拆解数据" />
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="admin-card trace-detail-card">
            <template #header>
              <div class="admin-section-head admin-section-head--compact">
                <div>
                  <span class="admin-section-head__eyebrow">Guardrails</span>
                  <h2>边界护栏</h2>
                  <p>对关键执行上限和短路策略进行健康度检查。</p>
                </div>
              </div>
            </template>
            <div class="admin-status-stack">
              <div v-for="item in guardrails" :key="item.key" class="guardrail-item">
                <div class="guardrail-item__row">
                  <strong>{{ item.title }}</strong>
                  <el-tag size="small" :type="item.healthy ? 'success' : 'danger'">
                    {{ item.healthy ? '健康' : '关注' }}
                  </el-tag>
                </div>
                <div class="guardrail-item__text">约束：{{ item.limit || '--' }}</div>
                <div class="guardrail-item__text">信号：{{ item.signal || '--' }}</div>
              </div>
              <el-empty v-if="guardrails.length === 0" description="暂无护栏数据" />
            </div>
          </el-card>
        </div>
      </section>

      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Process Collaboration</span>
              <h2>处理协作视图</h2>
              <p>把最近的处理消息流整理成协作视图，用于展示谁在发起、谁在接收、处理关系是否稳定。</p>
            </div>
          </div>
        </template>
        <div class="agent-network-grid">
          <div class="agent-network-panel">
            <div class="agent-network-panel__title">参与环节</div>
            <div v-if="agentNetworkNodes.length > 0" class="agent-network-node-list">
              <div v-for="node in agentNetworkNodes" :key="node.name" class="agent-network-node">
                <strong>{{ node.name }}</strong>
                <span>收发 {{ node.messageCount }} 次</span>
              </div>
            </div>
            <el-empty v-else description="暂无环节数据" :image-size="72" />
          </div>
          <div class="agent-network-panel">
            <div class="agent-network-panel__title">协作关系</div>
            <div v-if="agentNetworkEdges.length > 0" class="agent-network-edge-list">
              <article v-for="edge in agentNetworkEdges" :key="edge.key" class="agent-network-edge">
                <div class="agent-network-edge__head">
                  <strong>{{ edge.sender }} → {{ edge.receiver }}</strong>
                  <el-tag size="small" type="primary" effect="plain">{{ edge.count }} 次</el-tag>
                </div>
                <div class="agent-network-edge__meta">
                  <span>最近类型 {{ edge.latestPerformative || '--' }}</span>
                  <span>{{ formatTime(edge.latestCreatedAt) }}</span>
                </div>
                <div class="agent-network-edge__desc">{{ edge.preview || '暂无消息摘要' }}</div>
              </article>
            </div>
            <el-empty v-else description="暂无协作关系数据" :image-size="72" />
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="admin-card admin-card--table">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Recommended Cases</span>
              <h2>推荐复盘案例</h2>
              <p>用于复盘高风险拦截、证据支撑和边界治理场景。</p>
            </div>
          </div>
        </template>
        <div class="admin-toolbar">
          <el-input
            v-model="recommendedFilters.keyword"
            class="toolbar-keyword"
            clearable
            placeholder="筛选案例标题/推荐理由/标签"
          />
          <el-select v-model="recommendedFilters.ownerRoleLabel" clearable placeholder="来源筛选" class="toolbar-role">
            <el-option v-for="item in recommendedRoleOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
        <div v-if="filteredRecommendedCases.length > 0" class="recommended-grid">
          <article v-for="item in filteredRecommendedCases" :key="item.key + '-' + item.messageId" class="recommended-card">
            <div class="recommended-card__head">
              <strong>{{ item.title || '--' }}</strong>
              <el-tag size="small" :type="statusTagType(item.qualityScore)">{{ item.qualityScore ?? '--' }}</el-tag>
            </div>
            <div class="recommended-card__meta">
              <span>{{ item.ownerRoleLabel || '--' }}</span>
              <span>{{ formatTime(item.createdAt) }}</span>
              <span>消息 #{{ item.messageId ?? '--' }}</span>
            </div>
            <div class="recommended-card__reason">{{ item.reason || '--' }}</div>
            <div class="recommended-card__tags">
              <el-tag v-for="tag in normalizeArray(item.tags)" :key="`tag-${item.key}-${tag}`" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </article>
        </div>
        <div v-if="filteredRecommendedCases.length === 0" class="admin-empty-block">
          <el-empty description="暂无推荐案例" />
        </div>
      </el-card>
    </div>

    <section v-show="activeSection === 'details'" class="admin-grid admin-grid--editor">
      <el-card shadow="never" class="admin-card admin-card--table">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Recent Traces</span>
              <h2>最近轨迹</h2>
              <p>查看工具调用、步骤数量、边界状态和证据附着情况。</p>
            </div>
          </div>
        </template>
        <div class="admin-toolbar">
          <el-input
            v-model="traceFilters.keyword"
            class="toolbar-keyword"
            clearable
            placeholder="筛选消息ID/回复摘要/会话标题"
          />
          <el-select v-model="traceFilters.ownerRoleLabel" clearable placeholder="来源筛选" class="toolbar-role">
            <el-option v-for="item in traceRoleOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="traceFilters.boundary" clearable placeholder="边界筛选" class="toolbar-short">
            <el-option label="边界正常" value="normal" />
            <el-option label="边界异常" value="abnormal" />
          </el-select>
          <el-select v-model="traceFilters.evidence" clearable placeholder="证据筛选" class="toolbar-short">
            <el-option label="有证据" value="yes" />
            <el-option label="无证据" value="no" />
          </el-select>
        </div>
        <el-table
          :data="filteredRecentTraces"
          class="admin-data-table"
          border
          height="420"
          v-loading="loading"
          highlight-current-row
          :current-row-key="detailTrace?.messageId"
          row-key="messageId"
          @row-click="handleTraceRowClick"
        >
          <el-table-column prop="messageId" label="消息ID" width="96" />
          <el-table-column prop="ownerRoleLabel" label="来源" width="130" />
          <el-table-column prop="stepCount" label="步骤" width="74" />
          <el-table-column prop="toolCount" label="工具" width="74" />
          <el-table-column label="边界" width="84">
            <template #default="{ row }">
              <el-tag size="small" :type="row.boundedExecution ? 'success' : 'danger'">
                {{ row.boundedExecution ? '正常' : '异常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="证据" width="84">
            <template #default="{ row }">
              <el-tag size="small" :type="row.hasEvidence ? 'success' : 'info'">
                {{ row.hasEvidence ? '有' : '无' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="replyPreview" label="回复摘要" min-width="260" show-overflow-tooltip />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <div v-if="filteredRecentTraces.length === 0" class="admin-empty-block">
          <el-empty description="暂无轨迹数据" />
        </div>
      </el-card>

      <div class="admin-aside-stack">
        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Trace Detail</span>
                <h2>右侧详情</h2>
                <p>点击左侧轨迹行后，在这里查看步骤、工具和引用细节。</p>
              </div>
            </div>
          </template>
          <div v-if="detailTrace" class="trace-detail">
            <div class="trace-detail__title">
              <strong>消息 #{{ detailTrace.messageId }}</strong>
              <el-tag size="small" :type="detailTrace.boundedExecution ? 'success' : 'danger'">
                {{ detailTrace.boundedExecution ? '边界正常' : '边界异常' }}
              </el-tag>
            </div>
            <div class="trace-detail__meta">
              <span>{{ detailTrace.ownerRoleLabel || '--' }}</span>
              <span>{{ formatTime(detailTrace.createdAt) }}</span>
              <span>评分 {{ detailTrace.qualityScore ?? '--' }}</span>
            </div>
            <div class="trace-detail__ops">
              <el-button
                size="small"
                type="primary"
                plain
                :loading="orchestrationLinkLoading"
                :disabled="!detailTrace.sessionId"
                @click="loadLinkedOrchestration(detailTrace)"
              >
                定位关联任务
              </el-button>
              <el-button
                size="small"
                :disabled="!linkedOrchestration.taskId"
                @click="openLinkedOrchestration()"
              >
                前往任务治理
              </el-button>
            </div>
            <div v-if="linkedOrchestration.taskId" class="trace-detail__linkage">
              <strong>关联任务：</strong>{{ linkedOrchestration.taskId }}
              <span>控制状态 {{ linkedOrchestration.controlStatus || '--' }}</span>
              <span>运行状态 {{ linkedOrchestration.status || '--' }}</span>
            </div>
            <div v-if="linkedOrchestration.taskId" class="trace-detail__block trace-detail__block--ops">
              <div class="trace-detail__label">关联任务快捷处置</div>
              <div class="trace-linked-task">
                <div class="trace-linked-task__head">
                  <strong>{{ linkedTaskStatusHeadline }}</strong>
                  <el-tag size="small" :type="linkedTaskStatusType">{{ linkedOrchestration.controlStatus || linkedOrchestration.status || '--' }}</el-tag>
                </div>
                <div class="trace-linked-task__meta">
                  <span>下一步 {{ linkedOrchestration.pendingReviewAction || linkedOrchestration.nextAction || '--' }}</span>
                  <span>进度 {{ linkedOrchestration.progressSummary || '--' }}</span>
                  <span>连续失败 {{ linkedOrchestration.watchdogFailureStreak || 0 }}</span>
                </div>
                <div class="trace-linked-task__text">
                  {{ linkedOrchestration.currentStatusSummary || linkedTaskActionHint }}
                </div>
                <div class="trace-linked-task__actions">
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    :loading="orchestrationAdvanceLoading"
                    @click="advanceLinkedTaskNow"
                  >
                    立即推进一轮
                  </el-button>
                  <el-button
                    size="small"
                    :loading="orchestrationActionLoading === 'pause'"
                    @click="runLinkedTaskAction('pause')"
                  >
                    暂停
                  </el-button>
                  <el-button
                    size="small"
                    type="success"
                    :loading="orchestrationActionLoading === 'resume'"
                    @click="runLinkedTaskAction('resume')"
                  >
                    恢复
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="linkedOrchestration.controlStatus !== 'AWAITING_REVIEW'"
                    :loading="orchestrationActionLoading === 'ack'"
                    @click="runLinkedTaskAction('ack')"
                  >
                    复核后继续
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    :loading="orchestrationActionLoading === 'retry'"
                    @click="runLinkedTaskAction('retry')"
                  >
                    强制重跑
                  </el-button>
                </div>
                <div v-if="linkedTaskDiff" class="trace-linked-task__diff">
                  <div class="trace-linked-task__subtask-head">
                    <strong>恢复前后差异</strong>
                    <el-tag size="small" :type="linkedTaskDiff.statusTag">{{ linkedTaskDiff.statusLabel }}</el-tag>
                  </div>
                  <div class="trace-linked-task__meta">
                    <span>动作 {{ linkedTaskDiff.actionLabel }}</span>
                    <span>前 {{ linkedTaskDiff.beforeStatus }}</span>
                    <span>后 {{ linkedTaskDiff.afterStatus }}</span>
                  </div>
                  <div class="trace-linked-task__text">
                    {{ linkedTaskDiff.summary }}
                  </div>
                  <div class="trace-linked-task__diff-grid">
                    <div class="trace-linked-task__diff-col">
                      <div class="trace-detail__label">状态变化</div>
                      <ul class="trace-linked-task__diff-list">
                        <li v-for="item in linkedTaskDiff.statusChanges" :key="`status-${item}`">{{ item }}</li>
                      </ul>
                    </div>
                    <div class="trace-linked-task__diff-col">
                      <div class="trace-detail__label">阻塞变化</div>
                      <ul class="trace-linked-task__diff-list">
                        <li v-for="item in linkedTaskDiff.blockerChanges" :key="`blocker-${item}`">{{ item }}</li>
                      </ul>
                    </div>
                    <div class="trace-linked-task__diff-col">
                      <div class="trace-detail__label">子任务变化</div>
                      <ul class="trace-linked-task__diff-list">
                        <li v-for="item in linkedTaskDiff.subTaskChanges" :key="`subtask-${item}`">{{ item }}</li>
                      </ul>
                    </div>
                  </div>
                  <div v-if="linkedTaskDiff.newTimelineItems.length > 0" class="trace-linked-task__timeline">
                    <div class="trace-detail__label">本轮新增时间线</div>
                    <ul class="trace-linked-task__diff-list">
                      <li v-for="item in linkedTaskDiff.newTimelineItems" :key="`timeline-${item.key}`">
                        {{ item.time }} · {{ item.summary }}
                      </li>
                    </ul>
                  </div>
                </div>
                <div v-if="linkedBlockedSubTask" class="trace-linked-task__subtask">
                  <div class="trace-linked-task__subtask-head">
                    <strong>首个阻塞子任务 {{ linkedBlockedSubTask.subTaskId }}</strong>
                    <el-tag size="small" type="warning">BLOCKED</el-tag>
                  </div>
                  <div class="trace-linked-task__meta">
                    <span>当前处理方 {{ linkedBlockedSubTask.targetAgent || '--' }}</span>
                    <span>建议处理方 {{ linkedBlockedSubTask.recommendedAgent || '--' }}</span>
                    <span>触发原因 {{ linkedBlockedSubTask.recommendedAgentReviewTrigger || '--' }}</span>
                  </div>
                  <div class="trace-linked-task__text">
                    {{ linkedBlockedSubTask.recommendedAgentReason || linkedBlockedSubTask.description || '当前没有更多阻塞说明。' }}
                  </div>
                  <div class="trace-linked-task__chips">
                    <el-tag
                      v-for="cap in normalizeArray(linkedBlockedSubTask.recentMissingCapabilities)"
                      :key="`linked-blocked-${linkedBlockedSubTask.subTaskId}-${cap}`"
                      size="small"
                      effect="plain"
                    >
                      {{ cap }}
                    </el-tag>
                  </div>
                  <div class="trace-linked-task__actions">
                    <el-button
                      size="small"
                      type="warning"
                      :loading="subTaskActionLoading === 'skip'"
                      @click="runLinkedSubTaskAction('skip')"
                    >
                      跳过子任务
                    </el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="!linkedBlockedSubTask.recommendedAgent"
                      :loading="subTaskActionLoading === 'handoff'"
                      @click="runLinkedSubTaskAction('handoff')"
                    >
                      转派建议服务
                    </el-button>
                    <el-button
                      size="small"
                      type="success"
                      :disabled="normalizeArray(linkedBlockedSubTask.recentMissingCapabilities).length === 0"
                      :loading="subTaskActionLoading === 'capability'"
                      @click="runLinkedSubTaskAction('capability')"
                    >
                      按能力补位
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
            <div class="trace-detail__block">
              <div class="trace-detail__label">回复摘要</div>
              <div class="trace-detail__text">{{ detailTrace.replyPreview || detailTrace.reply || '--' }}</div>
            </div>
            <div v-if="selectedTraceSteps.length > 0" class="trace-detail__block">
              <div class="trace-replay">
                <div class="trace-replay__head">
                  <div>
                    <div class="trace-detail__label">过程回放</div>
                    <div class="trace-replay__caption">当前聚焦：{{ currentTraceReplayLabel }}</div>
                  </div>
                  <div class="trace-replay__actions">
                    <el-button size="small" :disabled="tracePlaybackIndex <= 0" @click="moveTracePlayback(-1)">上一步</el-button>
                    <el-button size="small" type="primary" :disabled="tracePlaybackIndex >= traceStepCount - 1" @click="moveTracePlayback(1)">下一步</el-button>
                    <el-button size="small" plain :disabled="traceStepCount <= 1" @click="toggleTraceAutoplay">
                      {{ isTraceAutoplaying ? '停止回放' : '自动回放' }}
                    </el-button>
                  </div>
                </div>
                <el-progress :percentage="traceReplayProgress" :stroke-width="8" :show-text="false" />
                <div class="trace-replay__chips">
                  <button
                    v-for="(step, index) in selectedTraceSteps"
                    :key="`replay-${step.index || index + 1}-${formatTraceStepLabel(step)}`"
                    type="button"
                    :class="['trace-replay__chip', { 'trace-replay__chip--active': index === tracePlaybackIndex }]"
                    @click="tracePlaybackIndex = index"
                  >
                    {{ step.index || index + 1 }}. {{ formatTraceStepLabel(step) }}
                  </button>
                </div>
                <div v-if="currentTraceReplayStep" class="trace-replay__focus">
                  <strong>{{ currentTraceReplayLabel }}</strong>
                  <p>{{ currentTraceReplayStep.reasoningSummary || currentTraceReplayStep.summary || '当前步骤未附带推理摘要。' }}</p>
                  <p>{{ currentTraceReplayStep.observationSummary || '当前步骤未附带执行结果摘要。' }}</p>
                </div>
              </div>
            </div>
            <div class="trace-detail__block">
              <AgentTraceFlow
                title="处理过程地图"
                eyebrow="Process Visual"
                :summary="detailTrace.knowledgeGraphSummary"
                :steps="selectedTraceSteps"
                :tools="selectedTraceTools"
                :flags="selectedTraceSafetyFlags"
                :references="selectedTraceReferences"
                :active-step-index="tracePlaybackIndex"
                :format-step-label="formatTraceStepLabel"
                @select-reference="openReferenceDrawer"
              />
            </div>
          </div>
          <el-empty v-else description="请选择一条轨迹查看详情" />
        </el-card>

        <el-card shadow="never" class="admin-card admin-card--table">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Rule Logs</span>
                <h2>最近规则执行</h2>
              </div>
            </div>
          </template>
          <el-table :data="recentRuleExecutions" class="admin-data-table" border height="220" v-loading="loading">
            <el-table-column prop="ruleGroup" label="规则组" min-width="120" />
            <el-table-column prop="ruleName" label="规则名" min-width="140" show-overflow-tooltip />
            <el-table-column prop="agentId" label="处理方" min-width="130" show-overflow-tooltip />
            <el-table-column prop="executionTimeMs" label="耗时(ms)" width="90" />
          </el-table>
          <div v-if="recentRuleExecutions.length === 0" class="admin-empty-block">
            <el-empty description="暂无规则执行数据" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card admin-card--table">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Process Messages</span>
                <h2>最近处理消息</h2>
              </div>
            </div>
          </template>
          <el-table :data="recentAgentMessages" class="admin-data-table" border height="220" v-loading="loading">
            <el-table-column prop="senderAgent" label="发送方" min-width="130" show-overflow-tooltip />
            <el-table-column prop="receiverAgent" label="接收方" min-width="130" show-overflow-tooltip />
            <el-table-column prop="performative" label="类型" width="90" />
            <el-table-column prop="contentPreview" label="内容" min-width="200" show-overflow-tooltip />
          </el-table>
          <div v-if="recentAgentMessages.length === 0" class="admin-empty-block">
            <el-empty description="暂无处理消息数据" />
          </div>
        </el-card>
      </div>
    </section>

    <ReferenceDetailDrawer v-model="referenceDrawerVisible" :reference="selectedReference" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import { adminApi } from '../../api'
import ReferenceDetailDrawer from '../../components/ReferenceDetailDrawer.vue'
import AgentTraceFlow from '../../components/AgentTraceFlow.vue'

const DEFAULT_QUERY = {
  days: 30,
  traceLimit: 12,
  ruleLimit: 12,
  messageLimit: 12
}

const loading = ref(false)
const loadError = ref('')
const overview = ref({})
const query = reactive({ ...DEFAULT_QUERY })
const selectedTrace = ref(null)
const tracePlaybackIndex = ref(0)
const isTraceAutoplaying = ref(false)
let tracePlaybackTimer = null
const referenceDrawerVisible = ref(false)
const selectedReference = ref(null)
const activeSection = ref('overview')
const orchestrationLinkLoading = ref(false)
const orchestrationActionLoading = ref('')
const subTaskActionLoading = ref('')
const orchestrationAdvanceLoading = ref(false)
const linkedOrchestration = ref({})
const linkedTaskDiff = ref(null)
const router = useRouter()
const traceFilters = reactive({
  keyword: '',
  ownerRoleLabel: '',
  boundary: '',
  evidence: ''
})
const recommendedFilters = reactive({
  keyword: '',
  ownerRoleLabel: ''
})
const sectionOptions = [
  { label: '总览', value: 'overview' },
  { label: '轨迹明细', value: 'details' }
]

const summary = computed(() => normalizeObject(overview.value.summary))
const evaluation = computed(() => normalizeObject(overview.value.evaluation))
const scorecards = computed(() => normalizeArray(evaluation.value.scorecards))
const stageCoverage = computed(() => normalizeArray(evaluation.value.stageCoverage))
const scenarioBreakdown = computed(() => normalizeArray(evaluation.value.scenarioBreakdown))
const guardrails = computed(() => normalizeArray(overview.value.guardrails))
const recommendedCases = computed(() => normalizeArray(overview.value.recommendedCases))
const recentTraces = computed(() => normalizeArray(overview.value.recentTraces))
const recentRuleExecutions = computed(() => normalizeArray(overview.value.recentRuleExecutions))
const recentAgentMessages = computed(() => normalizeArray(overview.value.recentAgentMessages))
const agentNetworkEdges = computed(() => {
  const grouped = new Map()
  recentAgentMessages.value.forEach((item) => {
    const sender = item?.senderAgent || '未知发送方'
    const receiver = item?.receiverAgent || '未知接收方'
    const key = `${sender}__${receiver}`
    const current = grouped.get(key) || {
      key,
      sender,
      receiver,
      count: 0,
      latestPerformative: '',
      latestCreatedAt: '',
      preview: ''
    }
    current.count += 1
    if (!current.latestCreatedAt || new Date(item?.createdAt || 0).getTime() >= new Date(current.latestCreatedAt || 0).getTime()) {
      current.latestCreatedAt = item?.createdAt || ''
      current.latestPerformative = item?.performative || ''
      current.preview = item?.contentPreview || ''
    }
    grouped.set(key, current)
  })
  return Array.from(grouped.values()).sort((a, b) => {
    if (b.count !== a.count) return b.count - a.count
    return new Date(b.latestCreatedAt || 0).getTime() - new Date(a.latestCreatedAt || 0).getTime()
  })
})
const agentNetworkNodes = computed(() => {
  const counts = new Map()
  recentAgentMessages.value.forEach((item) => {
    ;[item?.senderAgent, item?.receiverAgent].filter(Boolean).forEach((name) => {
      counts.set(name, (counts.get(name) || 0) + 1)
    })
  })
  return Array.from(counts.entries())
    .map(([name, messageCount]) => ({ name, messageCount }))
    .sort((a, b) => b.messageCount - a.messageCount)
})

const overallScore = computed(() => toNumber(evaluation.value.overallScore))
const summaryStatusLabel = computed(() => evaluation.value.overallStatus || '暂无评分')
const traceRoleOptions = computed(() => Array.from(new Set(recentTraces.value.map(item => item?.ownerRoleLabel).filter(Boolean))))
const recommendedRoleOptions = computed(() => Array.from(new Set(recommendedCases.value.map(item => item?.ownerRoleLabel).filter(Boolean))))
const filteredRecentTraces = computed(() => {
  const keyword = traceFilters.keyword.trim().toLowerCase()
  return recentTraces.value.filter((item) => {
    if (!item || typeof item !== 'object') return false
    if (traceFilters.ownerRoleLabel && item.ownerRoleLabel !== traceFilters.ownerRoleLabel) return false
    if (traceFilters.boundary === 'normal' && !item.boundedExecution) return false
    if (traceFilters.boundary === 'abnormal' && item.boundedExecution) return false
    if (traceFilters.evidence === 'yes' && !item.hasEvidence) return false
    if (traceFilters.evidence === 'no' && item.hasEvidence) return false
    if (!keyword) return true
    const joined = [item.messageId, item.replyPreview, item.reply, item.sessionTitle].map(v => String(v || '').toLowerCase()).join(' ')
    return joined.includes(keyword)
  })
})
const detailTrace = computed(() => {
  const rows = filteredRecentTraces.value
  if (rows.length === 0) return null
  const currentId = selectedTrace.value?.messageId
  return rows.find(item => item?.messageId === currentId) || rows[0]
})
const selectedTraceTools = computed(() => normalizeArray(detailTrace.value?.usedTools))
const selectedTraceSteps = computed(() => normalizeArray(detailTrace.value?.steps))
const selectedTraceSafetyFlags = computed(() => normalizeArray(detailTrace.value?.safetyFlags))
const selectedTraceReferences = computed(() => normalizeArray(detailTrace.value?.references))
const traceStepCount = computed(() => selectedTraceSteps.value.length)
const currentTraceReplayStep = computed(() => {
  if (!traceStepCount.value) return null
  const index = Math.max(0, Math.min(traceStepCount.value - 1, tracePlaybackIndex.value))
  return selectedTraceSteps.value[index] || null
})
const currentTraceReplayLabel = computed(() => currentTraceReplayStep.value ? formatTraceStepLabel(currentTraceReplayStep.value) : '--')
const linkedTaskSubTasks = computed(() => normalizeArray(linkedOrchestration.value?.subTasks))
const linkedBlockedSubTask = computed(() =>
  linkedTaskSubTasks.value.find((item) => String(item?.status || '').toUpperCase() === 'BLOCKED') || null
)
const linkedTaskStatusHeadline = computed(() => {
  if (!linkedOrchestration.value?.taskId) return '--'
  if (linkedOrchestration.value.circuitBreakerOpen) return '已进入熔断保护'
  if (linkedOrchestration.value.controlStatus === 'AWAITING_REVIEW') return '等待管理员确认'
  if (linkedOrchestration.value.controlStatus === 'PAUSED') return '已被人工暂停'
  if (linkedOrchestration.value.status === 'BLOCKED') return '存在业务阻塞'
  if (linkedOrchestration.value.status === 'FAILED') return '执行失败'
  if (linkedOrchestration.value.status === 'COMPLETED') return '已完成'
  return '可继续自动推进'
})
const linkedTaskStatusType = computed(() => {
  if (!linkedOrchestration.value?.taskId) return 'info'
  if (linkedOrchestration.value.circuitBreakerOpen || linkedOrchestration.value.status === 'FAILED') return 'danger'
  if (linkedOrchestration.value.controlStatus === 'AWAITING_REVIEW') return 'warning'
  if (linkedOrchestration.value.controlStatus === 'PAUSED') return 'info'
  return 'success'
})
const linkedTaskActionHint = computed(() => {
  if (!linkedOrchestration.value?.taskId) return '请先定位一条关联任务。'
  if (linkedOrchestration.value.controlStatus === 'AWAITING_REVIEW') {
    return '这条任务已暂停等待管理员确认，可在这里直接复核后继续，或前往任务治理页做更细的子任务处置。'
  }
  if (linkedOrchestration.value.status === 'BLOCKED') {
    return '任务当前被阻塞。先看下方首个阻塞子任务的快捷处置，再决定是否强制重跑。'
  }
  if (linkedOrchestration.value.controlStatus === 'PAUSED') {
    return '当前已经人工暂停，恢复后会重新交给系统自动推进。'
  }
  return '这里的快捷操作会直接调用任务治理接口，适合在系统诊断中排查到问题后就地处置。'
})
const traceReplayProgress = computed(() => {
  if (!traceStepCount.value) return 0
  return Number((((tracePlaybackIndex.value + 1) / traceStepCount.value) * 100).toFixed(1))
})
const filteredRecommendedCases = computed(() => {
  const keyword = recommendedFilters.keyword.trim().toLowerCase()
  return recommendedCases.value.filter((item) => {
    if (!item || typeof item !== 'object') return false
    if (recommendedFilters.ownerRoleLabel && item.ownerRoleLabel !== recommendedFilters.ownerRoleLabel) return false
    if (!keyword) return true
    const tags = normalizeArray(item.tags).join(' ')
    const joined = [item.title, item.reason, item.ownerRoleLabel, tags].map(v => String(v || '').toLowerCase()).join(' ')
    return joined.includes(keyword)
  })
})

onMounted(() => {
  loadOverview()
})

watch(detailTrace, (value) => {
  stopTraceAutoplay()
  tracePlaybackIndex.value = value && normalizeArray(value.steps).length > 0 ? 0 : 0
  linkedOrchestration.value = {}
  linkedTaskDiff.value = null
}, { immediate: true })

watch(activeSection, (value) => {
  if (value !== 'details') {
    stopTraceAutoplay()
  }
})

onBeforeUnmount(() => {
  stopTraceAutoplay()
})

function normalizeArray(value) {
  return Array.isArray(value) ? value : []
}

function normalizeObject(value) {
  return value && typeof value === 'object' ? value : {}
}

function clonePlainObject(value) {
  return JSON.parse(JSON.stringify(normalizeObject(value)))
}

function toNumber(value) {
  const result = Number(value)
  return Number.isFinite(result) ? result : 0
}

function statusTagType(score) {
  const value = toNumber(score)
  if (value >= 85) return 'success'
  if (value >= 70) return 'primary'
  if (value >= 50) return 'warning'
  return 'danger'
}

function formatPercent(value) {
  const num = toNumber(value)
  return `${num.toFixed(1)}%`
}

function formatTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function buildLinkedTaskDiff(before, after, actionLabel) {
  const previous = normalizeObject(before)
  const current = normalizeObject(after)
  if (!current.taskId) return null

  const statusChanges = []
  if (previous.controlStatus !== current.controlStatus) {
    statusChanges.push(`控制状态 ${previous.controlStatus || '--'} -> ${current.controlStatus || '--'}`)
  }
  if (previous.status !== current.status) {
    statusChanges.push(`执行状态 ${previous.status || '--'} -> ${current.status || '--'}`)
  }
  if (Number(previous.watchdogFailureStreak || 0) !== Number(current.watchdogFailureStreak || 0)) {
    statusChanges.push(`连续失败 ${previous.watchdogFailureStreak || 0} -> ${current.watchdogFailureStreak || 0}`)
  }
  if (statusChanges.length === 0) {
    statusChanges.push('任务状态未发生明显变化')
  }

  const blockerChanges = diffStringSets(
    normalizeArray(previous.blockers).map(item => String(item)),
    normalizeArray(current.blockers).map(item => String(item)),
    '新增阻塞',
    '已解除'
  )
  if (blockerChanges.length === 0) {
    blockerChanges.push('阻塞集合无变化')
  }

  const previousSubTasks = indexSubTasks(previous.subTasks)
  const currentSubTasks = indexSubTasks(current.subTasks)
  const subTaskChanges = []
  Object.keys(currentSubTasks).forEach((subTaskId) => {
    const beforeItem = previousSubTasks[subTaskId]
    const afterItem = currentSubTasks[subTaskId]
    if (!beforeItem) {
      subTaskChanges.push(`${subTaskId} 新增为 ${afterItem.status || '--'} / ${afterItem.targetAgent || '--'}`)
      return
    }
    if (beforeItem.status !== afterItem.status || beforeItem.targetAgent !== afterItem.targetAgent) {
      subTaskChanges.push(`${subTaskId} ${beforeItem.status || '--'} / ${beforeItem.targetAgent || '--'} -> ${afterItem.status || '--'} / ${afterItem.targetAgent || '--'}`)
    }
  })
  Object.keys(previousSubTasks).forEach((subTaskId) => {
    if (!currentSubTasks[subTaskId]) {
      subTaskChanges.push(`${subTaskId} 已从当前快照移除`)
    }
  })
  if (subTaskChanges.length === 0) {
    subTaskChanges.push('子任务状态无变化')
  }

  const newTimelineItems = computeTimelineDiff(previous.timeline, current.timeline)
  const latestControlOutcome = normalizeObject(current.latestControlOutcome)
  const rawStatus = String(latestControlOutcome.status || '').toLowerCase()
  const statusTag = rawStatus === 'completed'
    ? 'success'
    : rawStatus === 'failed' || rawStatus === 'blocked'
      ? 'danger'
      : rawStatus === 'paused'
        ? 'info'
        : 'warning'
  const statusLabel = rawStatus === 'completed'
    ? '推进完成'
    : rawStatus === 'failed'
      ? '推进失败'
      : rawStatus === 'blocked'
        ? '仍有阻塞'
        : rawStatus === 'paused'
          ? '已暂停'
          : '已更新'

  return {
    actionLabel,
    beforeStatus: `${previous.controlStatus || '--'} / ${previous.status || '--'}`,
    afterStatus: `${current.controlStatus || '--'} / ${current.status || '--'}`,
    statusTag,
    statusLabel,
    summary: latestControlOutcome.summary || current.currentStatusSummary || '已记录本次处置后的恢复结果。',
    statusChanges,
    blockerChanges,
    subTaskChanges,
    newTimelineItems
  }
}

function diffStringSets(beforeItems, afterItems, addedLabel, removedLabel) {
  const before = new Set(beforeItems)
  const after = new Set(afterItems)
  const rows = []
  afterItems.forEach((item) => {
    if (!before.has(item)) rows.push(`${addedLabel}: ${item}`)
  })
  beforeItems.forEach((item) => {
    if (!after.has(item)) rows.push(`${removedLabel}: ${item}`)
  })
  return rows
}

function indexSubTasks(items) {
  const map = {}
  normalizeArray(items).forEach((item) => {
    const subTaskId = String(item?.subTaskId || '')
    if (!subTaskId) return
    map[subTaskId] = normalizeObject(item)
  })
  return map
}

function computeTimelineDiff(beforeTimeline, afterTimeline) {
  const beforeKeys = new Set(
    normalizeArray(beforeTimeline).map(item => `${item?.type || ''}|${item?.occurredAt || ''}|${item?.displaySummary || item?.summary || ''}`)
  )
  return normalizeArray(afterTimeline)
    .filter((item) => {
      const key = `${item?.type || ''}|${item?.occurredAt || ''}|${item?.displaySummary || item?.summary || ''}`
      return !beforeKeys.has(key)
    })
    .map((item) => ({
      key: `${item?.type || ''}|${item?.occurredAt || ''}|${item?.displaySummary || item?.summary || ''}`,
      time: formatTime(item?.occurredAt),
      summary: item?.displaySummary || item?.summary || item?.type || '--'
    }))
}

function resetQuery() {
  Object.assign(query, DEFAULT_QUERY)
  loadOverview()
}

function handleTraceRowClick(row) {
  selectedTrace.value = row || null
}

async function loadLinkedOrchestration(trace) {
  if (!trace?.sessionId) return
  orchestrationLinkLoading.value = true
  try {
    linkedOrchestration.value = normalizeObject(await adminApi.getLatestSessionOrchestrationTask(trace.sessionId))
    linkedTaskDiff.value = null
    if (!linkedOrchestration.value.taskId) {
      ElMessage.warning('当前轨迹未找到关联任务')
    } else {
      ElMessage.success(`已定位关联任务 ${linkedOrchestration.value.taskId}`)
    }
  } catch (error) {
    linkedOrchestration.value = {}
    ElMessage.error(error.response?.data?.message || '定位关联任务失败')
  } finally {
    orchestrationLinkLoading.value = false
  }
}

function openLinkedOrchestration() {
  if (!linkedOrchestration.value?.taskId) return
  router.push({
    name: 'AdminOrchestration',
    query: {
      taskId: linkedOrchestration.value.taskId
    }
  })
}

async function runLinkedTaskAction(type) {
  if (!linkedOrchestration.value?.taskId) return
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
  orchestrationActionLoading.value = type
  const before = clonePlainObject(linkedOrchestration.value)
  try {
    linkedOrchestration.value = normalizeObject(await config.api(linkedOrchestration.value.taskId, reason || undefined))
    linkedTaskDiff.value = buildLinkedTaskDiff(before, linkedOrchestration.value, config.title)
    ElMessage.success(config.success)
    await loadOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || `${config.title}失败`)
  } finally {
    orchestrationActionLoading.value = ''
  }
}

async function advanceLinkedTaskNow() {
  if (!linkedOrchestration.value?.taskId) return
  orchestrationAdvanceLoading.value = true
  const before = clonePlainObject(linkedOrchestration.value)
  try {
    linkedOrchestration.value = normalizeObject(await adminApi.advanceOrchestrationTaskNow(linkedOrchestration.value.taskId))
    linkedTaskDiff.value = buildLinkedTaskDiff(before, linkedOrchestration.value, '立即推进一轮')
    ElMessage.success('已触发当前任务立即推进一轮')
    await loadOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '立即推进失败')
  } finally {
    orchestrationAdvanceLoading.value = false
  }
}

async function runLinkedSubTaskAction(type) {
  const subTask = linkedBlockedSubTask.value
  if (!linkedOrchestration.value?.taskId || !subTask?.subTaskId) return
  subTaskActionLoading.value = type
  const before = clonePlainObject(linkedOrchestration.value)
  try {
    if (type === 'skip') {
      const reason = await promptReason('跳过阻塞子任务', true)
      if (reason === null) return
      linkedOrchestration.value = normalizeObject(
        await adminApi.skipOrchestrationSubTask(linkedOrchestration.value.taskId, subTask.subTaskId, reason)
      )
      linkedTaskDiff.value = buildLinkedTaskDiff(before, linkedOrchestration.value, '跳过子任务')
      ElMessage.success('阻塞子任务已跳过')
    } else if (type === 'handoff') {
      const targetAgent = subTask.recommendedAgent || ''
      if (!targetAgent) {
        ElMessage.warning('当前没有推荐服务，无法直接转派')
        return
      }
      const reason = await promptReason('转派阻塞子任务', true)
      if (reason === null) return
      linkedOrchestration.value = normalizeObject(
        await adminApi.handoffOrchestrationSubTask(linkedOrchestration.value.taskId, subTask.subTaskId, targetAgent, reason)
      )
      linkedTaskDiff.value = buildLinkedTaskDiff(before, linkedOrchestration.value, `转派给 ${targetAgent}`)
      ElMessage.success(`子任务已转派给 ${targetAgent}`)
    } else if (type === 'capability') {
      const capability = normalizeArray(subTask.recentMissingCapabilities)[0]
      if (!capability) {
        ElMessage.warning('当前没有可用于补位的缺失能力')
        return
      }
      const reason = await promptReason(`按能力补位：${capability}`, true)
      if (reason === null) return
      linkedOrchestration.value = normalizeObject(
        await adminApi.handoffOrchestrationSubTaskByCapability(linkedOrchestration.value.taskId, subTask.subTaskId, capability, reason)
      )
      linkedTaskDiff.value = buildLinkedTaskDiff(before, linkedOrchestration.value, `按能力补位 ${capability}`)
      ElMessage.success(`已按能力 ${capability} 进行补位`)
    }
    await loadOverview()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '阻塞子任务处置失败')
  } finally {
    subTaskActionLoading.value = ''
  }
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

function openReferenceDrawer(reference) {
  if (!reference || typeof reference !== 'object') return
  selectedReference.value = reference
  referenceDrawerVisible.value = true
}

function formatTraceStepLabel(step) {
  return step?.actionName || step?.agentName || '未命名动作'
}

function moveTracePlayback(delta) {
  if (!traceStepCount.value) {
    tracePlaybackIndex.value = 0
    return
  }
  tracePlaybackIndex.value = Math.max(0, Math.min(traceStepCount.value - 1, tracePlaybackIndex.value + delta))
}

function stopTraceAutoplay() {
  if (tracePlaybackTimer) {
    clearInterval(tracePlaybackTimer)
    tracePlaybackTimer = null
  }
  isTraceAutoplaying.value = false
}

function toggleTraceAutoplay() {
  if (isTraceAutoplaying.value) {
    stopTraceAutoplay()
    return
  }
  if (traceStepCount.value <= 1) return
  isTraceAutoplaying.value = true
  tracePlaybackTimer = setInterval(() => {
    if (tracePlaybackIndex.value >= traceStepCount.value - 1) {
      stopTraceAutoplay()
      return
    }
    tracePlaybackIndex.value += 1
  }, 1500)
}

function pickSelectedTrace(traces) {
  const rows = normalizeArray(traces)
  if (rows.length === 0) {
    selectedTrace.value = null
    return
  }
  const currentId = selectedTrace.value?.messageId
  const matched = rows.find(item => item?.messageId === currentId)
  selectedTrace.value = matched || rows[0]
}

async function loadOverview() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await adminApi.getHarnessOverview({
      days: query.days,
      traceLimit: query.traceLimit,
      ruleLimit: query.ruleLimit,
      messageLimit: query.messageLimit
    })
    overview.value = normalizeObject(data)
    pickSelectedTrace(overview.value.recentTraces)
  } catch (error) {
    loadError.value = error.response?.data?.message || '系统诊断看板加载失败'
    ElMessage.error(loadError.value)
    overview.value = {}
    selectedTrace.value = null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.harness-page {
  padding-bottom: 6px;
  width: 100%;
  min-width: 0;
  gap: 18px;
}

.harness-pane {
  display: grid;
  gap: 18px;
}

.harness-switcher {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 18px;
  border: 1px solid rgba(51, 65, 85, 0.1);
  background: rgba(255, 255, 255, 0.72);
}

.harness-switcher__control {
  flex-shrink: 0;
}

.harness-switcher__hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.harness-stat-grid :deep(.admin-stat-card) {
  min-height: 116px;
}

.harness-page .admin-grid--sidebar {
  grid-template-columns: minmax(0, 1.28fr) minmax(350px, 0.82fr);
}

.harness-page .admin-grid--editor {
  grid-template-columns: minmax(0, 1.36fr) minmax(360px, 0.82fr);
}

.harness-page .admin-grid--sidebar > *,
.harness-page .admin-grid--editor > * {
  min-width: 0;
}

.harness-page :deep(.admin-stat-card) {
  min-height: 132px;
  display: flex;
  flex-direction: column;
}

.harness-page :deep(.admin-stat-card p) {
  margin-top: auto;
}

.harness-error-alert {
  border-radius: 14px;
}

.query-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(260px, 0.92fr);
  gap: 14px;
  align-items: start;
}

.query-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.query-form :deep(.el-input-number) {
  width: 100%;
}

.query-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.query-form__actions {
  grid-column: 1 / -1;
}

.query-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.query-status-panel {
  border: 1px solid rgba(51, 65, 85, 0.12);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.96));
  padding: 11px 12px;
}

.query-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.eval-coverage,
.eval-scenario {
  margin-top: 14px;
}

.eval-coverage h3,
.eval-scenario h3 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #334155;
}

.eval-coverage-list,
.eval-scenario-list {
  display: grid;
  gap: 8px;
}

.eval-coverage-item,
.eval-scenario-item {
  border-radius: 12px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.95));
  padding: 12px;
}

.eval-coverage-item__head,
.eval-scenario-item__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.eval-coverage-item__desc,
.eval-scenario-item__desc {
  margin-top: 8px;
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
}

.eval-coverage-item :deep(.el-progress-bar__outer) {
  margin-top: 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
}

.eval-coverage-item :deep(.el-progress-bar__inner) {
  border-radius: 999px;
}

.eval-scenario-item__meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #64748b;
}

.score-item {
  border-radius: 14px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.85), rgba(255, 255, 255, 0.95));
  padding: 14px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.score-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(51, 65, 85, 0.08);
}

.score-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.score-item__score {
  margin-top: 8px;
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.score-item__signal {
  margin-top: 8px;
  color: #475569;
  line-height: 1.55;
  font-size: 12px;
}

.guardrail-item {
  border-radius: 12px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  padding: 10px 12px;
}

.guardrail-item__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.guardrail-item__text {
  margin-top: 6px;
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
}

.agent-network-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.72fr) minmax(0, 1.28fr);
  gap: 16px;
}

.agent-network-panel {
  border-radius: 16px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.96));
  padding: 14px;
}

.agent-network-panel__title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.agent-network-node-list,
.agent-network-edge-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.agent-network-node,
.agent-network-edge {
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.95);
  padding: 12px 14px;
}

.agent-network-node strong,
.agent-network-edge strong {
  color: #0f172a;
}

.agent-network-node span,
.agent-network-edge__meta,
.agent-network-edge__desc {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.agent-network-edge__head,
.agent-network-edge__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.trace-detail {
  display: grid;
  gap: 12px;
}

.trace-detail__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.trace-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
}

.trace-detail__ops,
.trace-detail__linkage {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.trace-detail__linkage {
  font-size: 12px;
  color: #475569;
}

.trace-detail__block {
  border: 1px solid rgba(51, 65, 85, 0.12);
  border-radius: 12px;
  padding: 12px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.85), rgba(255, 255, 255, 0.95));
}

.trace-detail__block--ops {
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.88), rgba(255, 255, 255, 0.97));
  border-color: rgba(59, 130, 246, 0.18);
}

.trace-detail__label {
  font-size: 12px;
  color: #334155;
  font-weight: 600;
}

.trace-detail__text {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #475569;
}

.trace-linked-task {
  margin-top: 8px;
  display: grid;
  gap: 10px;
}

.trace-linked-task__head,
.trace-linked-task__subtask-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.trace-linked-task__meta,
.trace-linked-task__actions,
.trace-linked-task__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trace-linked-task__diff {
  display: grid;
  gap: 10px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.78), rgba(255, 255, 255, 0.96));
}

.trace-linked-task__diff-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.trace-linked-task__diff-col,
.trace-linked-task__timeline {
  min-width: 0;
}

.trace-linked-task__diff-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.trace-linked-task__meta {
  color: #64748b;
  font-size: 12px;
}

.trace-linked-task__text {
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.trace-linked-task__subtask {
  display: grid;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px dashed rgba(148, 163, 184, 0.28);
}

.trace-replay {
  display: grid;
  gap: 12px;
}

.trace-replay__head,
.trace-replay__actions,
.trace-replay__chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.trace-replay__head {
  align-items: flex-start;
  justify-content: space-between;
}

.trace-replay__caption {
  margin-top: 6px;
  font-size: 12px;
  color: #475569;
}

.trace-replay__chips {
  margin-top: 2px;
}

.trace-replay__chip {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: #fff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s ease;
}

.trace-replay__chip:hover {
  border-color: rgba(59, 130, 246, 0.32);
  color: #1d4ed8;
}

.trace-replay__chip--active {
  border-color: rgba(37, 99, 235, 0.36);
  background: rgba(239, 246, 255, 0.96);
  color: #1d4ed8;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.08);
}

.trace-replay__focus {
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.9), rgba(248, 250, 252, 0.96));
  border: 1px solid rgba(59, 130, 246, 0.14);
}

.trace-replay__focus strong {
  color: #0f172a;
}

.trace-replay__focus p {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.trace-tags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trace-step-list {
  margin-top: 6px;
  display: grid;
  gap: 6px;
}

.trace-step-item {
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.28);
  padding: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #475569;
}

.trace-step-item strong {
  color: #0f172a;
}

.trace-reference-list {
  margin-top: 6px;
  display: grid;
  gap: 8px;
}

.trace-reference-button {
  width: 100%;
  text-align: left;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #dbeafe;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.trace-reference-button:hover {
  border-color: #93c5fd;
  box-shadow: 0 8px 16px rgba(59, 130, 246, 0.14);
  transform: translateY(-1px);
}

.trace-reference-button__title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #1e3a8a;
}

.trace-reference-button__desc {
  margin-top: 6px;
  display: block;
  font-size: 12px;
  color: #475569;
}

.toolbar-keyword {
  flex: 1;
  min-width: 260px;
}

.toolbar-role {
  width: 150px;
  min-width: 120px;
}

.toolbar-short {
  width: 128px;
  min-width: 120px;
}

.recommended-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.recommended-card {
  border-radius: 16px;
  border: 1px solid rgba(51, 65, 85, 0.12);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.96));
  padding: 13px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.recommended-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(51, 65, 85, 0.1);
}

.recommended-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.recommended-card__meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.recommended-card__reason {
  margin-top: 7px;
  line-height: 1.55;
  color: #334155;
  font-size: 12px;
}

.recommended-card__tags {
  margin-top: 7px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.trace-detail-card {
  position: sticky;
  top: 16px;
}

.trace-detail-card :deep(.el-card__body) {
  max-height: calc(100vh - 168px);
  overflow: auto;
}

@media (max-width: 960px) {
  .harness-switcher {
    flex-direction: column;
    align-items: stretch;
  }

  .score-grid {
    grid-template-columns: 1fr;
  }

  .query-form {
    grid-template-columns: 1fr;
  }

  .query-form__actions {
    grid-column: auto;
  }

  .query-form :deep(.el-input-number) {
    width: 100%;
  }

  .toolbar-keyword,
  .toolbar-role,
  .toolbar-short {
    width: 100%;
  }

  .query-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .query-status-panel {
    padding: 10px;
  }

  .agent-network-grid {
    grid-template-columns: 1fr;
  }

  .trace-detail-card {
    position: static;
  }

  .trace-detail-card :deep(.el-card__body) {
    max-height: none;
    overflow: visible;
  }
}

@media (max-width: 1320px) {
  .harness-page .admin-grid--sidebar,
  .harness-page .admin-grid--editor {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (min-width: 1560px) {
  .harness-page .admin-grid--sidebar {
    grid-template-columns: minmax(0, 1.36fr) minmax(380px, 0.86fr);
  }

  .harness-page .admin-grid--editor {
    grid-template-columns: minmax(0, 1.4fr) minmax(380px, 0.86fr);
  }
}
</style>
