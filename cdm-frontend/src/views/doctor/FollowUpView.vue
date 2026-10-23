<template>
  <div class="followup-page">
    <section class="followup-hero">
      <div class="followup-hero__content">
        <span class="followup-hero__eyebrow">FOLLOW-UP CARE</span>
        <h1>随访执行工作台</h1>
        <p>
          管理人工随访计划、补录关键健康指标，并结合智能随访提醒持续跟进，避免高风险患者脱管。
        </p>
        <div class="followup-hero__chips">
          <el-tag effect="plain" round>当前视图：{{ tabLabel(activeTab) }}</el-tag>
          <el-tag effect="plain" round type="warning">医生患者 {{ patients.length }}</el-tag>
          <el-tag effect="plain" round type="success">
            {{ activeTab === 'smart' ? `已回复 ${smartRepliedCount}` : `重点处理 ${attentionPlanCount}` }}
          </el-tag>
        </div>
      </div>

      <div class="followup-hero__actions">
        <template v-if="activeTab !== 'smart'">
          <el-button class="followup-hero__ghost" @click="loadData">刷新计划</el-button>
          <el-button type="primary" class="followup-hero__primary" @click="openCreateDialog">新建随访</el-button>
        </template>
        <template v-else>
          <el-select
            v-model="smartPatientId"
            placeholder="选择患者"
            filterable
            clearable
            class="followup-hero__select"
            @change="handleSmartPatientChange"
          >
            <el-option v-for="p in patients" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" class="followup-hero__primary" :loading="generating" :disabled="!smartPatientId" @click="generateAiMessage">
            AI 生成随访消息
          </el-button>
        </template>
      </div>
    </section>

    <section class="followup-stat-grid">
      <article class="followup-stat-card">
        <span>{{ activeTab === 'smart' ? '消息历史' : '当前视图计划' }}</span>
        <strong>{{ currentTabRecordCount }}</strong>
        <p>{{ activeTab === 'smart' ? '当前查询范围内的智能随访消息数' : `当前显示 ${tabLabel(activeTab)} 计划` }}</p>
      </article>
      <article class="followup-stat-card">
        <span>管理患者</span>
        <strong>{{ patients.length }}</strong>
        <p>当前医生名下已纳入管理的患者总数</p>
      </article>
      <article class="followup-stat-card">
        <span>{{ activeTab === 'smart' ? '已发送消息' : '重点处理计划' }}</span>
        <strong>{{ activeTab === 'smart' ? smartSentCount : attentionPlanCount }}</strong>
        <p>{{ activeTab === 'smart' ? '已发送但未回复的触达消息' : '待执行与已逾期计划合计' }}</p>
      </article>
      <article class="followup-stat-card">
        <span>{{ activeTab === 'smart' ? '当前目标患者' : 'AI 已回复' }}</span>
        <strong>{{ activeTab === 'smart' ? selectedSmartPatientName : smartRepliedCount }}</strong>
        <p>{{ activeTab === 'smart' ? '智能随访当前聚焦对象' : '最近加载的智能随访回复数' }}</p>
      </article>
    </section>

    <section class="followup-panel">
      <div class="followup-panel__tabs">
        <el-tabs v-model="activeTab" class="followup-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="待执行" name="pending" />
          <el-tab-pane label="进行中" name="in-progress" />
          <el-tab-pane label="已完成" name="completed" />
          <el-tab-pane label="已逾期" name="overdue" />
          <el-tab-pane label="已取消" name="cancelled" />
          <el-tab-pane label="智能随访" name="smart" />
        </el-tabs>
      </div>

      <template v-if="activeTab !== 'smart'">
        <div class="followup-panel__toolbar">
          <div>
            <span class="followup-panel__label">执行视图</span>
            <h2>{{ tabLabel(activeTab) }}计划</h2>
          </div>
          <p>当前显示 {{ followUps.length }} 条记录，可直接查看、编辑、启动、完成或推送 AI 提醒。</p>
        </div>

        <div class="followup-table-shell">
          <div class="followup-table-scroll">
            <el-table :data="followUps" stripe v-loading="loading" class="followup-table">
            <el-table-column prop="patientName" label="患者" width="120" />
            <el-table-column prop="diseaseType" label="疾病" width="130">
              <template #default="{ row }">{{ diseaseLabel(row.diseaseType) }}</template>
            </el-table-column>
            <el-table-column prop="frequency" label="随访频次" width="110">
              <template #default="{ row }">{{ freqLabel(row.frequency) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small" effect="light">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nextFollowDate" label="下次随访日期" width="140" />
            <el-table-column prop="planContent" label="计划内容" min-width="250">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.planContent"
                  placement="top-start"
                  :show-after="150"
                  popper-class="doctor-overflow-tooltip"
                >
                  <template #content>
                    <div class="tooltip-content">{{ row.planContent }}</div>
                  </template>
                  <span class="ellipsis-cell">{{ row.planContent }}</span>
                </el-tooltip>
                <span v-else class="table-empty">-</span>
              </template>
            </el-table-column>
            <el-table-column label="检查项目" min-width="190">
              <template #default="{ row }">
                <div v-if="row.checkItems && row.checkItems.length > 0" class="tag-wrap">
                  <el-tag
                    v-for="item in row.checkItems.slice(0, 2)"
                    :key="`${row.id}-${item}`"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    {{ item }}
                  </el-tag>
                  <el-tag v-if="row.checkItems.length > 2" size="small" type="info" effect="plain">
                    +{{ row.checkItems.length - 2 }}
                  </el-tag>
                </div>
                <span v-else class="table-empty">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button size="small" type="primary" link @click="showDetail(row)">查看计划</el-button>
                  <el-button v-if="canEdit(row)" size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
                  <el-button v-if="canStart(row)" size="small" type="warning" link @click="handleStart(row)">开始执行</el-button>
                  <el-button v-if="canComplete(row)" size="small" type="success" link @click="openCompleteDialog(row)">执行完成</el-button>
                  <el-button v-if="canNotify(row)" size="small" type="warning" link @click="handleSmartNotify(row)">AI提醒</el-button>
                  <el-button v-if="canCancel(row)" size="small" type="danger" link @click="handleCancel(row)">取消</el-button>
                </div>
              </template>
            </el-table-column>
            </el-table>
          </div>

          <div v-if="!loading && followUps.length === 0" class="followup-empty-panel">
            <el-empty description="当前没有随访计划" />
          </div>
        </div>
      </template>

      <template v-else>
        <div class="smart-panel">
          <div class="smart-panel__header">
            <div>
              <span class="followup-panel__label">AI 跟进</span>
              <h2>智能随访工作区</h2>
            </div>
            <p>选择患者后生成触达文案，复核病史提醒与整理步骤，再发送给患者。</p>
          </div>

          <div class="smart-panel__summary">
            <article class="smart-summary-card">
              <span>目标患者</span>
              <strong>{{ selectedSmartPatientName }}</strong>
              <p>{{ smartPatientId ? '已切换到当前患者历史消息' : '先选择患者，再进入随访内容整理' }}</p>
            </article>
            <article class="smart-summary-card">
              <span>已发送</span>
              <strong>{{ smartSentCount }}</strong>
              <p>状态为 sent 的智能随访消息数量</p>
            </article>
            <article class="smart-summary-card">
              <span>已回复</span>
              <strong>{{ smartRepliedCount }}</strong>
              <p>状态为 replied 的患者反馈数量</p>
            </article>
          </div>

          <article v-if="generatedMessage" class="smart-preview-card">
            <div class="smart-preview-card__head">
              <div>
                <span class="followup-panel__label">内容草稿</span>
                <h3>随访消息预览 · {{ generatedMessage.patientName }}</h3>
              </div>
              <div class="smart-preview-card__actions">
                <el-button type="success" size="small" @click="sendAiMessage" :loading="sending">发送给患者</el-button>
                <el-button size="small" @click="generatedMessage = null">取消</el-button>
              </div>
            </div>

            <el-input v-model="generatedMessage.message" type="textarea" :autosize="{ minRows: 4, maxRows: 10 }" />

            <div v-if="hasHistoryAlerts(generatedMessage)" class="smart-history-alerts">
              <div class="smart-history-title">病史提醒</div>
              <div class="smart-history-tag-list">
                <el-tag
                  v-for="(alert, index) in generatedMessage.historyAlerts"
                  :key="`generated-history-${index}`"
                  type="warning"
                  effect="plain"
                  size="small"
                >
                  {{ alert }}
                </el-tag>
              </div>
            </div>

            <div v-if="generatedMessage.agentSteps && generatedMessage.agentSteps.length > 0" class="smart-agent-trace">
              <div class="smart-agent-trace-title">内容整理过程</div>
              <AgentTraceFlow
                title="随访消息整理过程"
                eyebrow="Message Process"
                :steps="generatedMessage.agentSteps"
                :flags="generatedMessage.historyAlerts"
                :format-step-label="(step) => formatSmartAgentName(step?.agentName)"
                compact
              />
            </div>
          </article>

          <div class="followup-table-shell">
            <div class="followup-table-scroll">
              <el-table :data="messageHistory" stripe v-loading="historyLoading" class="followup-table followup-table--history">
              <el-table-column prop="patientName" label="患者" width="110" />
              <el-table-column prop="message" label="消息内容" show-overflow-tooltip />
              <el-table-column prop="channel" label="渠道" width="100" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'sent' ? 'success' : row.status === 'replied' ? 'primary' : 'info'" size="small" effect="light">
                    {{ { pending: '待发送', sent: '已发送', replied: '已回复' }[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="feedback" label="患者反馈" show-overflow-tooltip>
                <template #default="{ row }">{{ row.feedback || '-' }}</template>
              </el-table-column>
              <el-table-column label="病史提醒" width="220">
                <template #default="{ row }">
                  <div v-if="hasHistoryAlerts(row)" class="smart-history-tag-list">
                    <el-tag
                      v-for="(alert, index) in row.historyAlerts.slice(0, 2)"
                      :key="`history-row-${row.id}-${index}`"
                      type="warning"
                      effect="plain"
                      size="small"
                    >
                      {{ alert }}
                    </el-tag>
                  </div>
                  <span v-else class="table-empty">-</span>
                </template>
              </el-table-column>
              <el-table-column label="生成过程" width="120">
                <template #default="{ row }">
                  <el-popover
                    v-if="row.agentSteps && row.agentSteps.length > 0"
                    placement="left"
                    width="320"
                    trigger="click"
                  >
                    <template #reference>
                      <el-button type="primary" link size="small">查看步骤</el-button>
                    </template>
                    <div class="smart-agent-step-list">
                      <div
                        v-for="step in row.agentSteps"
                        :key="`history-step-${row.id}-${step.index}`"
                        class="smart-agent-step"
                      >
                        <div class="smart-agent-step-top">
                          <span class="smart-agent-step-index">步骤 {{ step.index }}</span>
                          <span class="smart-agent-step-name">{{ formatSmartAgentName(step.agentName) }}</span>
                        </div>
                        <div class="smart-agent-step-summary">{{ step.summary }}</div>
                      </div>
                    </div>
                  </el-popover>
                  <span v-else class="table-empty">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="时间" width="140" />
              </el-table>
            </div>

            <div v-if="!historyLoading && messageHistory.length === 0" class="followup-empty-panel">
              <el-empty description="当前没有智能随访消息记录" />
            </div>
          </div>
        </div>
      </template>
    </section>

    <el-dialog v-model="detailVisible" title="随访计划详情" width="640px" destroy-on-close append-to-body align-center>
      <template v-if="currentRow">
        <div class="followup-detail">
          <div class="followup-detail__summary">
            <div>
              <h3>{{ currentRow.patientName }}</h3>
              <p>{{ diseaseLabel(currentRow.diseaseType) }} · {{ freqLabel(currentRow.frequency) }}</p>
            </div>
            <div class="followup-detail__tags">
              <el-tag :type="statusType(currentRow.status)" size="small">{{ statusLabel(currentRow.status) }}</el-tag>
              <el-tag size="small" type="info" effect="plain">下次 {{ currentRow.nextFollowDate || '未设置' }}</el-tag>
            </div>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="患者">{{ currentRow.patientName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(currentRow.status)" size="small">{{ statusLabel(currentRow.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="疾病类型">{{ diseaseLabel(currentRow.diseaseType) }}</el-descriptions-item>
            <el-descriptions-item label="随访频次">{{ freqLabel(currentRow.frequency) }}</el-descriptions-item>
            <el-descriptions-item label="下次随访日期">{{ currentRow.nextFollowDate || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="计划编号">{{ currentRow.agentId || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="followup-detail__block">
            <div class="followup-detail__label">计划内容</div>
            <RichTextBlock class="detail-content" :content="currentRow.planContent" empty-text="当前没有计划内容" />
          </div>

          <div class="followup-detail__block">
            <div class="followup-detail__label">检查项目</div>
            <div v-if="currentRow.checkItems && currentRow.checkItems.length > 0" class="tag-wrap">
              <el-tag v-for="(item, i) in currentRow.checkItems" :key="i" type="info" effect="plain">{{ item }}</el-tag>
            </div>
            <div v-else class="followup-detail__empty">当前没有检查项目</div>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="currentRow && canEdit(currentRow)" @click="openEditDialog(currentRow)">编辑</el-button>
        <el-button v-if="currentRow && canStart(currentRow)" type="warning" @click="handleStart(currentRow)">开始执行</el-button>
        <el-button v-if="currentRow && canComplete(currentRow)" type="success" @click="openCompleteDialog(currentRow)">执行完成</el-button>
        <el-button v-if="currentRow && canCancel(currentRow)" type="danger" plain @click="handleCancel(currentRow)">取消计划</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑随访计划' : '新建随访计划'"
      width="700px"
      destroy-on-close
      append-to-body
      align-center
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="患者">
          <el-select v-model="form.patientId" placeholder="选择患者" filterable :disabled="!!editingId" style="width: 100%">
            <el-option v-for="p in patients" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="疾病类型">
          <el-select v-model="form.diseaseType" placeholder="选择疾病" style="width: 100%">
            <el-option v-for="item in diseaseOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="随访频次">
          <el-select v-model="form.frequency" placeholder="选择频次" style="width: 100%">
            <el-option v-for="item in frequencyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次日期">
          <el-date-picker
            v-model="form.nextFollowDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择随访日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="计划内容">
          <el-input
            v-model="form.planContent"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
            placeholder="请输入随访计划内容"
          />
        </el-form-item>
        <el-form-item label="检查项目">
          <el-input
            v-model="form.checkItemsInput"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder="每行一项，或用中文/英文逗号分隔"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ editingId ? '保存修改' : '创建计划' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completionVisible" title="完成随访" width="780px" destroy-on-close append-to-body align-center>
      <template v-if="currentRow">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
          title="建议在完成随访时同步补录本次关键指标，系统可基于新数据更新风险评估。"
        />

        <el-form :model="completionForm" label-width="110px">
          <el-form-item label="患者信息">
            <div class="completion-summary">
              <span>{{ currentRow.patientName }}</span>
              <el-tag size="small" type="info">{{ diseaseLabel(currentRow.diseaseType) }}</el-tag>
              <el-tag size="small" :type="statusType(currentRow.status)">{{ statusLabel(currentRow.status) }}</el-tag>
            </div>
          </el-form-item>

          <el-form-item label="完成方式">
            <el-radio-group v-model="completionForm.collectHealthData" @change="handleCompletionModeChange">
              <el-radio :value="true">补录本次关键数据</el-radio>
              <el-radio :value="false">仅完成随访</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="更新风险评估">
            <div class="completion-switch-row">
              <el-switch v-model="completionForm.triggerReassessment" />
              <span class="completion-hint">
                {{ completionForm.collectHealthData ? '保存数据后立即按本次指标重评估。' : '基于患者当前最新健康记录重新评估。' }}
              </span>
            </div>
          </el-form-item>

          <template v-if="completionForm.collectHealthData">
            <el-divider content-position="left">本次随访数据</el-divider>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="记录日期">
                  <el-date-picker
                    v-model="completionForm.recordDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="体重 kg">
                  <el-input-number v-model="completionForm.weight" :min="0" :precision="1" :step="0.5" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row v-if="currentRow.diseaseType === 'HYPERTENSION'" :gutter="16">
              <el-col :span="8">
                <el-form-item label="收缩压">
                  <el-input-number v-model="completionForm.systolicBp" :min="0" :max="300" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="舒张压">
                  <el-input-number v-model="completionForm.diastolicBp" :min="0" :max="200" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="静息心率">
                  <el-input-number v-model="completionForm.restingHeartRate" :min="0" :max="240" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row v-if="currentRow.diseaseType === 'DIABETES'" :gutter="16">
              <el-col :span="8">
                <el-form-item label="空腹血糖">
                  <el-input-number v-model="completionForm.fastingGlucose" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="餐后血糖">
                  <el-input-number v-model="completionForm.postprandialGlucose" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="HbA1c">
                  <el-input-number v-model="completionForm.hba1c" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row v-if="currentRow.diseaseType === 'COPD'" :gutter="16">
              <el-col :span="8">
                <el-form-item label="FEV1%">
                  <el-input-number v-model="completionForm.fev1Percent" :min="0" :max="150" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="CAT 评分">
                  <el-input-number v-model="completionForm.catScore" :min="0" :max="40" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="mMRC 评分">
                  <el-input-number v-model="completionForm.mmrcScore" :min="0" :max="4" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row v-if="currentRow.diseaseType === 'CKD'" :gutter="16">
              <el-col :span="8">
                <el-form-item label="eGFR">
                  <el-input-number v-model="completionForm.egfr" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="UACR">
                  <el-input-number v-model="completionForm.uacr" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="血肌酐">
                  <el-input-number v-model="completionForm.serumCreatinine" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row v-if="currentRow.diseaseType === 'CHD'" :gutter="16">
              <el-col :span="8">
                <el-form-item label="胸痛频次">
                  <el-input-number v-model="completionForm.chestPainFrequency" :min="0" :max="99" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="射血分数">
                  <el-input-number v-model="completionForm.ejectionFraction" :min="0" :max="100" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="LDL-C">
                  <el-input-number v-model="completionForm.ldlCholesterol" :min="0" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row v-if="currentRow.diseaseType === 'STROKE'" :gutter="16">
              <el-col :span="8">
                <el-form-item label="ADL 评分">
                  <el-input-number v-model="completionForm.adlScore" :min="0" :max="100" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="MMSE 评分">
                  <el-input-number v-model="completionForm.mmseScore" :min="0" :max="30" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="颈动脉狭窄%">
                  <el-input-number v-model="completionForm.carotidStenosis" :min="0" :max="100" :precision="1" :step="0.1" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="completion-hint completion-hint-block">
              至少录入一项本次随访指标即可。系统会仅保存你填写的字段。
            </div>
          </template>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="completionVisible = false">取消</el-button>
        <el-button type="primary" :loading="completing" @click="submitCompletion">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import { followUpApi, smartFollowUpApi, patientApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { useRoute, useRouter } from 'vue-router'
import RichTextBlock from '../../components/RichTextBlock.vue'
import AgentTraceFlow from '../../components/AgentTraceFlow.vue'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const followUps = ref([])
const loading = ref(false)
const activeTab = ref('pending')
const detailVisible = ref(false)
const currentRow = ref(null)
const patients = ref([])
const formVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const completionVisible = ref(false)
const completing = ref(false)

const smartPatientId = ref(null)
const generatedMessage = ref(null)
const generating = ref(false)
const sending = ref(false)
const messageHistory = ref([])
const historyLoading = ref(false)
const availableTabs = new Set(['pending', 'in-progress', 'completed', 'overdue', 'cancelled', 'smart'])

const form = reactive(createEmptyForm())
const completionForm = reactive(createEmptyCompletionForm())

const diseaseMap = { DIABETES: '2型糖尿病', HYPERTENSION: '高血压', COPD: '慢阻肺', CKD: '慢性肾病', CHD: '冠心病', STROKE: '脑卒中' }
const freqMap = { IMMEDIATE: '立即', WEEKLY: '每周', BIWEEKLY: '双周', MONTHLY: '每月', QUARTERLY: '每季度' }
const statusMap = { PENDING: '待执行', IN_PROGRESS: '进行中', COMPLETED: '已完成', OVERDUE: '已逾期', CANCELLED: '已取消' }
const tabMap = {
  pending: '待执行',
  'in-progress': '进行中',
  completed: '已完成',
  overdue: '已逾期',
  cancelled: '已取消',
  smart: '智能随访'
}

const diseaseOptions = computed(() => Object.entries(diseaseMap).map(([value, label]) => ({ value, label })))
const frequencyOptions = computed(() => Object.entries(freqMap).map(([value, label]) => ({ value, label })))
const patientIds = computed(() => new Set((patients.value || []).map(item => item.id)))
const currentTabRecordCount = computed(() => activeTab.value === 'smart' ? messageHistory.value.length : followUps.value.length)
const attentionPlanCount = computed(() => followUps.value.filter(item => ['PENDING', 'OVERDUE'].includes(item.status)).length)
const smartSentCount = computed(() => messageHistory.value.filter(item => item.status === 'sent').length)
const smartRepliedCount = computed(() => messageHistory.value.filter(item => item.status === 'replied').length)
const selectedSmartPatientName = computed(() => {
  const patient = patients.value.find(item => item.id === smartPatientId.value)
  return patient?.name || '未选择患者'
})

function createEmptyForm() {
  return {
    patientId: null,
    diseaseType: '',
    frequency: 'WEEKLY',
    nextFollowDate: '',
    planContent: '',
    checkItemsInput: ''
  }
}

function todayString() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function createEmptyCompletionForm() {
  return {
    collectHealthData: true,
    triggerReassessment: true,
    recordDate: todayString(),
    weight: null,
    systolicBp: null,
    diastolicBp: null,
    restingHeartRate: null,
    fastingGlucose: null,
    postprandialGlucose: null,
    hba1c: null,
    fev1Percent: null,
    catScore: null,
    mmrcScore: null,
    egfr: null,
    uacr: null,
    serumCreatinine: null,
    chestPainFrequency: null,
    ejectionFraction: null,
    ldlCholesterol: null,
    adlScore: null,
    mmseScore: null,
    carotidStenosis: null
  }
}

function resetForm() {
  Object.assign(form, createEmptyForm())
  editingId.value = null
}

function resetCompletionForm() {
  Object.assign(completionForm, createEmptyCompletionForm())
}

function tabLabel(name) { return tabMap[name] || name }
function diseaseLabel(t) { return diseaseMap[t] || t }
function freqLabel(f) { return freqMap[f] || f }
function statusLabel(s) { return statusMap[s] || s }
function statusType(s) { return { PENDING: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', OVERDUE: 'danger', CANCELLED: 'info' }[s] || 'info' }
function canEdit(row) { return row && !['COMPLETED', 'CANCELLED'].includes(row.status) }
function canStart(row) { return row && ['PENDING', 'OVERDUE'].includes(row.status) }
function canComplete(row) { return row && ['PENDING', 'OVERDUE', 'IN_PROGRESS'].includes(row.status) }
function canNotify(row) { return row && ['PENDING', 'OVERDUE', 'IN_PROGRESS'].includes(row.status) }
function canCancel(row) { return row && !['COMPLETED', 'CANCELLED'].includes(row.status) }
function hasHistoryAlerts(row) { return Array.isArray(row?.historyAlerts) && row.historyAlerts.length > 0 }

function normalizePatientId(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function applyRouteQuery() {
  const requestedTab = route.query.tab == null ? '' : String(route.query.tab)
  if (availableTabs.has(requestedTab)) {
    activeTab.value = requestedTab
  }
  if (activeTab.value === 'smart') {
    smartPatientId.value = normalizePatientId(route.query.patientId)
  }
}

async function syncRouteQuery(tab = activeTab.value, patientId = smartPatientId.value) {
  const nextTab = availableTabs.has(tab) ? tab : 'pending'
  const nextPatientId = nextTab === 'smart' ? normalizePatientId(patientId) : null
  const currentTab = route.query.tab == null ? '' : String(route.query.tab)
  const currentPatientId = normalizePatientId(route.query.patientId)
  if (currentTab === nextTab && currentPatientId === nextPatientId) {
    return false
  }
  const nextQuery = { ...route.query, tab: nextTab }
  if (nextPatientId != null) {
    nextQuery.patientId = String(nextPatientId)
  } else {
    delete nextQuery.patientId
  }
  await router.replace({ name: 'FollowUps', query: nextQuery })
  return true
}

function parseCheckItems(value) {
  return (value || '')
    .split(/[\n,，]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function filterDoctorPlans(items) {
  if (!patientIds.value.size) return items
  return (items || []).filter(item => patientIds.value.has(item.patientId))
}

function fillFormFromRow(row) {
  Object.assign(form, {
    patientId: row.patientId,
    diseaseType: row.diseaseType,
    frequency: row.frequency,
    nextFollowDate: row.nextFollowDate || '',
    planContent: row.planContent || '',
    checkItemsInput: (row.checkItems || []).join('\n')
  })
}

function showDetail(row) {
  currentRow.value = row
  detailVisible.value = true
}

function openCompleteDialog(row) {
  currentRow.value = row
  resetCompletionForm()
  detailVisible.value = false
  completionVisible.value = true
}

function openCreateDialog() {
  resetForm()
  formVisible.value = true
}

function openEditDialog(row) {
  currentRow.value = row
  editingId.value = row.id
  fillFormFromRow(row)
  detailVisible.value = false
  formVisible.value = true
}

async function submitForm() {
  if (!form.patientId) return ElMessage.warning('请选择患者')
  if (!form.diseaseType) return ElMessage.warning('请选择疾病类型')
  if (!form.frequency) return ElMessage.warning('请选择随访频次')
  if (!form.planContent.trim()) return ElMessage.warning('请输入计划内容')

  saving.value = true
  try {
    const payload = {
      patientId: form.patientId,
      diseaseType: form.diseaseType,
      frequency: form.frequency,
      nextFollowDate: form.nextFollowDate || null,
      planContent: form.planContent.trim(),
      checkItems: parseCheckItems(form.checkItemsInput),
      agentId: 'DoctorManual'
    }

    if (editingId.value) {
      const updated = await followUpApi.update(editingId.value, payload)
      if (updated?.status === 'PENDING' && currentRow.value?.status !== 'PENDING') {
        activeTab.value = 'pending'
        ElMessage.success('随访计划已更新，因重新排期已恢复为待执行')
      } else if (updated?.status === 'OVERDUE' && currentRow.value?.status !== 'OVERDUE') {
        activeTab.value = 'overdue'
        ElMessage.success('随访计划已更新，因下次随访时间已过期，状态已更新为逾期')
      } else {
        ElMessage.success('随访计划已更新')
      }
    } else {
      await followUpApi.create(payload)
      activeTab.value = 'pending'
      ElMessage.success('随访计划已创建')
    }

    formVisible.value = false
    resetForm()
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function handleStart(row) {
  try {
    await ElMessageBox.confirm(`确认将 ${row.patientName} 的随访计划标记为进行中？`, '确认操作', { type: 'warning' })
    await followUpApi.start(row.id)
    ElMessage.success('已标记为进行中')
    detailVisible.value = false
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e.message || e))
  }
}

function handleCompletionModeChange(value) {
  completionForm.triggerReassessment = !!value
}

function buildCompletionHealthRecord() {
  const payload = {
    patientId: currentRow.value?.patientId || null,
    recordDate: completionForm.recordDate || todayString()
  }
  const keys = [
    'weight',
    'systolicBp',
    'diastolicBp',
    'restingHeartRate',
    'fastingGlucose',
    'postprandialGlucose',
    'hba1c',
    'fev1Percent',
    'catScore',
    'mmrcScore',
    'egfr',
    'uacr',
    'serumCreatinine',
    'chestPainFrequency',
    'ejectionFraction',
    'ldlCholesterol',
    'adlScore',
    'mmseScore',
    'carotidStenosis'
  ]
  keys.forEach((key) => {
    if (completionForm[key] !== null && completionForm[key] !== undefined && completionForm[key] !== '') {
      payload[key] = completionForm[key]
    }
  })
  return payload
}

function hasCompletionMetrics() {
  const payload = buildCompletionHealthRecord()
  return Object.keys(payload).some(key => !['patientId', 'recordDate'].includes(key))
}

async function submitCompletion() {
  if (!currentRow.value) return
  if (completionForm.collectHealthData && !hasCompletionMetrics()) {
    ElMessage.warning('请至少录入一项本次随访指标')
    return
  }

  completing.value = true
  try {
    const payload = {
      triggerReassessment: completionForm.triggerReassessment
    }
    if (completionForm.collectHealthData) {
      payload.healthRecord = buildCompletionHealthRecord()
    }

    await followUpApi.complete(currentRow.value.id, payload)
    activeTab.value = 'completed'
    completionVisible.value = false

    if (completionForm.collectHealthData && completionForm.triggerReassessment) {
      ElMessage.success('随访已完成，已保存本次数据并更新风险评估')
    } else if (completionForm.collectHealthData) {
      ElMessage.success('随访已完成，已保存本次随访数据')
    } else if (completionForm.triggerReassessment) {
      ElMessage.success('随访已完成，已按最新记录更新风险评估')
    } else {
      ElMessage.success('随访已完成')
    }

    await loadData()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message || e))
  } finally {
    completing.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确认取消 ${row.patientName} 的随访计划？`, '确认操作', { type: 'warning' })
    await followUpApi.cancel(row.id)
    ElMessage.success('随访计划已取消')
    detailVisible.value = false
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e.message || e))
  }
}

async function handleSmartNotify(row) {
  smartPatientId.value = row.patientId
  activeTab.value = 'smart'
  await generateAiMessage()
}

async function generateAiMessage() {
  if (!smartPatientId.value) return
  generating.value = true
  try {
    generatedMessage.value = await smartFollowUpApi.generate(smartPatientId.value)
  } catch (e) {
    ElMessage.error('生成失败: ' + (e.message || e))
  } finally {
    generating.value = false
  }
}

async function sendAiMessage() {
  if (!generatedMessage.value) return
  sending.value = true
  try {
    await smartFollowUpApi.send(
      generatedMessage.value.patientId,
      generatedMessage.value.message,
      generatedMessage.value.agentSteps || [],
      generatedMessage.value.historyAlerts || []
    )
    ElMessage.success('随访消息已发送给患者')
    generatedMessage.value = null
    await loadMessageHistory()
  } catch (e) {
    ElMessage.error('发送失败: ' + (e.message || e))
  } finally {
    sending.value = false
  }
}

async function loadMessageHistory() {
  historyLoading.value = true
  try {
    messageHistory.value = await smartFollowUpApi.getHistory({
      patientId: smartPatientId.value || undefined,
      limit: 30
    })
  } finally {
    historyLoading.value = false
  }
}

async function handleSmartPatientChange() {
  if (activeTab.value !== 'smart') return
  if (await syncRouteQuery('smart', smartPatientId.value)) {
    return
  }
  await loadMessageHistory()
}

async function loadData() {
  loading.value = true
  try {
    let result = []
    if (activeTab.value === 'completed') {
      result = await followUpApi.getCompleted()
    } else if (activeTab.value === 'overdue') {
      result = await followUpApi.getOverdue()
    } else if (activeTab.value === 'pending') {
      result = await followUpApi.getPending()
    } else if (activeTab.value === 'in-progress') {
      result = await followUpApi.getInProgress()
    } else if (activeTab.value === 'cancelled') {
      result = await followUpApi.getCancelled()
    }
    followUps.value = filterDoctorPlans(result)
  } finally {
    loading.value = false
  }
}

async function handleTabChange() {
  if (await syncRouteQuery(activeTab.value, smartPatientId.value)) {
    return
  }
  if (activeTab.value === 'smart') {
    await loadMessageHistory()
  } else {
    await loadData()
  }
}

function formatSmartAgentName(name) {
  const agentMap = {
    followup_context_agent: '信息整理',
    followup_focus_agent: '重点提炼',
    followup_drafting_agent: '随访文案起草'
  }
  return agentMap[name] || '处理步骤'
}

onMounted(async () => {
  patients.value = await patientApi.list({ doctorId: userStore.doctorId })
  applyRouteQuery()
  if (activeTab.value === 'smart') {
    await loadMessageHistory()
  } else {
    await loadData()
  }
})

watch(
  () => [route.query.tab, route.query.patientId],
  async () => {
    applyRouteQuery()
    if (activeTab.value === 'smart') {
      await loadMessageHistory()
    } else {
      await loadData()
    }
  }
)
</script>

<style scoped>
.followup-page {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.followup-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(143, 177, 198, 0.2), transparent 34%),
    linear-gradient(135deg, #21313d 0%, #324757 52%, #456072 100%);
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
  color: #f7fbff;
}

.followup-hero__content {
  max-width: 760px;
}

.followup-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.followup-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.followup-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.followup-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.followup-hero__actions {
  display: grid;
  gap: 12px;
  align-content: start;
  width: 280px;
}

.followup-hero__ghost,
.followup-hero__primary,
.followup-hero__select {
  width: 100%;
}

.followup-hero__ghost,
.followup-hero__primary {
  min-height: 46px;
}

.followup-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.followup-stat-card,
.smart-summary-card {
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 16px 36px rgba(17, 28, 38, 0.08);
}

.followup-stat-card span,
.smart-summary-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.followup-stat-card strong,
.smart-summary-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.followup-stat-card p,
.smart-summary-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.followup-panel {
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 44px rgba(17, 28, 38, 0.08);
  min-width: 0;
}

.followup-panel__tabs {
  margin-bottom: 16px;
}

.followup-panel__toolbar,
.smart-panel__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.followup-panel__label {
  display: inline-flex;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #7093a8;
}

.followup-panel__toolbar h2,
.smart-panel__header h2 {
  margin: 0;
  font-size: 26px;
  color: #1f3340;
}

.followup-panel__toolbar p,
.smart-panel__header p {
  margin: 0;
  max-width: 420px;
  font-size: 13px;
  line-height: 1.7;
  color: #6e7f89;
  text-align: right;
}

.followup-table-shell {
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(248, 250, 251, 0.88);
  min-width: 0;
}

.followup-table-scroll {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
}

.followup-table {
  min-width: 1320px;
}

.followup-table--history {
  min-width: 1120px;
}

.followup-empty-panel {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.smart-panel {
  display: grid;
  gap: 18px;
}

.smart-panel__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.smart-preview-card {
  padding: 20px;
  border-radius: 24px;
  border: 1px solid rgba(174, 213, 129, 0.34);
  background: linear-gradient(180deg, #f6fced 0%, #eef8df 100%);
}

.smart-preview-card__head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
  margin-bottom: 14px;
}

.smart-preview-card__head h3 {
  margin: 0;
  font-size: 22px;
  color: #1f3340;
}

.smart-preview-card__actions {
  display: flex;
  gap: 8px;
}

.smart-history-alerts {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid #f8d9a0;
  border-radius: 14px;
  background: #fff7e8;
}

.smart-history-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #ad6800;
}

.smart-history-tag-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.smart-agent-trace {
  margin-top: 14px;
}

.smart-agent-trace-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.smart-agent-step-list {
  display: grid;
  gap: 10px;
}

.smart-agent-step {
  padding: 12px;
  border: 1px solid #d9ecff;
  border-radius: 14px;
  background: #f4faff;
}

.smart-agent-step-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.smart-agent-step-index {
  font-size: 12px;
  color: #909399;
}

.smart-agent-step-name {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}

.smart-agent-step-summary {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.followup-detail {
  display: grid;
  gap: 18px;
}

.followup-detail__summary {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(34, 51, 66, 0.06), rgba(112, 147, 168, 0.12));
}

.followup-detail__summary h3 {
  margin: 0;
  font-size: 22px;
  color: #1f3340;
}

.followup-detail__summary p {
  margin: 8px 0 0;
  color: #667984;
}

.followup-detail__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.followup-detail__block {
  display: grid;
  gap: 10px;
}

.followup-detail__label {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #6c8091;
}

.followup-detail__empty {
  color: #8a98a3;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.completion-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.completion-switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.completion-hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.completion-hint-block {
  margin-top: 4px;
}

.detail-content {
  line-height: 1.8;
  padding: 14px 16px;
  background: #f5f7fa;
  border-radius: 16px;
  font-size: 14px;
  max-height: 300px;
  overflow-y: auto;
}

.ellipsis-cell {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.table-empty {
  color: #909399;
}

.tooltip-content {
  max-width: 420px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

:deep(.doctor-overflow-tooltip) {
  max-width: 460px;
}

:deep(.followup-tabs .el-tabs__header) {
  margin-bottom: 10px;
}

:deep(.followup-tabs .el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.followup-tabs .el-tabs__item) {
  height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  color: #647681;
}

:deep(.followup-tabs .el-tabs__item.is-active) {
  color: #214055;
  background: rgba(198, 220, 232, 0.42);
}

:deep(.followup-tabs .el-tabs__active-bar) {
  display: none;
}

@media (max-width: 1200px) {
  .followup-stat-grid,
  .smart-panel__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .followup-table {
    min-width: 1200px;
  }

  .followup-table--history {
    min-width: 1020px;
  }
}

@media (max-width: 960px) {
  .followup-hero,
  .followup-panel__toolbar,
  .smart-panel__header,
  .followup-detail__summary,
  .smart-preview-card__head {
    flex-direction: column;
  }

  .followup-hero__actions {
    width: 100%;
  }

  .followup-panel__toolbar p,
  .smart-panel__header p {
    max-width: none;
    text-align: left;
  }

  .completion-switch-row {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .followup-hero,
  .followup-panel {
    padding: 20px;
    border-radius: 22px;
  }

  .followup-stat-grid,
  .smart-panel__summary {
    grid-template-columns: 1fr;
  }

  .followup-hero h1 {
    font-size: 28px;
  }
}
</style>
