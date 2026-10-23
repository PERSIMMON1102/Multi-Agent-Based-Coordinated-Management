<template>
  <section :class="['agent-trace-flow', { 'agent-trace-flow--compact': compact }]">
    <div class="agent-trace-flow__hero">
      <div>
        <div class="agent-trace-flow__eyebrow">{{ eyebrow }}</div>
        <h3>{{ title }}</h3>
      </div>
      <div class="agent-trace-flow__stats">
        <div class="agent-trace-flow__stat">
          <span>步骤</span>
          <strong>{{ normalizedSteps.length }}</strong>
        </div>
        <div v-if="normalizedTools.length > 0" class="agent-trace-flow__stat">
          <span>工具</span>
          <strong>{{ normalizedTools.length }}</strong>
        </div>
        <div v-if="normalizedReferences.length > 0" class="agent-trace-flow__stat">
          <span>证据</span>
          <strong>{{ normalizedReferences.length }}</strong>
        </div>
        <div v-if="normalizedFlags.length > 0" class="agent-trace-flow__stat">
          <span>护栏</span>
          <strong>{{ normalizedFlags.length }}</strong>
        </div>
      </div>
    </div>

    <RichTextBlock
      v-if="summary"
      class="agent-trace-flow__summary"
      :content="summary"
      compact
    />

    <div v-if="stageNodes.length > 0" class="agent-trace-flow__sequence">
      <article
        v-for="(stage, index) in stageNodes"
        :key="stage.key"
        :class="[
          'agent-trace-flow__sequence-stage',
          `agent-trace-flow__sequence-stage--${stage.state}`
        ]"
      >
        <div class="agent-trace-flow__sequence-top">
          <span class="agent-trace-flow__sequence-index">0{{ index + 1 }}</span>
          <span class="agent-trace-flow__sequence-state">{{ stage.stateLabel }}</span>
        </div>
        <strong>{{ stage.label }}</strong>
        <p>{{ stage.description }}</p>
      </article>
    </div>

    <div v-if="hasHierarchy" class="agent-trace-flow__lanes">
      <section class="agent-trace-flow__lane agent-trace-flow__lane--coordinator">
        <div class="agent-trace-flow__lane-head">
          <span class="agent-trace-flow__lane-kicker">阶段 1</span>
          <strong>总览整理</strong>
        </div>
        <div class="agent-trace-flow__lane-card">
          <div class="agent-trace-flow__lane-card-top">
            <div class="agent-trace-flow__lane-heading">
              <span class="agent-trace-flow__agent-badge agent-trace-flow__agent-badge--coordinator">C</span>
              <div class="agent-trace-flow__lane-title">{{ coordinatorLabel }}</div>
            </div>
            <span class="agent-trace-flow__status agent-trace-flow__status--coordinator">处理中</span>
          </div>
          <div class="agent-trace-flow__lane-role">职责：整理问题、归纳重点、汇总结论</div>
          <p class="agent-trace-flow__lane-text">负责梳理当前问题，并把相关信息整理成最终可展示结果。</p>
        </div>
      </section>

      <div v-if="subAgentNodes.length > 0" class="agent-trace-flow__connector">
        <span class="agent-trace-flow__connector-line"></span>
        <span class="agent-trace-flow__connector-label">信息分工</span>
      </div>

      <section v-if="subAgentNodes.length > 0" class="agent-trace-flow__lane agent-trace-flow__lane--agents">
        <div class="agent-trace-flow__lane-head">
          <span class="agent-trace-flow__lane-kicker">阶段 2</span>
          <strong>专业整理</strong>
        </div>
        <div class="agent-trace-flow__lane-grid">
          <article
            v-for="(agent, index) in subAgentNodes"
            :key="`${agent.name}-${index}`"
            :class="['agent-trace-flow__lane-card', agent.themeClass]"
          >
            <div class="agent-trace-flow__lane-card-top">
              <div class="agent-trace-flow__lane-heading">
                <span :class="['agent-trace-flow__agent-badge', agent.badgeClass]">{{ agent.badge }}</span>
                <div>
                  <div class="agent-trace-flow__lane-title">{{ agent.label }}</div>
                  <div class="agent-trace-flow__lane-order">处理顺序 {{ agent.order }}</div>
                </div>
              </div>
              <span :class="['agent-trace-flow__status', `agent-trace-flow__status--${agent.status}`]">{{ agent.statusLabel }}</span>
            </div>
            <div class="agent-trace-flow__lane-role">职责：{{ agent.role }}</div>
            <p class="agent-trace-flow__lane-text">{{ agent.summary }}</p>
          </article>
        </div>
      </section>

      <div v-if="capabilityNodes.length > 0" class="agent-trace-flow__connector">
        <span class="agent-trace-flow__connector-line"></span>
        <span class="agent-trace-flow__connector-label">资料引用</span>
      </div>

      <section v-if="capabilityNodes.length > 0" class="agent-trace-flow__lane agent-trace-flow__lane--capabilities">
        <div class="agent-trace-flow__lane-head">
          <span class="agent-trace-flow__lane-kicker">阶段 3</span>
          <strong>支撑资料</strong>
        </div>
        <div class="agent-trace-flow__lane-grid agent-trace-flow__lane-grid--compact">
          <article
            v-for="(capability, index) in capabilityNodes"
            :key="`${capability.name}-${index}`"
            class="agent-trace-flow__lane-card agent-trace-flow__lane-card--soft"
          >
            <div class="agent-trace-flow__lane-card-top">
              <div class="agent-trace-flow__lane-heading">
                <span class="agent-trace-flow__agent-badge agent-trace-flow__agent-badge--capability">T</span>
                <div>
                  <div class="agent-trace-flow__lane-title">{{ capability.label }}</div>
                  <div class="agent-trace-flow__lane-order">资料顺序 {{ capability.order }}</div>
                </div>
              </div>
              <span :class="['agent-trace-flow__status', `agent-trace-flow__status--${capability.status}`]">{{ capability.statusLabel }}</span>
            </div>
            <div class="agent-trace-flow__lane-role">职责：{{ capability.role }}</div>
            <p class="agent-trace-flow__lane-text">{{ capability.summary }}</p>
          </article>
        </div>
      </section>
    </div>

    <div v-if="normalizedSteps.length > 0" class="agent-trace-flow__timeline">
      <article
        v-for="(step, index) in normalizedSteps"
        :key="buildStepKey(step, index)"
        :class="[
          'agent-trace-flow__node',
          {
            'agent-trace-flow__node--active': hasActiveStep && normalizedActiveIndex === index,
            'agent-trace-flow__node--muted': hasActiveStep && normalizedActiveIndex !== index
          }
        ]"
      >
        <div class="agent-trace-flow__rail">
          <span :class="['agent-trace-flow__dot', { 'agent-trace-flow__dot--active': hasActiveStep && normalizedActiveIndex === index }]">
            {{ step.index || index + 1 }}
          </span>
          <span v-if="index < normalizedSteps.length - 1" class="agent-trace-flow__line"></span>
        </div>
        <div :class="['agent-trace-flow__card', { 'agent-trace-flow__card--active': hasActiveStep && normalizedActiveIndex === index }]">
          <div class="agent-trace-flow__card-head">
            <div>
              <div class="agent-trace-flow__step-index">步骤 {{ step.index || index + 1 }}</div>
              <strong>{{ resolveStepLabel(step, index) }}</strong>
            </div>
            <el-tag
              size="small"
              effect="plain"
              :type="step.observationSummary || step.summary ? 'success' : 'info'"
            >
              {{ step.actionName ? '处理动作' : '处理步骤' }}
            </el-tag>
          </div>
          <p v-if="step.reasoningSummary" class="agent-trace-flow__reason">
            {{ step.reasoningSummary }}
          </p>
          <p v-if="step.observationSummary" class="agent-trace-flow__observation">
            {{ step.observationSummary }}
          </p>
          <p v-if="!step.reasoningSummary && !step.observationSummary && step.summary" class="agent-trace-flow__observation">
            {{ step.summary }}
          </p>
        </div>
      </article>
    </div>
    <el-empty v-else description="暂无可视化步骤" :image-size="72" />

    <div
      v-if="normalizedTools.length > 0 || normalizedFlags.length > 0 || normalizedReferences.length > 0"
      class="agent-trace-flow__support"
    >
      <div v-if="normalizedTools.length > 0" class="agent-trace-flow__support-card">
        <div class="agent-trace-flow__support-title">工具与能力</div>
        <div class="agent-trace-flow__tags">
          <el-tag
            v-for="tool in normalizedTools"
            :key="`tool-${tool}`"
            size="small"
            effect="plain"
            type="info"
          >
            {{ tool }}
          </el-tag>
        </div>
      </div>

      <div v-if="normalizedFlags.length > 0" class="agent-trace-flow__support-card">
        <div class="agent-trace-flow__support-title">安全护栏</div>
        <div class="agent-trace-flow__tags">
          <el-tag
            v-for="flag in normalizedFlags"
            :key="`flag-${flag}`"
            size="small"
            effect="plain"
            type="warning"
          >
            {{ flag }}
          </el-tag>
        </div>
      </div>

      <div v-if="normalizedReferences.length > 0" class="agent-trace-flow__support-card">
        <div class="agent-trace-flow__support-title">参考依据</div>
        <div class="agent-trace-flow__reference-list">
          <button
            v-for="(reference, index) in normalizedReferences"
            :key="buildReferenceKey(reference, index)"
            type="button"
            class="agent-trace-flow__reference"
            @click="$emit('select-reference', reference)"
          >
            <strong>{{ reference.label || reference.title || reference.refId || `引用 ${index + 1}` }}</strong>
            <span>{{ reference.snippet || reference.contentSnippet || reference.sourceType || '点击查看详情' }}</span>
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import RichTextBlock from './RichTextBlock.vue'

const props = defineProps({
  title: {
    type: String,
    default: '处理过程'
  },
  eyebrow: {
    type: String,
    default: 'Process'
  },
  summary: {
    type: String,
    default: ''
  },
  steps: {
    type: Array,
    default: () => []
  },
  tools: {
    type: Array,
    default: () => []
  },
  flags: {
    type: Array,
    default: () => []
  },
  references: {
    type: Array,
    default: () => []
  },
  compact: {
    type: Boolean,
    default: false
  },
  activeStepIndex: {
    type: Number,
    default: -1
  },
  phase: {
    type: String,
    default: ''
  },
  formatStepLabel: {
    type: Function,
    default: null
  },
  coordinatorLabel: {
    type: String,
    default: ''
  }
})

defineEmits(['select-reference'])

const normalizedSteps = computed(() => Array.isArray(props.steps) ? props.steps.filter(Boolean) : [])
const normalizedTools = computed(() => Array.isArray(props.tools) ? props.tools.filter(Boolean) : [])
const normalizedFlags = computed(() => Array.isArray(props.flags) ? props.flags.filter(Boolean) : [])
const normalizedReferences = computed(() => Array.isArray(props.references) ? props.references.filter(Boolean) : [])
const coordinatorLabel = computed(() => props.coordinatorLabel || '信息整理中心')
const normalizedActiveIndex = computed(() => {
  if (!normalizedSteps.value.length) return -1
  const value = Number(props.activeStepIndex)
  if (!Number.isFinite(value) || value < 0) return -1
  return Math.max(0, Math.min(normalizedSteps.value.length - 1, value))
})
const hasActiveStep = computed(() => normalizedActiveIndex.value >= 0)
const subAgentNodes = computed(() => {
  const seen = new Set()
  return normalizedSteps.value
    .filter(step => isSubAgent(step?.actionName || step?.agentName))
    .map((step, index) => {
      const name = step?.actionName || step?.agentName
      if (!name || seen.has(name)) return null
      seen.add(name)
      return {
        name,
        label: resolveStepLabel(step, index),
        summary: step?.observationSummary || step?.reasoningSummary || '已完成本项整理',
        role: describeNodeRole(name, true),
        status: inferNodeStatus(step?.observationSummary || step?.reasoningSummary || ''),
        statusLabel: inferNodeStatusLabel(step?.observationSummary || step?.reasoningSummary || ''),
        order: index + 1,
        badge: buildAgentBadge(name),
        badgeClass: agentBadgeClass(name),
        themeClass: agentThemeClass(name)
      }
    })
    .filter(Boolean)
})
const capabilityNodes = computed(() => {
  const seen = new Set()
  const merged = []
  normalizedTools.value
    .filter(tool => !isSubAgent(tool))
    .forEach(tool => {
      if (!tool || seen.has(tool)) return
      seen.add(tool)
      merged.push({
        name: tool,
        label: tool,
        summary: '按需引用相关资料',
        role: describeNodeRole(tool, false),
        status: 'active',
        statusLabel: '已调用',
        order: merged.length + 1
      })
    })
  normalizedSteps.value
    .filter(step => !isSubAgent(step?.actionName || step?.agentName))
    .forEach((step, index) => {
      const name = step?.actionName || step?.agentName
      if (!name || seen.has(name)) return
      seen.add(name)
      merged.push({
        name,
        label: resolveStepLabel(step, index),
        summary: step?.observationSummary || step?.reasoningSummary || '已完成资料整理',
        role: describeNodeRole(name, false),
        status: inferNodeStatus(step?.observationSummary || step?.reasoningSummary || ''),
        statusLabel: inferNodeStatusLabel(step?.observationSummary || step?.reasoningSummary || ''),
        order: merged.length + 1
      })
    })
  return merged
})
const hasHierarchy = computed(() => subAgentNodes.value.length > 0 || capabilityNodes.value.length > 0)
const stageNodes = computed(() => {
  const hasSteps = normalizedSteps.value.length > 0
  const hasAgents = subAgentNodes.value.length > 0
  const hasCapabilities = capabilityNodes.value.length > 0 || normalizedTools.value.length > 0
  const hasSummary = props.phase === 'done'
    || Boolean(String(props.summary || '').trim())
    || normalizedFlags.value.length > 0
    || normalizedReferences.value.length > 0
    || (hasSteps && props.phase === 'answering')

  const currentStageKey = inferCurrentStageKey()

  return [
    buildStageNode('decompose', '问题整理', '先识别当前问题并形成初步处理思路。', hasSteps, currentStageKey),
    buildStageNode('delegate', '重点分工', '将不同内容分开整理，确保结论更完整。', hasAgents, currentStageKey),
    buildStageNode('invoke', '资料引用', '结合患者资料、参考依据和评估信息完善内容。', hasCapabilities, currentStageKey),
    buildStageNode('summarize', '结果汇总', '整理各部分结果并生成最终展示内容。', hasSummary, currentStageKey)
  ]
})

function resolveStepLabel(step, index) {
  if (typeof props.formatStepLabel === 'function') {
    return props.formatStepLabel(step, index)
  }
  return step?.actionName || step?.agentName || step?.title || `步骤 ${index + 1}`
}

function buildStepKey(step, index) {
  return `${step?.index || index + 1}-${step?.actionName || step?.agentName || 'step'}`
}

function buildReferenceKey(reference, index) {
  return `${reference?.refId || reference?.title || reference?.label || 'reference'}-${index}`
}

function isSubAgent(name) {
  return typeof name === 'string' && name.endsWith('_agent')
}

function describeNodeRole(name, isAgentNode) {
  const roleMap = {
    patient_profile_agent: '负责患者画像、病史上下文和档案差异理解',
    risk_assessment_agent: '负责风险判断、高危识别和评估结论生成',
    evidence_support_agent: '负责证据检索、知识依据补充和内容校对',
    patient_context: '读取本地患者资料、病史和近期待办信息',
    medplum_patient_insight: '补充云端健康档案与健康指标信息',
    risk_assessment: '运行规则评估并输出风险等级与依据',
    medical_evidence_search: '检索慢病管理知识和指南证据',
    knowledge_graph_support: '补充知识内容关联和主题说明'
  }
  if (roleMap[name]) return roleMap[name]
  return isAgentNode ? '负责完成对应内容整理' : '提供相关资料支撑'
}

function inferNodeStatus(summary) {
  const text = String(summary || '')
  if (!text) return 'idle'
  if (text.includes('不可用') || text.includes('降级') || text.includes('失败') || text.includes('跳过')) return 'degraded'
  return 'active'
}

function inferNodeStatusLabel(summary) {
  const status = inferNodeStatus(summary)
  if (status === 'degraded') return '降级执行'
  if (status === 'idle') return '待命'
  return '已执行'
}

function inferCurrentStageKey() {
  if (props.phase === 'done') return 'summarize'
  if (props.phase === 'answering') return 'summarize'
  if (capabilityNodes.value.length > 0 || normalizedTools.value.length > 0) return 'invoke'
  if (subAgentNodes.value.length > 0) return 'delegate'
  if (normalizedSteps.value.length > 0 || props.phase === 'thinking') return 'decompose'
  return ''
}

function buildStageNode(key, label, description, completed, currentStageKey) {
  const state = completed
    ? (currentStageKey === key && props.phase !== 'done' ? 'active' : 'done')
    : (currentStageKey === key ? 'active' : 'pending')
  return {
    key,
    label,
    description,
    state,
    stateLabel:
      state === 'done'
        ? '完成'
        : state === 'active'
          ? '进行中'
          : '待触发'
  }
}

function buildAgentBadge(name) {
  const words = String(name || '')
    .replace(/_agent$/i, '')
    .split('_')
    .filter(Boolean)
  if (words.length === 0) return 'A'
  return words.map(word => word[0]?.toUpperCase() || '').join('').slice(0, 2)
}

function agentBadgeClass(name) {
  const classMap = {
    patient_profile_agent: 'agent-trace-flow__agent-badge--profile',
    risk_assessment_agent: 'agent-trace-flow__agent-badge--risk',
    evidence_support_agent: 'agent-trace-flow__agent-badge--evidence'
  }
  return classMap[name] || ''
}

function agentThemeClass(name) {
  const classMap = {
    patient_profile_agent: 'agent-trace-flow__lane-card--profile',
    risk_assessment_agent: 'agent-trace-flow__lane-card--risk',
    evidence_support_agent: 'agent-trace-flow__lane-card--evidence'
  }
  return classMap[name] || ''
}
</script>

<style scoped>
.agent-trace-flow {
  display: grid;
  gap: 14px;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(51, 65, 85, 0.1);
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.92), rgba(255, 255, 255, 0.98));
}

.agent-trace-flow--compact {
  padding: 14px;
  border-radius: 18px;
  gap: 12px;
}

.agent-trace-flow__hero,
.agent-trace-flow__card-head,
.agent-trace-flow__tags,
.agent-trace-flow__stats {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.agent-trace-flow__hero,
.agent-trace-flow__card-head {
  align-items: flex-start;
  justify-content: space-between;
}

.agent-trace-flow__eyebrow,
.agent-trace-flow__step-index,
.agent-trace-flow__support-title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.agent-trace-flow h3 {
  margin: 6px 0 0;
  font-size: 18px;
  line-height: 1.3;
  color: #1e293b;
}

.agent-trace-flow__stats {
  justify-content: flex-end;
}

.agent-trace-flow__stat {
  min-width: 72px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.agent-trace-flow__stat span {
  display: block;
  font-size: 11px;
  color: #64748b;
}

.agent-trace-flow__stat strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  color: #0f172a;
}

.agent-trace-flow__summary {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.04);
  color: #475569;
}

.agent-trace-flow__sequence {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.agent-trace-flow__sequence-stage {
  padding: 12px 13px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.78);
}

.agent-trace-flow__sequence-stage--done {
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.98), rgba(255, 255, 255, 0.92));
  border-color: rgba(16, 185, 129, 0.18);
}

.agent-trace-flow__sequence-stage--active {
  background:
    linear-gradient(135deg, rgba(239, 246, 255, 0.98), rgba(255, 255, 255, 0.94)),
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.12), transparent 42%);
  border-color: rgba(59, 130, 246, 0.24);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.08);
}

.agent-trace-flow__sequence-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.agent-trace-flow__sequence-index {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #94a3b8;
}

.agent-trace-flow__sequence-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.76);
  color: #64748b;
  font-size: 10px;
  font-weight: 800;
}

.agent-trace-flow__sequence-stage--done .agent-trace-flow__sequence-state {
  background: rgba(220, 252, 231, 0.86);
  color: #0f766e;
}

.agent-trace-flow__sequence-stage--active .agent-trace-flow__sequence-state {
  background: rgba(219, 234, 254, 0.92);
  color: #1d4ed8;
}

.agent-trace-flow__sequence-stage strong {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: #0f172a;
}

.agent-trace-flow__sequence-stage p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: #64748b;
}

.agent-trace-flow__lanes {
  display: grid;
  gap: 12px;
}

.agent-trace-flow__lane {
  display: grid;
  gap: 10px;
}

.agent-trace-flow__lane-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-trace-flow__lane-kicker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.agent-trace-flow__lane-head strong {
  font-size: 14px;
  color: #1e293b;
}

.agent-trace-flow__lane-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px;
}

.agent-trace-flow__lane-grid--compact {
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
}

.agent-trace-flow__connector {
  display: grid;
  justify-items: center;
  gap: 6px;
  margin-top: -2px;
}

.agent-trace-flow__connector-line {
  width: 2px;
  height: 18px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(59, 130, 246, 0.4), rgba(16, 185, 129, 0.32));
}

.agent-trace-flow__connector-label {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.agent-trace-flow__lane-card {
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.agent-trace-flow__lane--coordinator .agent-trace-flow__lane-card {
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.98), rgba(255, 255, 255, 0.98));
  border-color: rgba(59, 130, 246, 0.22);
}

.agent-trace-flow__lane--agents .agent-trace-flow__lane-card {
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.98), rgba(255, 255, 255, 0.98));
  border-color: rgba(16, 185, 129, 0.18);
}

.agent-trace-flow__lane-card--profile {
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.98), rgba(255, 255, 255, 0.98));
  border-color: rgba(59, 130, 246, 0.22);
}

.agent-trace-flow__lane-card--risk {
  background: linear-gradient(135deg, rgba(255, 247, 237, 0.98), rgba(255, 255, 255, 0.98));
  border-color: rgba(249, 115, 22, 0.22);
}

.agent-trace-flow__lane-card--evidence {
  background: linear-gradient(135deg, rgba(250, 245, 255, 0.98), rgba(255, 255, 255, 0.98));
  border-color: rgba(168, 85, 247, 0.2);
}

.agent-trace-flow__lane-card--soft {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.98));
}

.agent-trace-flow__lane-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.agent-trace-flow__lane-card-top {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 10px;
}

.agent-trace-flow__lane-heading {
  display: flex;
  align-items: start;
  gap: 10px;
}

.agent-trace-flow__agent-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #38bdf8);
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(15, 118, 110, 0.18);
}

.agent-trace-flow__agent-badge--coordinator {
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.18);
}

.agent-trace-flow__agent-badge--capability {
  background: linear-gradient(135deg, #64748b, #94a3b8);
  box-shadow: 0 8px 18px rgba(100, 116, 139, 0.18);
}

.agent-trace-flow__agent-badge--profile {
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.18);
}

.agent-trace-flow__agent-badge--risk {
  background: linear-gradient(135deg, #ea580c, #fb923c);
  box-shadow: 0 8px 18px rgba(234, 88, 12, 0.2);
}

.agent-trace-flow__agent-badge--evidence {
  background: linear-gradient(135deg, #7c3aed, #c084fc);
  box-shadow: 0 8px 18px rgba(124, 58, 237, 0.18);
}

.agent-trace-flow__lane-role {
  margin-top: 7px;
  font-size: 11px;
  line-height: 1.65;
  color: #64748b;
}

.agent-trace-flow__lane-order {
  margin-top: 2px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #94a3b8;
}

.agent-trace-flow__lane-text {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
}

.agent-trace-flow__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.agent-trace-flow__status--coordinator,
.agent-trace-flow__status--active {
  color: #0f766e;
  background: rgba(220, 252, 231, 0.88);
}

.agent-trace-flow__status--degraded {
  color: #9a6700;
  background: rgba(254, 240, 138, 0.72);
}

.agent-trace-flow__status--idle {
  color: #64748b;
  background: rgba(226, 232, 240, 0.82);
}

.agent-trace-flow__timeline {
  display: grid;
  gap: 10px;
}

.agent-trace-flow__node {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.agent-trace-flow__node--muted {
  opacity: 0.6;
}

.agent-trace-flow__node--active {
  transform: translateX(2px);
}

.agent-trace-flow__rail {
  display: grid;
  justify-items: center;
  align-content: start;
}

.agent-trace-flow__dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: linear-gradient(135deg, #0f766e, #38bdf8);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(15, 118, 110, 0.22);
}

.agent-trace-flow__dot--active {
  background: linear-gradient(135deg, #2563eb, #22c55e);
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);
}

.agent-trace-flow__line {
  width: 2px;
  min-height: 100%;
  margin-top: 6px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(56, 189, 248, 0.46), rgba(148, 163, 184, 0.12));
}

.agent-trace-flow__card,
.agent-trace-flow__support-card,
.agent-trace-flow__reference {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.94);
}

.agent-trace-flow__card {
  padding: 14px 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.05);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.agent-trace-flow__card--active {
  border-color: rgba(37, 99, 235, 0.32);
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.12);
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.96), rgba(255, 255, 255, 0.98));
}

.agent-trace-flow__card strong,
.agent-trace-flow__reference strong {
  color: #0f172a;
  font-size: 14px;
}

.agent-trace-flow__reason,
.agent-trace-flow__observation,
.agent-trace-flow__reference span {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: #475569;
}

.agent-trace-flow__reason {
  color: #0f766e;
}

.agent-trace-flow__support {
  display: grid;
  gap: 10px;
}

.agent-trace-flow__support-card {
  padding: 14px 16px;
}

.agent-trace-flow__tags {
  margin-top: 10px;
}

.agent-trace-flow__reference-list {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.agent-trace-flow__reference {
  width: 100%;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.agent-trace-flow__reference:hover {
  border-color: rgba(56, 189, 248, 0.35);
  box-shadow: 0 10px 22px rgba(56, 189, 248, 0.1);
  transform: translateY(-1px);
}

@media (max-width: 768px) {
  .agent-trace-flow__hero {
    flex-direction: column;
  }

  .agent-trace-flow__sequence {
    grid-template-columns: 1fr;
  }

  .agent-trace-flow__stats {
    justify-content: flex-start;
  }

  .agent-trace-flow__node {
    grid-template-columns: 24px minmax(0, 1fr);
    gap: 10px;
  }

  .agent-trace-flow__card-head {
    flex-direction: column;
  }
}
</style>
