<template>
  <div v-loading="loading" class="patient-detail-shell">
    <el-page-header class="patient-detail-back" @back="$router.back()" />

    <section class="patient-detail-hero">
      <div class="patient-detail-hero__copy">
        <span class="patient-detail-hero__eyebrow">患者健康档案</span>
        <h1>{{ patient.name || '患者详情' }}</h1>
        <div class="patient-detail-hero__chips">
          <el-tag v-if="patient.gender" effect="plain">{{ patient.gender }}</el-tag>
          <el-tag v-if="patient.birthDate" effect="plain" type="info">{{ patient.birthDate }}</el-tag>
          <el-tag v-if="patient.fhirId" effect="plain" type="warning">云端档案编号: {{ patient.fhirId }}</el-tag>
        </div>
        <p>把风险识别、医生干预、异常恢复、随访计划和报告生成收拢到一个入口里，先处理当前关键事项，再按需下钻明细。</p>
      </div>

      <div class="patient-detail-hero__stats">
        <div class="patient-detail-stat">
          <span>评估记录</span>
          <strong>{{ assessments.length }}</strong>
        </div>
        <div class="patient-detail-stat">
          <span>随访计划</span>
          <strong>{{ followUps.length }}</strong>
        </div>
        <div class="patient-detail-stat">
          <span>健康记录</span>
          <strong>{{ healthRecords.length }}</strong>
        </div>
        <div class="patient-detail-stat">
          <span>纸质病历</span>
          <strong>{{ paperCaseRecords.length }}</strong>
        </div>
        <div class="patient-detail-stat">
          <span>异常作业</span>
          <strong>{{ attentionClinicalJobs.length }}</strong>
        </div>
        <div class="patient-detail-stat">
          <span>医生报告</span>
          <strong>{{ doctorReports.length }}</strong>
        </div>
        <div class="patient-detail-stat">
          <span>云端记录</span>
          <strong>{{ cloudPaperCaseResourceCount }}</strong>
        </div>
      </div>
    </section>

    <el-card shadow="never" class="patient-detail-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="patient-detail-tabs">
        <el-tab-pane label="医生干预" name="intervention">
          <div class="intervention-workbench">
            <section class="intervention-banner">
              <div>
                <span class="paper-case-panel__eyebrow">医生干预</span>
                <h3>当前处理入口</h3>
                <p>{{ interventionHeadline }}</p>
              </div>
              <div class="patient-detail-actions">
                <el-button
                  type="primary"
                  :loading="reportGenerating"
                  :disabled="!latestAssessment"
                  @click="generateDoctorReport"
                >
                  {{ reportActionLabel }}
                </el-button>
                <el-button
                  :disabled="!activeFollowUp && !latestFollowUp"
                  @click="switchToTab('followups')"
                >
                  查看随访计划
                </el-button>
                <el-button
                  v-if="primaryPendingPaperCase"
                  type="warning"
                  plain
                  @click="openPaperCaseReview(primaryPendingPaperCase)"
                >
                  处理病历审核
                </el-button>
                <el-button
                  v-else-if="primaryRecoverablePaperCase"
                  type="danger"
                  plain
                  @click="openPaperCaseReview(primaryRecoverablePaperCase)"
                >
                  处理恢复阻塞
                </el-button>
                <el-button
                  v-else-if="primaryRetryableClinicalJob"
                  type="danger"
                  plain
                  :loading="clinicalJobActionJobId === primaryRetryableClinicalJob.id"
                  @click="requeueClinicalJob(primaryRetryableClinicalJob)"
                >
                  重试异常导入
                </el-button>
              </div>
            </section>

            <div class="intervention-kpi-grid">
              <div class="intervention-kpi">
                <span>最新风险</span>
                <strong>{{ latestAssessment ? riskLabel(latestAssessment.riskLevel) : '待评估' }}</strong>
                <p>{{ latestAssessment ? `${diseaseLabel(latestAssessment.diseaseType)} · ${formatDate(latestAssessment.createdAt)}` : '暂无风险识别结果' }}</p>
              </div>
              <div class="intervention-kpi">
                <span>默认动作</span>
                <strong>{{ activeFollowUp || latestFollowUp ? '已自动生成随访计划' : '尚未生成随访计划' }}</strong>
                <p>{{ activeFollowUp || latestFollowUp ? followUpSummaryText : '风险识别后未看到随访计划，请优先核对当前流程' }}</p>
              </div>
              <div class="intervention-kpi">
                <span>人工待处理</span>
                <strong>{{ pendingInterventionCount }}</strong>
                <p>{{ pendingInterventionSummary }}</p>
              </div>
              <div class="intervention-kpi">
                <span>医生报告</span>
                <strong>{{ latestDoctorReport ? (latestDoctorReport.freshnessLabel || formatDate(latestDoctorReport.generatedAt)) : '尚未生成' }}</strong>
                <p>{{ doctorReportSummaryText }}</p>
              </div>
            </div>

            <section class="intervention-section">
              <div class="intervention-section__head">
                <div>
                  <span class="paper-case-panel__eyebrow">病例工作区</span>
                  <h3>当前处理概览</h3>
                </div>
                <p>{{ mainWorkflowSummary }}</p>
              </div>
              <div class="workflow-step-grid">
                <article
                  v-for="step in interventionWorkflowSteps"
                  :key="step.key"
                  class="workflow-step-card"
                  :data-tone="step.tone"
                >
                  <div class="workflow-step-card__meta">
                    <span>{{ step.title }}</span>
                    <el-tag size="small" effect="plain" :type="step.tagType">{{ step.status }}</el-tag>
                  </div>
                  <strong>{{ step.metric }}</strong>
                  <p>{{ step.description }}</p>
                  <el-button
                    v-if="step.actionLabel"
                    link
                    type="primary"
                    @click="handleWorkbenchAction(step.actionKind)"
                  >
                    {{ step.actionLabel }}
                  </el-button>
                </article>
              </div>
            </section>

            <section class="intervention-section">
              <div class="intervention-section__head">
                <div>
                  <span class="paper-case-panel__eyebrow">病例工作区</span>
                  <h3>近期处理记录</h3>
                </div>
                <p>把评估、随访、报告、恢复动作按时间放在一条线上，方便医生快速回看最近发生的处理事项。</p>
              </div>
              <div class="care-timeline">
                <article
                  v-for="item in careTimeline"
                  :key="item.key"
                  class="care-timeline__item"
                  :data-tone="item.tone"
                >
                  <div class="care-timeline__dot"></div>
                  <div class="care-timeline__body">
                    <div class="care-timeline__meta">
                      <strong>{{ item.title }}</strong>
                      <span>{{ item.timeLabel }}</span>
                    </div>
                    <p>{{ item.description }}</p>
                  </div>
                </article>
              </div>
            </section>

            <section class="intervention-section">
              <div class="intervention-section__head">
                <div>
                  <span class="paper-case-panel__eyebrow">病例工作区</span>
                  <h3>异常恢复情况</h3>
                </div>
                <p>把导入异常、人工确认或重试、恢复成功、后续评估继续放到一条连续流程里展示。</p>
              </div>
              <div class="recovery-story-grid">
                <article
                  v-for="step in recoveryStorySteps"
                  :key="step.key"
                  class="recovery-story-card"
                  :data-tone="step.tone"
                >
                  <div class="recovery-story-card__meta">
                    <span>{{ step.title }}</span>
                    <el-tag size="small" effect="plain" :type="step.tagType">{{ step.status }}</el-tag>
                  </div>
                  <strong>{{ step.metric }}</strong>
                  <p>{{ step.description }}</p>
                  <el-button
                    v-if="step.actionLabel"
                    link
                    type="primary"
                    @click="handleWorkbenchAction(step.actionKind)"
                  >
                    {{ step.actionLabel }}
                  </el-button>
                </article>
              </div>
            </section>

            <div class="intervention-detail-grid">
              <article class="intervention-detail-card">
                <span class="paper-case-panel__eyebrow">风险转随访</span>
                <h3>风险评估后的默认动作</h3>
                <div v-if="latestAssessment" class="intervention-detail-card__headline">
                  <el-tag :type="riskTagType(latestAssessment.riskLevel)">{{ riskLabel(latestAssessment.riskLevel) }}</el-tag>
                  <strong>{{ diseaseLabel(latestAssessment.diseaseType) }}</strong>
                </div>
                <p>{{ latestAssessment?.assessmentDetail || '暂无风险评估结果，需先完成数据导入或重新触发评估。' }}</p>
                <div v-if="hasHistoryAlerts(latestAssessment)" class="history-alerts">
                  <el-tag
                    v-for="(alert, i) in latestAssessment.historyAlerts"
                    :key="`intervention-history-${latestAssessment.id || i}-${i}`"
                    type="warning"
                    effect="plain"
                    size="small"
                  >
                    {{ alert }}
                  </el-tag>
                </div>
                <div class="intervention-inline-note">
                  {{ defaultActionNarrative }}
                </div>
                <div class="patient-detail-actions">
                  <el-button link type="primary" @click="switchToTab('assessments')">查看风险评估</el-button>
                  <el-button link type="primary" @click="switchToTab('followups')">查看随访计划</el-button>
                </div>
              </article>

              <article class="intervention-detail-card">
                <span class="paper-case-panel__eyebrow">人工处理</span>
                <h3>当前待医生处理</h3>
                <ul class="intervention-task-list">
                  <li v-if="primaryPendingPaperCase">
                    纸质病历存在编码或归档待确认：{{ primaryPendingPaperCase.originalFileName || `病历 #${primaryPendingPaperCase.id}` }}
                  </li>
                  <li v-if="primaryRecoverablePaperCase">
                    纸质病历归档异常待恢复：{{ primaryRecoverablePaperCase.originalFileName || `病历 #${primaryRecoverablePaperCase.id}` }}
                  </li>
                  <li v-if="primaryRetryableClinicalJob">
                    临床作业可重新入队：{{ formatClinicalJobType(primaryRetryableClinicalJob.jobType) }} / {{ primaryRetryableClinicalJob.sourceFileName || `作业 #${primaryRetryableClinicalJob.id}` }}
                  </li>
                  <li v-if="!hasPendingIntervention">
                    当前没有阻塞流程的人工任务，可继续关注随访执行和新数据回流。
                  </li>
                </ul>
                <div class="intervention-inline-note">
                  {{ pendingInterventionSummary }}
                </div>
                <div class="patient-detail-actions">
                  <el-button
                    v-if="hasPendingIntervention"
                    link
                    type="primary"
                    @click="handleWorkbenchAction('recovery')"
                  >
                    进入恢复处理
                  </el-button>
                  <el-button v-else link type="primary" @click="switchToTab('records')">查看最新数据</el-button>
                </div>
              </article>

              <article class="intervention-detail-card">
                <span class="paper-case-panel__eyebrow">医生报告</span>
                <h3>报告查看与更新</h3>
                <template v-if="latestDoctorReport">
                  <div class="intervention-detail-card__headline">
                    <el-tag :type="doctorReportTagType">{{ doctorReportStatusLabel }}</el-tag>
                    <strong>{{ latestDoctorReport.title || '医生评估报告' }}</strong>
                  </div>
                  <div class="paper-case-sub">{{ formatDate(latestDoctorReport.generatedAt) }}</div>
                  <div v-if="doctorReportNextAction" class="report-purpose-note">
                    {{ doctorReportNextAction }}
                  </div>
                  <div v-if="doctorReportStaleReasonText" class="intervention-inline-note">
                    {{ doctorReportStaleReasonText }}
                  </div>
                  <div class="report-purpose-note">
                    这份报告用于给医生快速回看本次风险判断、干预重点和随访建议，也可作为本次处理记录。
                  </div>
                  <RichTextBlock class="report-preview" :content="latestDoctorReport.content || '当前报告还没有摘要内容。'" compact />
                </template>
                <template v-else>
                  <p>系统已经把风险评估后的默认动作固定为“自动生成随访计划”，医生在此处只需补一次明确的报告生成动作。</p>
                  <div class="intervention-inline-note">报告查看与更新统一放在这里，便于按当前患者情况连续处理。</div>
                </template>
                <div class="patient-detail-actions">
                  <el-button
                    type="primary"
                    plain
                    :loading="reportGenerating"
                    :disabled="!latestAssessment"
                    @click="generateDoctorReport"
                  >
                    {{ reportActionLabel }}
                  </el-button>
                  <el-button
                    v-if="latestDoctorReport"
                    link
                    type="primary"
                    @click="openDoctorReportDetail(latestDoctorReport)"
                  >
                    查看报告
                  </el-button>
                  <el-button link type="primary" @click="switchToTab('assessments')">回看风险依据</el-button>
                </div>
              </article>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="基本信息" name="info">
          <div class="detail-grid">
            <div class="detail-item"><span>姓名</span><strong>{{ patient.name || '-' }}</strong></div>
            <div class="detail-item"><span>性别</span><strong>{{ patient.gender || '-' }}</strong></div>
            <div class="detail-item"><span>出生日期</span><strong>{{ patient.birthDate || '-' }}</strong></div>
            <div class="detail-item"><span>电话</span><strong>{{ patient.phone || '-' }}</strong></div>
            <div class="detail-item"><span>身份证号</span><strong>{{ patient.idCard || '-' }}</strong></div>
            <div class="detail-item"><span>地址</span><strong>{{ patient.address || '-' }}</strong></div>
            <div class="detail-item"><span>紧急联系人</span><strong>{{ patient.emergencyContact || '-' }}</strong></div>
            <div class="detail-item"><span>紧急电话</span><strong>{{ patient.emergencyPhone || '-' }}</strong></div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="病史档案" name="history">
          <div class="detail-history-grid">
            <div class="detail-item"><span>既往患病情况</span><strong>{{ displayHistory(patient.medicalHistory?.pastDiseases) }}</strong></div>
            <div class="detail-item"><span>手术史</span><strong>{{ displayHistory(patient.medicalHistory?.surgicalHistory) }}</strong></div>
            <div class="detail-item"><span>药物过敏</span><strong>{{ displayHistory(patient.medicalHistory?.drugAllergies) }}</strong></div>
            <div class="detail-item"><span>食物过敏</span><strong>{{ displayHistory(patient.medicalHistory?.foodAllergies) }}</strong></div>
            <div class="detail-item"><span>预防接种</span><strong>{{ displayHistory(patient.medicalHistory?.immunizationHistory) }}</strong></div>
            <div class="detail-item"><span>家族史</span><strong>{{ displayHistory(patient.medicalHistory?.familyHistory) }}</strong></div>
            <div class="detail-item detail-item--wide"><span>其他备注</span><strong>{{ displayHistory(patient.medicalHistory?.otherNotes) }}</strong></div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="风险评估" name="assessments">
          <el-table :data="assessments" stripe class="detail-table">
            <el-table-column prop="diseaseType" label="疾病" width="130">
              <template #default="{ row }">{{ diseaseLabel(row.diseaseType) }}</template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险等级" width="100">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="评分" width="80" />
            <el-table-column prop="assessmentDetail" label="评估详情" min-width="220" show-overflow-tooltip />
            <el-table-column label="病史提醒" width="260">
              <template #default="{ row }">
                <div v-if="hasHistoryAlerts(row)" class="history-alerts">
                  <el-tag
                    v-for="(alert, i) in row.historyAlerts"
                    :key="`patient-detail-history-${row.id || row.createdAt}-${i}`"
                    type="warning"
                    effect="plain"
                    size="small"
                  >
                    {{ alert }}
                  </el-tag>
                </div>
                <span v-else class="history-empty">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="recommendations" label="建议" min-width="260">
              <template #default="{ row }">
                <ul class="recommendation-list">
                  <li v-for="(r, i) in row.recommendations" :key="i">{{ r }}</li>
                </ul>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="150">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="assessments.length === 0" description="暂无评估记录" />
        </el-tab-pane>

        <el-tab-pane label="随访计划" name="followups">
          <el-table :data="followUps" stripe class="detail-table">
            <el-table-column prop="diseaseType" label="疾病" width="130">
              <template #default="{ row }">{{ diseaseLabel(row.diseaseType) }}</template>
            </el-table-column>
            <el-table-column prop="frequency" label="频次" width="100">
              <template #default="{ row }">{{ freqLabel(row.frequency) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nextFollowDate" label="下次随访" width="120" />
            <el-table-column prop="planContent" label="计划内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="checkItems" label="检查项目" min-width="280">
              <template #default="{ row }">
                <div class="check-tag-list">
                  <el-tag v-for="(item, i) in row.checkItems" :key="i" size="small" effect="plain">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="followUps.length === 0" description="当前没有随访计划" />
        </el-tab-pane>

        <el-tab-pane label="健康记录" name="records">
          <el-table :data="healthRecords" stripe class="detail-table">
            <el-table-column prop="recordDate" label="日期" width="110" />
            <el-table-column label="血压" width="110">
              <template #default="{ row }">
                <span v-if="row.systolicBp">{{ row.systolicBp }}/{{ row.diastolicBp }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fastingGlucose" label="空腹血糖" width="90" />
            <el-table-column prop="hba1c" label="HbA1c" width="80" />
            <el-table-column prop="bmi" label="BMI" width="70" />
            <el-table-column prop="egfr" label="eGFR" width="70" />
            <el-table-column prop="fev1Percent" label="FEV1%" width="80" />
            <el-table-column prop="ejectionFraction" label="EF%" width="70" />
            <el-table-column prop="ldlCholesterol" label="LDL-C" width="80" />
          </el-table>
          <el-empty v-if="healthRecords.length === 0" description="暂无健康记录" />
        </el-tab-pane>

        <el-tab-pane label="纸质病历" name="papercases">
          <div class="paper-case-panel">
            <div class="paper-case-panel__head">
              <div>
                <span class="paper-case-panel__eyebrow">纸质病历</span>
                <h3>纸质病历记录</h3>
                <p>查看识别后的病历摘要、标准疾病编码状态、云端归档状态，并可下载原始病历文件。</p>
              </div>
            </div>

            <el-table :data="paperCaseRecords" stripe class="detail-table" empty-text="暂无纸质病历记录">
              <el-table-column prop="recordDate" label="病历日期" width="120">
                <template #default="{ row }">{{ row.recordDate || '--' }}</template>
              </el-table-column>
              <el-table-column prop="originalFileName" label="原文件" min-width="220" show-overflow-tooltip />
              <el-table-column prop="preliminaryDiagnosis" label="初步诊断" min-width="220" show-overflow-tooltip />
              <el-table-column label="标准疾病编码（ICD-11）" min-width="220">
                <template #default="{ row }">
                  <div class="paper-case-main">{{ formatPaperCaseIcd(row) }}</div>
                  <div class="paper-case-sub">{{ formatPaperCaseIcdStatus(row) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="云端归档" min-width="200">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="paperCaseMedplumTagType(row.medplumSyncStatus)">
                    {{ formatPaperCaseMedplumStatus(row.medplumSyncStatus) }}
                  </el-tag>
                  <div class="paper-case-sub">{{ row.medplumSyncMessage || '--' }}</div>
                </template>
              </el-table-column>
              <el-table-column label="处理记录" min-width="260">
                <template #default="{ row }">
                  <div v-if="row.job" class="paper-case-job">
                    <div class="paper-case-job__line">
                      <el-tag size="small" effect="plain" :type="paperCaseJobTagType(row.job.jobStatus)">
                        {{ formatPaperCaseJobStatus(row.job.jobStatus) }}
                      </el-tag>
                      <span>{{ formatPaperCaseJobStage(row.job.lastStage) }}</span>
                    </div>
                    <div class="paper-case-sub">记录编号 {{ row.job.correlationId || '--' }}</div>
                    <div class="paper-case-sub">尝试 {{ formatPaperCaseJobAttempt(row.job) }}</div>
                    <div v-if="row.job.lastError" class="paper-case-job__error">{{ row.job.lastError }}</div>
                  </div>
                  <span v-else class="paper-case-sub">暂无异步作业信息</span>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="canReviewPaperCase(row)"
                    link
                    type="warning"
                    @click="openPaperCaseReview(row)"
                  >
                    去审核
                  </el-button>
                  <el-button
                    v-if="canRecoverPaperCase(row)"
                    link
                    type="danger"
                    @click="openPaperCaseReview(row)"
                  >
                    去恢复
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    :loading="downloadingPaperCaseId === row.id"
                    @click="downloadPaperCase(row)"
                  >
                    下载原件
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="paperCaseRecords.length > 0" class="paper-case-summary-grid">
              <div class="detail-item">
                <span>待确认标准编码</span>
                <strong>{{ pendingReviewPaperCaseCount }}</strong>
              </div>
              <div class="detail-item">
                <span>已完成云端归档</span>
                <strong>{{ syncedPaperCaseCount }}</strong>
              </div>
              <div class="detail-item">
                <span>最近归档时间</span>
                <strong>{{ latestPaperCaseSyncTime }}</strong>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="临床作业" name="clinicaljobs">
          <div class="paper-case-panel" v-loading="clinicalJobsLoading">
            <div class="paper-case-panel__head">
              <div>
                <span class="paper-case-panel__eyebrow">临床作业</span>
                <h3>患者临床作业异常</h3>
                <p>{{ clinicalJobPanelDescription }}</p>
              </div>
              <div class="patient-detail-actions">
                <el-button size="small" @click="loadClinicalJobs(true)">刷新作业</el-button>
              </div>
            </div>

            <div class="paper-case-summary-grid" v-if="patientClinicalJobs.length > 0">
              <div class="detail-item">
                <span>待审核</span>
                <strong>{{ patientClinicalJobSummary.reviewPendingCount }}</strong>
              </div>
              <div class="detail-item">
                <span>失败</span>
                <strong>{{ patientClinicalJobSummary.failedCount }}</strong>
              </div>
              <div class="detail-item">
                <span>死信</span>
                <strong>{{ patientClinicalJobSummary.deadLetterCount }}</strong>
              </div>
              <div class="detail-item">
                <span>可重试</span>
                <strong>{{ patientClinicalJobSummary.retryableCount }}</strong>
              </div>
            </div>

            <el-table :data="patientClinicalJobs" stripe class="detail-table" empty-text="当前患者暂无需要处理的临床作业">
              <el-table-column label="状态" width="112">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="paperCaseJobTagType(row.jobStatus)">
                    {{ formatPaperCaseJobStatus(row.jobStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="作业类型" min-width="140">
                <template #default="{ row }">{{ formatClinicalJobType(row.jobType) }}</template>
              </el-table-column>
              <el-table-column label="当前阶段" min-width="160">
                <template #default="{ row }">{{ formatPaperCaseJobStage(row.lastStage) }}</template>
              </el-table-column>
              <el-table-column label="来源文件" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.sourceFileName || '--' }}</template>
              </el-table-column>
              <el-table-column label="错误信息" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">{{ row.lastError || '--' }}</template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="row.jobType === 'paper_case_ingest'"
                    link
                    type="warning"
                    @click="openPaperCaseReviewByJob(row)"
                  >
                    去病历处理
                  </el-button>
                  <el-button
                    v-if="canRequeueClinicalJob(row)"
                    link
                    type="danger"
                    :loading="clinicalJobActionJobId === row.id"
                    @click="requeueClinicalJob(row)"
                  >
                    重新入队
                  </el-button>
                  <el-button
                    v-if="row.jobType !== 'paper_case_ingest'"
                    link
                    type="primary"
                    @click="goToHealthRecordForm(row)"
                  >
                    去补录
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="云端资源" name="cloud">
          <div class="cloud-panel" v-loading="cloudLoading">
            <div class="cloud-panel__head">
              <div>
                <span class="cloud-panel__eyebrow">云端档案</span>
                <h3>云端资料与归档记录</h3>
                <p>直接查看当前患者在云端档案中的基本资料、健康指标，以及纸质病历归档后的标准化记录。</p>
              </div>
              <el-button size="small" @click="loadCloudData(true)">刷新云端档案</el-button>
            </div>

            <el-alert
              v-if="cloudMessage"
              :type="cloudHasAnyData ? 'success' : 'warning'"
              :closable="false"
              show-icon
              :title="cloudMessage"
              class="cloud-panel__alert"
            />

            <div class="detail-grid detail-grid--cloud">
              <div class="detail-item">
                <span>云端档案编号</span>
                <strong>{{ medplumPatientData.medplumPatientId || '--' }}</strong>
              </div>
              <div class="detail-item">
                <span>云端姓名</span>
                <strong>{{ cloudPatientDisplayName }}</strong>
              </div>
              <div class="detail-item">
                <span>健康指标数</span>
                <strong>{{ cloudObservations.length }}</strong>
              </div>
              <div class="detail-item">
                <span>病历标准资源</span>
                <strong>{{ cloudPaperCaseResourceCount }}</strong>
              </div>
            </div>

            <div class="cloud-resource-grid">
              <div class="detail-item">
                <span>就诊记录</span>
                <strong>{{ cloudEncounters.length }}</strong>
              </div>
              <div class="detail-item">
                <span>诊断记录</span>
                <strong>{{ cloudConditions.length }}</strong>
              </div>
              <div class="detail-item">
                <span>病历文档</span>
                <strong>{{ cloudDocumentReferences.length }}</strong>
              </div>
            </div>

            <div class="cloud-section">
              <div class="cloud-section__title">最近健康指标</div>
              <el-table :data="cloudObservations" stripe class="detail-table" empty-text="暂无云端健康指标">
                <el-table-column prop="effectiveDateTime" label="时间" min-width="180" />
                <el-table-column prop="code" label="指标" min-width="180" />
                <el-table-column prop="value" label="值" min-width="160" />
              </el-table>
            </div>

            <div class="cloud-section">
              <div class="cloud-section__title">纸质病历标准记录</div>
              <el-table :data="cloudPaperCaseRows" stripe class="detail-table" empty-text="暂无云端纸质病历资源">
                <el-table-column prop="resourceType" label="资源类型" width="160" />
                <el-table-column prop="display" label="摘要" min-width="260" show-overflow-tooltip />
                <el-table-column prop="date" label="时间" min-width="180" />
                <el-table-column prop="id" label="资源 ID" min-width="180" show-overflow-tooltip />
              </el-table>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="doctorReportDialogVisible"
      width="880px"
      title="医生报告详情"
      destroy-on-close
      append-to-body
      align-center
    >
      <template v-if="selectedDoctorReport">
        <div class="doctor-report-dialog">
          <div class="doctor-report-dialog__meta">
            <div class="detail-item">
              <span>报告标题</span>
              <strong>{{ selectedDoctorReport.title || '医生评估报告' }}</strong>
            </div>
            <div class="detail-item">
              <span>生成时间</span>
              <strong>{{ formatDate(selectedDoctorReport.generatedAt) || '--' }}</strong>
            </div>
            <div class="detail-item">
              <span>报告用途</span>
              <strong>用于医生复核风险、明确干预动作并保留本次处理记录</strong>
            </div>
          </div>

          <div class="doctor-report-dialog__section">
            <div class="cloud-section__title">完整报告内容</div>
            <div class="doctor-report-dialog__content">
              <RichTextBlock :content="selectedDoctorReport.content || '暂无报告内容'" />
            </div>
          </div>

          <div v-if="selectedDoctorReport.references && selectedDoctorReport.references.length > 0" class="doctor-report-dialog__section">
            <div class="cloud-section__title">引用依据</div>
            <div class="doctor-report-reference-list">
              <div
                v-for="(reference, index) in selectedDoctorReport.references"
                :key="`${selectedDoctorReport.id}-reference-${index}`"
                class="doctor-report-reference-card"
              >
                <strong>{{ reference.title || '参考资料' }}</strong>
                <div class="paper-case-sub">
                  {{ [reference.source, reference.section, reference.publishedAt].filter(Boolean).join(' · ') || '未标注来源信息' }}
                </div>
                <p>{{ reference.contentSnippet || '当前没有摘要' }}</p>
              </div>
            </div>
          </div>

          <div v-if="selectedDoctorReport.agentSteps && selectedDoctorReport.agentSteps.length > 0" class="doctor-report-dialog__section">
            <div class="cloud-section__title">生成过程</div>
            <div class="doctor-report-agent-list">
              <div
                v-for="(step, index) in selectedDoctorReport.agentSteps"
                :key="`${selectedDoctorReport.id}-agent-${index}`"
                class="doctor-report-agent-item"
              >
                <span>步骤 {{ step.index || index + 1 }}</span>
                <strong>{{ formatReportStepName(step.agentName) }}</strong>
                <p>{{ step.summary || '已完成该步骤' }}</p>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clinicalDataJobApi, patientApi, assessmentApi, followUpApi, healthRecordApi, medplumApi, paperCaseApi, healthReportApi } from '../../api'
import { ElMessage } from '../../utils/element-plus-service'
import dayjs from 'dayjs'
import RichTextBlock from '../../components/RichTextBlock.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const activeTab = ref('intervention')
const patient = ref({})
const assessments = ref([])
const followUps = ref([])
const healthRecords = ref([])
const paperCaseRecords = ref([])
const clinicalJobsLoading = ref(false)
const clinicalJobActionJobId = ref(null)
const patientClinicalJobItems = ref([])
const patientRetryableJobs = ref([])
const doctorReports = ref([])
const reportGenerating = ref(false)
const doctorReportDialogVisible = ref(false)
const selectedDoctorReport = ref(null)
const cloudLoading = ref(false)
const downloadingPaperCaseId = ref(null)
const medplumPatientData = ref({})
const medplumObservationMeta = ref({})
const medplumPaperCaseMeta = ref({})
const cloudLoadedPatientId = ref(null)
const availableTabs = new Set(['intervention', 'info', 'history', 'assessments', 'followups', 'records', 'papercases', 'clinicaljobs', 'cloud'])

const diseaseMap = { DIABETES: '2型糖尿病', HYPERTENSION: '高血压', COPD: '慢阻肺', CKD: '慢性肾病', CHD: '冠心病', STROKE: '脑卒中' }
const riskMap = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危急' }
const freqMap = { IMMEDIATE: '立即', WEEKLY: '每周', BIWEEKLY: '双周', MONTHLY: '每月', QUARTERLY: '每季度' }
const statusMap = { PENDING: '待执行', IN_PROGRESS: '进行中', COMPLETED: '已完成', OVERDUE: '已逾期', CANCELLED: '已取消' }

const cloudObservations = computed(() => medplumObservationMeta.value?.observations || [])
const cloudEncounters = computed(() => medplumPaperCaseMeta.value?.encounters || [])
const cloudConditions = computed(() => medplumPaperCaseMeta.value?.conditions || [])
const cloudDocumentReferences = computed(() => medplumPaperCaseMeta.value?.documentReferences || [])
const cloudPaperCaseResourceCount = computed(() => (
  cloudEncounters.value.length + cloudConditions.value.length + cloudDocumentReferences.value.length
))
const cloudHasAnyData = computed(() => Boolean(medplumPatientData.value?.found)
  || cloudObservations.value.length > 0
  || cloudPaperCaseResourceCount.value > 0)
const cloudMessage = computed(() => (
  medplumPaperCaseMeta.value?.message
  || medplumObservationMeta.value?.message
  || medplumPatientData.value?.message
  || ''
))
const cloudPatientDisplayName = computed(() => {
  if (!medplumPatientData.value?.found) {
    return '云端资料暂未建立'
  }
  const name = String(medplumPatientData.value?.name || '').trim()
  if (!name || name === '???') {
    return '云端资料未填写姓名'
  }
  return name
})
const cloudPaperCaseRows = computed(() => {
  const encounterRows = cloudEncounters.value.map(item => ({
    resourceType: '就诊记录',
    id: item?.id || '',
    display: item?.display || '纸质病历就诊',
    date: item?.date || ''
  }))
  const conditionRows = cloudConditions.value.map(item => ({
    resourceType: '诊断记录',
    id: item?.id || '',
    display: item?.display || '未命名诊断',
    date: item?.date || ''
  }))
  const documentRows = cloudDocumentReferences.value.map(item => ({
    resourceType: '病历文档',
    id: item?.id || '',
    display: item?.display || '纸质病历文书',
    date: item?.date || ''
  }))
  return [...encounterRows, ...conditionRows, ...documentRows]
})
const pendingReviewPaperCaseCount = computed(() => paperCaseRecords.value.filter(item => item?.icdCodingStatus === 'pending_review').length)
const syncedPaperCaseCount = computed(() => paperCaseRecords.value.filter(item => item?.medplumSyncStatus === 'synced').length)
const failedPaperCaseCount = computed(() => paperCaseRecords.value.filter(item => item?.medplumSyncStatus === 'failed').length)
const latestPaperCaseSyncTime = computed(() => {
  const values = paperCaseRecords.value
    .map(item => item?.medplumSyncedAt)
    .filter(Boolean)
    .sort((a, b) => dayjs(b).valueOf() - dayjs(a).valueOf())
  return values.length > 0 ? formatDate(values[0]) : '--'
})
const sortedAssessments = computed(() => [...assessments.value].sort((left, right) => dayjs(right.createdAt).valueOf() - dayjs(left.createdAt).valueOf()))
const latestAssessment = computed(() => sortedAssessments.value[0] || null)
const sortedFollowUps = computed(() => [...followUps.value].sort((left, right) => dayjs(right.updatedAt || right.createdAt || right.nextFollowDate).valueOf() - dayjs(left.updatedAt || left.createdAt || left.nextFollowDate).valueOf()))
const activeFollowUp = computed(() => sortedFollowUps.value.find(item => ['PENDING', 'IN_PROGRESS', 'OVERDUE'].includes(item?.status)) || null)
const latestFollowUp = computed(() => activeFollowUp.value || sortedFollowUps.value[0] || null)
const sortedDoctorReports = computed(() => [...doctorReports.value].sort((left, right) => dayjs(right.generatedAt).valueOf() - dayjs(left.generatedAt).valueOf()))
const latestDoctorReport = computed(() => sortedDoctorReports.value[0] || null)
const doctorReportStatusCode = computed(() => latestDoctorReport.value?.workflowStatusCode || '')
const doctorReportStatusLabel = computed(() => latestDoctorReport.value?.workflowStatusLabel || '已生成')
const doctorReportNextAction = computed(() => latestDoctorReport.value?.nextAction || '')
const doctorReportFreshnessLabel = computed(() => latestDoctorReport.value?.freshnessLabel || '')
const doctorReportStaleReasonText = computed(() => {
  const reasons = latestDoctorReport.value?.staleReasons
  if (!Array.isArray(reasons) || reasons.length === 0) {
    return ''
  }
  return `报告依据已发生变化：${reasons.join('、')}。`
})
const doctorReportTagType = computed(() => {
  if (!latestDoctorReport.value) {
    return 'info'
  }
  if (latestDoctorReport.value?.stale) {
    return 'warning'
  }
  if (doctorReportStatusCode.value === 'closed_loop') {
    return 'success'
  }
  if (doctorReportStatusCode.value === 'followup_active') {
    return 'primary'
  }
  return 'success'
})
const primaryPendingPaperCase = computed(() => paperCaseRecords.value.find(item => canReviewPaperCase(item)) || null)
const primaryRecoverablePaperCase = computed(() => paperCaseRecords.value.find(item => canRecoverPaperCase(item)) || null)
const attentionClinicalJobs = computed(() => (
  [...patientClinicalJobItems.value].sort((left, right) => dayjs(right.updatedAt || right.createdAt).valueOf() - dayjs(left.updatedAt || left.createdAt).valueOf())
))
const primaryRetryableClinicalJob = computed(() => attentionClinicalJobs.value.find(item => canRequeueClinicalJob(item)) || null)
const hasPendingIntervention = computed(() => Boolean(primaryPendingPaperCase.value || primaryRecoverablePaperCase.value || primaryRetryableClinicalJob.value))
const attentionClinicalJobSummary = computed(() => ({
  reviewPendingCount: attentionClinicalJobs.value.filter(item => item.jobStatus === 'review_pending').length,
  failedCount: attentionClinicalJobs.value.filter(item => item.jobStatus === 'failed').length,
  deadLetterCount: attentionClinicalJobs.value.filter(item => item.jobStatus === 'dead_letter').length,
  retryableCount: attentionClinicalJobs.value.filter(item => canRequeueClinicalJob(item)).length
}))
const pendingInterventionCount = computed(() => (
  pendingReviewPaperCaseCount.value
  + failedPaperCaseCount.value
  + attentionClinicalJobSummary.value.retryableCount
))
const followUpSummaryText = computed(() => {
  const plan = latestFollowUp.value
  if (!plan) {
    return '未生成'
  }
  const parts = [
    diseaseLabel(plan.diseaseType),
    freqLabel(plan.frequency),
    statusLabel(plan.status)
  ].filter(Boolean)
  if (plan.nextFollowDate) {
    parts.push(`下次随访 ${plan.nextFollowDate}`)
  }
  return parts.join(' · ')
})
const pendingInterventionSummary = computed(() => {
  if (!hasPendingIntervention.value) {
    return '当前没有导入阻塞，系统可以继续承接新的评估和随访。'
  }
  const parts = []
  if (pendingReviewPaperCaseCount.value > 0) {
    parts.push(`${pendingReviewPaperCaseCount.value} 份病历待人工确认`)
  }
  if (failedPaperCaseCount.value > 0) {
    parts.push(`${failedPaperCaseCount.value} 份病历归档异常待恢复`)
  }
  if (attentionClinicalJobSummary.value.retryableCount > 0) {
    parts.push(`${attentionClinicalJobSummary.value.retryableCount} 个临床作业可重试`)
  }
  return parts.join('，')
})
const interventionHeadline = computed(() => {
  if (hasPendingIntervention.value) {
    return `当前存在待处理环节：${pendingInterventionSummary.value}。先恢复导入与标准化，再让评估和随访继续向后推进。`
  }
  if (latestAssessment.value && latestFollowUp.value) {
    if (latestDoctorReport.value?.stale) {
      return '最新风险、随访或健康数据已经变化，建议先刷新医生报告，再继续记录本次处理结果。'
    }
    return `最新风险已识别，系统已自动生成随访计划。医生只需在这里完成报告生成或复核，即可形成完整闭环。`
  }
  if (latestAssessment.value) {
    return '最新风险已识别，但随访计划尚未显式呈现。建议先核对默认动作是否落库，再继续报告生成。'
  }
  return '当前还没有完整进入风险识别阶段，可先补录健康记录或处理导入异常，让流程跑通。'
})
const defaultActionNarrative = computed(() => {
  if (!latestAssessment.value) {
    return '先形成健康记录和风险评估，再由系统自动落随访计划。'
  }
  if (latestFollowUp.value) {
    return `该患者风险识别后已经自动形成随访计划：${followUpSummaryText.value}。医生现在只需要确认是否生成报告。`
  }
  return '按当前流程设计，风险评估后的默认动作应当是自动生成随访计划；当前未看到计划，说明还有步骤未完成。'
})
const reportActionLabel = computed(() => {
  if (latestDoctorReport.value?.stale) {
    return '更新医生报告'
  }
  return latestDoctorReport.value ? '重新生成医生报告' : '生成医生报告'
})
const doctorReportSummaryText = computed(() => {
  if (!latestDoctorReport.value) {
    return '报告已纳入当前处理流程，用于沉淀本次医生结论'
  }
  return [
    latestDoctorReport.value.title || doctorReportStatusLabel.value,
    doctorReportFreshnessLabel.value,
    doctorReportNextAction.value
  ].filter(Boolean).join(' · ')
})
const mainWorkflowSummary = computed(() => {
  if (doctorReportStatusCode.value === 'closed_loop' && !hasPendingIntervention.value) {
    return '导入、风险识别、随访执行、报告生成已经完成闭环，当前可回看结果并继续观察后续数据。'
  }
  if (latestAssessment.value && latestFollowUp.value && !hasPendingIntervention.value) {
    return '导入、风险识别、医生干预、随访计划、报告生成已经收敛到一条连续流程上。'
  }
  if (hasPendingIntervention.value) {
    return '当前流程并没有断在评估之后，而是停在人工确认或失败恢复环节，需要先把阻塞清掉。'
  }
  return '当前流程已具备基本数据，但还需要补齐评估、随访或报告动作。'
})
const interventionWorkflowSteps = computed(() => ([
  {
    key: 'risk',
    title: '风险识别',
    status: latestAssessment.value ? riskLabel(latestAssessment.value.riskLevel) : '待识别',
    tagType: latestAssessment.value ? riskTagType(latestAssessment.value.riskLevel) : 'info',
    metric: latestAssessment.value ? `${diseaseLabel(latestAssessment.value.diseaseType)} · ${latestAssessment.value.score ?? '--'} 分` : '暂无评估结果',
    description: latestAssessment.value ? (latestAssessment.value.assessmentDetail || '已生成评估结果') : '先补录数据或恢复导入，再触发评估。',
    actionLabel: '查看评估',
    actionKind: 'assessments',
    tone: latestAssessment.value ? 'stable' : 'waiting'
  },
  {
    key: 'doctor',
    title: '医生干预',
    status: hasPendingIntervention.value ? '待处理' : '已收敛到统一入口',
    tagType: hasPendingIntervention.value ? 'warning' : 'success',
    metric: hasPendingIntervention.value ? `${pendingInterventionCount.value} 个待处理项` : '统一入口可直接处置',
    description: hasPendingIntervention.value ? pendingInterventionSummary.value : '不用再在评估页、病历审核页、作业页和任务治理页之间来回切换。',
    actionLabel: hasPendingIntervention.value ? '立即处理' : '查看入口',
    actionKind: hasPendingIntervention.value ? 'recovery' : 'intervention',
    tone: hasPendingIntervention.value ? 'attention' : 'stable'
  },
  {
    key: 'followup',
    title: '随访计划',
    status: latestFollowUp.value ? statusLabel(latestFollowUp.value.status) : '未生成',
    tagType: latestFollowUp.value ? statusType(latestFollowUp.value.status) : 'info',
    metric: latestFollowUp.value ? followUpSummaryText.value : '未见自动计划',
    description: latestFollowUp.value ? '风险评估后的默认动作已固定为自动生成随访计划。' : '如果没有计划，说明当前流程还没有完全落地。',
    actionLabel: '查看随访',
    actionKind: 'followups',
    tone: latestFollowUp.value ? 'stable' : 'waiting'
  },
  {
    key: 'report',
    title: '医生报告',
    status: latestDoctorReport.value ? doctorReportStatusLabel.value : '未生成',
    tagType: latestDoctorReport.value ? doctorReportTagType.value : 'warning',
    metric: latestDoctorReport.value ? (doctorReportFreshnessLabel.value || formatDate(latestDoctorReport.value.generatedAt)) : '等待医生触发',
    description: latestDoctorReport.value
      ? (doctorReportStaleReasonText.value || doctorReportNextAction.value || '报告已经纳入当前处理流程。')
      : '这里提供唯一明确按钮，不再把报告做成平行功能。',
    actionLabel: latestAssessment.value ? reportActionLabel.value : '',
    actionKind: latestAssessment.value ? 'report' : '',
    tone: latestDoctorReport.value ? (latestDoctorReport.value?.stale ? 'attention' : 'stable') : 'attention'
  }
]))
const careTimeline = computed(() => {
  const items = []
  if (latestAssessment.value) {
    items.push({
      key: `assessment-${latestAssessment.value.id || latestAssessment.value.createdAt}`,
      title: '完成风险评估',
      timeValue: latestAssessment.value.createdAt,
      timeLabel: formatDate(latestAssessment.value.createdAt) || '时间待确认',
      description: latestAssessment.value.assessmentDetail || `${diseaseLabel(latestAssessment.value.diseaseType)} 风险已识别`,
      tone: 'stable'
    })
  }
  if (latestFollowUp.value) {
    items.push({
      key: `followup-${latestFollowUp.value.id || latestFollowUp.value.nextFollowDate}`,
      title: '形成随访安排',
      timeValue: latestFollowUp.value.updatedAt || latestFollowUp.value.createdAt || latestFollowUp.value.nextFollowDate,
      timeLabel: formatDate(latestFollowUp.value.updatedAt || latestFollowUp.value.createdAt) || latestFollowUp.value.nextFollowDate || '时间待确认',
      description: followUpSummaryText.value || '已形成随访计划',
      tone: latestFollowUp.value.status === 'COMPLETED' ? 'stable' : 'attention'
    })
  }
  if (latestDoctorReport.value) {
    items.push({
      key: `report-${latestDoctorReport.value.id || latestDoctorReport.value.generatedAt}`,
      title: latestDoctorReport.value.stale ? '医生报告待更新' : '生成医生报告',
      timeValue: latestDoctorReport.value.generatedAt,
      timeLabel: formatDate(latestDoctorReport.value.generatedAt) || '时间待确认',
      description: doctorReportSummaryText.value || '已生成医生报告',
      tone: latestDoctorReport.value.stale ? 'attention' : 'stable'
    })
  }
  if (hasPendingIntervention.value) {
    items.push({
      key: 'pending-intervention',
      title: '存在待处理事项',
      timeValue: null,
      timeLabel: '当前',
      description: pendingInterventionSummary.value,
      tone: 'danger'
    })
  }
  if (items.length === 0) {
    items.push({
      key: 'timeline-empty',
      title: '等待进入当前流程',
      timeValue: null,
      timeLabel: '当前',
      description: '当前还没有形成评估、随访或报告记录，可先补录健康数据或恢复导入。',
      tone: 'waiting'
    })
  }
  return items.sort((left, right) => {
    const leftValue = left.timeValue ? dayjs(left.timeValue).valueOf() : -1
    const rightValue = right.timeValue ? dayjs(right.timeValue).valueOf() : -1
    return rightValue - leftValue
  })
})
const recoveryStorySteps = computed(() => {
  const hasFailure = failedPaperCaseCount.value > 0 || attentionClinicalJobs.value.length > 0
  const hasRecoveryAction = Boolean(primaryPendingPaperCase.value || primaryRecoverablePaperCase.value || primaryRetryableClinicalJob.value)
  return [
    {
      key: 'import',
      title: '导入异常',
      status: hasFailure ? '已发现' : '无阻塞',
      tagType: hasFailure ? 'danger' : 'success',
      metric: hasFailure ? `${attentionClinicalJobs.value.length + failedPaperCaseCount.value} 个异常` : '导入处理稳定',
      description: hasFailure ? '异常数据接入没有被隐藏，而是直接暴露给医生处理。' : '当前没有导入或归档阻塞。',
      actionLabel: hasFailure ? '查看异常' : '',
      actionKind: hasFailure ? 'clinicaljobs' : '',
      tone: hasFailure ? 'danger' : 'stable'
    },
    {
      key: 'human',
      title: '人工确认 / 重试',
      status: hasRecoveryAction ? '待执行' : '无需处理',
      tagType: hasRecoveryAction ? 'warning' : 'success',
      metric: hasRecoveryAction ? pendingInterventionSummary.value : '没有待确认项',
      description: hasRecoveryAction ? '医生可以在统一入口里直接审核标准疾病编码、重试导入或转去补录。' : '当前无需人工恢复动作。',
      actionLabel: hasRecoveryAction ? '进入恢复处理' : '',
      actionKind: hasRecoveryAction ? 'recovery' : '',
      tone: hasRecoveryAction ? 'attention' : 'stable'
    },
    {
      key: 'resume',
      title: '恢复成功',
      status: hasFailure ? '待恢复' : '流程通畅',
      tagType: hasFailure ? 'info' : 'success',
      metric: hasFailure ? '等待清除阻塞' : '当前无需恢复',
      description: hasFailure ? '恢复成功后会继续向后跑，不需要再单独讲另一条故事线。' : '当前没有失败阻塞，后续评估和随访可以直接继续。',
      actionLabel: !hasFailure && latestAssessment.value ? '查看评估' : '',
      actionKind: !hasFailure && latestAssessment.value ? 'assessments' : '',
      tone: hasFailure ? 'waiting' : 'stable'
    },
    {
      key: 'continue',
      title: '后续评估继续',
      status: latestAssessment.value ? '已继续' : '待继续',
      tagType: latestAssessment.value ? 'success' : 'info',
      metric: latestAssessment.value ? formatDate(latestAssessment.value.createdAt) : '暂无后续评估',
      description: latestAssessment.value ? '恢复后的结果会重新进入风险识别与随访。' : '当异常清理完成后，这里应该出现新的评估记录。',
      actionLabel: latestAssessment.value ? '查看工作区' : '',
      actionKind: latestAssessment.value ? 'intervention' : '',
      tone: latestAssessment.value ? 'stable' : 'waiting'
    }
  ]
})
const selectedClinicalJobStatus = computed(() => normalizeJobStatus(route.query.jobStatus))
const selectedClinicalJobType = computed(() => {
  const value = String(route.query.jobType || '').trim()
  return value || ''
})
const selectedClinicalJobId = computed(() => {
  const value = Number(route.query.jobId)
  return Number.isInteger(value) && value > 0 ? value : null
})
const patientClinicalJobs = computed(() => {
  let items = Array.isArray(patientClinicalJobItems.value) ? patientClinicalJobItems.value : []
  if (selectedClinicalJobStatus.value) {
    items = items.filter(item => item.jobStatus === selectedClinicalJobStatus.value)
  }
  if (selectedClinicalJobType.value) {
    items = items.filter(item => item.jobType === selectedClinicalJobType.value)
  }
  if (selectedClinicalJobId.value) {
    items = items.filter(item => item.id === selectedClinicalJobId.value)
  }
  return items.sort((left, right) => dayjs(right.updatedAt || right.createdAt).valueOf() - dayjs(left.updatedAt || left.createdAt).valueOf())
})
const patientClinicalJobSummary = computed(() => ({
  reviewPendingCount: patientClinicalJobs.value.filter(item => item.jobStatus === 'review_pending').length,
  failedCount: patientClinicalJobs.value.filter(item => item.jobStatus === 'failed').length,
  deadLetterCount: patientClinicalJobs.value.filter(item => item.jobStatus === 'dead_letter').length,
  retryableCount: patientClinicalJobs.value.filter(item => canRequeueClinicalJob(item)).length
}))
const clinicalJobPanelDescription = computed(() => {
  const base = '集中查看当前患者名下需要医生处理的临床数据作业，纸质病历可回到审核流，设备导入类异常可直接重新入队。'
  if (selectedClinicalJobStatus.value) {
    return `${base} 当前已筛选 ${formatPaperCaseJobStatus(selectedClinicalJobStatus.value)}。`
  }
  return base
})

function diseaseLabel(t) { return diseaseMap[t] || t }
function riskLabel(l) { return riskMap[l] || l }
function riskTagType(l) { return { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[l] || 'info' }
function freqLabel(f) { return freqMap[f] || f }
function statusLabel(s) { return statusMap[s] || s }
function statusType(s) { return { PENDING: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', OVERDUE: 'danger', CANCELLED: 'info' }[s] || 'info' }
function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '' }
function displayHistory(value) { return value || '未填写' }
function hasHistoryAlerts(row) { return Array.isArray(row?.historyAlerts) && row.historyAlerts.length > 0 }
function formatPaperCaseIcd(row) {
  if (!row?.icdCode) {
    return '未匹配标准疾病编码'
  }
  const suffix = row?.icdTitle ? ` ${row.icdTitle}` : ''
  return `${row.icdCode}${suffix}`
}
function formatPaperCaseIcdStatus(row) {
  const mapping = {
    pending_review: '待医生确认',
    auto_coded: '自动编码',
    manually_confirmed: '已人工确认',
    not_coded: '未编码'
  }
  return [mapping[row?.icdCodingStatus] || row?.icdCodingStatus || '未编码', row?.icdCodingSource || 'unknown']
    .filter(Boolean)
    .join(' / ')
}
function formatPaperCaseMedplumStatus(status) {
  const mapping = {
    pending: '待处理',
    pending_sync: '待归档',
    review_pending: '待审核',
    synced: '已归档',
    skipped: '已跳过',
    failed: '归档异常',
    duplicate: '重复拦截'
  }
  return mapping[status] || status || '未知'
}
function paperCaseMedplumTagType(status) {
  if (status === 'synced') return 'success'
  if (status === 'review_pending' || status === 'pending_sync' || status === 'pending') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}
function formatPaperCaseJobStatus(status) {
  const mapping = {
    received: '已接收',
    parsed: '已解析',
    mapped: '已映射',
    review_pending: '待审核',
    running: '执行中',
    synced: '已归档',
    failed: '失败',
    dead_letter: '死信',
    cancelled: '已取消'
  }
  return mapping[status] || status || '未知'
}
function paperCaseJobTagType(status) {
  if (status === 'synced') return 'success'
  if (status === 'review_pending' || status === 'parsed' || status === 'mapped' || status === 'received' || status === 'running') return 'warning'
  if (status === 'failed' || status === 'dead_letter') return 'danger'
  return 'info'
}
function formatPaperCaseJobStage(stage) {
  const mapping = {
    received: '接收病历',
    paper_case_async_dispatch: '异步派发',
    ocr_extract: '影像文字识别',
    ocr_parsed: '识别完成',
    icd_mapping: '标准编码映射',
    structured_mapped: '标准化映射完成',
    medplum_sync: '云端归档',
    medplum_synced: '云端归档完成',
    awaiting_manual_icd_review: '等待标准编码人工确认',
    medplum_sync_failed: '云端归档失败',
    running_timeout: '运行超时',
    failed: '执行失败',
    manual_cancel: '人工取消'
  }
  return mapping[stage] || stage || '阶段未知'
}
function formatPaperCaseJobAttempt(job) {
  const current = Number(job?.runAttempt || 0)
  const retries = Number(job?.retryCount || 0)
  const maxRetries = Number(job?.maxRetries || 0)
  if (!current && !retries && !maxRetries) {
    return '--'
  }
  return `${current || 0} / ${maxRetries || 0}，已重试 ${retries || 0} 次`
}

function formatClinicalJobType(jobType) {
  if (jobType === 'paper_case_ingest') return '纸质病历导入'
  if (jobType === 'huawei_iot_import') return '设备数据导入'
  return jobType || '未知类型'
}

function formatReportStepName(name) {
  const text = String(name || '').trim()
  if (!text) {
    return '处理步骤'
  }
  const normalized = text.toLowerCase()
  if (normalized.includes('context')) return '信息整理'
  if (normalized.includes('reference')) return '依据整理'
  if (normalized.includes('draft')) return '内容起草'
  if (normalized.includes('supervisor')) return '结果汇总'
  if (normalized.includes('followup')) return '随访整理'
  if (normalized.includes('assessment')) return '风险评估整理'
  return text
}

function canReviewPaperCase(row) {
  return row?.icdCodingStatus === 'pending_review'
    || row?.job?.jobStatus === 'review_pending'
}

function canRecoverPaperCase(row) {
  return row?.medplumSyncStatus === 'failed'
    && ['failed', 'dead_letter'].includes(row?.job?.jobStatus)
}

function openPaperCaseReview(row) {
  if (!row?.id) {
    return
  }
  router.push({
    name: 'DoctorPaperCaseReview',
    query: {
      patientId: String(route.params.id),
      paperCaseId: String(row.id)
    }
  })
}

function openPaperCaseReviewByJob(job) {
  const matched = paperCaseRecords.value.find(item => item?.job?.id === job?.id)
    || paperCaseRecords.value.find(item => item?.id === job?.sourceRefId)
  if (!matched?.id) {
    return
  }
  openPaperCaseReview(matched)
}

function canRequeueClinicalJob(job) {
  return ['failed', 'dead_letter'].includes(job?.jobStatus)
    && patientRetryableJobs.value.some(item => item?.id === job?.id)
}

async function switchToTab(tab) {
  if (!availableTabs.has(tab)) {
    return
  }
  activeTab.value = tab
  if (tab === 'clinicaljobs') {
    await loadClinicalJobs()
  }
  if (tab === 'cloud') {
    await loadCloudData()
  }
  await syncTabToRoute(tab)
}

async function loadDoctorReports() {
  const patientId = route.params.id
  if (!patientId) {
    doctorReports.value = []
    return
  }
  try {
    const reports = await healthReportApi.getByPatient(patientId, 'doctor_assessment')
    doctorReports.value = Array.isArray(reports) ? reports : []
  } catch (e) {
    doctorReports.value = []
  }
}

async function generateDoctorReport() {
  const patientId = route.params.id
  if (!patientId) {
    return
  }
  if (!latestAssessment.value) {
    ElMessage.warning('当前还没有风险评估结果，无法生成医生报告')
    return
  }
  reportGenerating.value = true
  try {
    const report = await healthReportApi.generateDoctorAssessment(patientId)
    if (report?.id) {
      doctorReports.value = [report, ...doctorReports.value.filter(item => item?.id !== report.id)]
      selectedDoctorReport.value = report
      doctorReportDialogVisible.value = true
    } else {
      await loadDoctorReports()
    }
    ElMessage.success('医生评估报告已生成')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '医生报告生成失败')
  } finally {
    reportGenerating.value = false
  }
}

function openDoctorReportDetail(report) {
  if (!report?.id) {
    return
  }
  router.push({
    name: 'DoctorReportDetail',
    params: {
      id: String(route.params.id),
      reportId: String(report.id)
    }
  })
}

async function handleWorkbenchAction(actionKind) {
  if (!actionKind) {
    return
  }
  if (actionKind === 'report') {
    await generateDoctorReport()
    return
  }
  if (actionKind === 'recovery') {
    if (primaryPendingPaperCase.value) {
      openPaperCaseReview(primaryPendingPaperCase.value)
      return
    }
    if (primaryRecoverablePaperCase.value) {
      openPaperCaseReview(primaryRecoverablePaperCase.value)
      return
    }
    if (primaryRetryableClinicalJob.value) {
      if (primaryRetryableClinicalJob.value.jobType === 'paper_case_ingest') {
        openPaperCaseReviewByJob(primaryRetryableClinicalJob.value)
        return
      }
      await requeueClinicalJob(primaryRetryableClinicalJob.value)
      return
    }
    await switchToTab('clinicaljobs')
    return
  }
  await switchToTab(actionKind)
}

async function requeueClinicalJob(job) {
  if (!canRequeueClinicalJob(job)) return
  clinicalJobActionJobId.value = job.id
  try {
    const result = await clinicalDataJobApi.requeue(job.id)
    if (result?.success) {
      ElMessage.success(result.message || '临床作业已重新入队')
    } else {
      ElMessage.warning(result?.message || '临床作业已尝试重新入队，请稍后刷新确认')
    }
    await loadClinicalJobs(true)
    await loadPatientDetail()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '临床作业重新入队失败')
  } finally {
    clinicalJobActionJobId.value = null
  }
}

function normalizeJobStatus(value) {
  const normalized = String(value || '').trim()
  return ['review_pending', 'failed', 'dead_letter'].includes(normalized) ? normalized : ''
}

function syncTabFromRoute(tab) {
  const normalized = tab == null ? '' : String(tab)
  activeTab.value = availableTabs.has(normalized) ? normalized : 'intervention'
}

async function syncTabToRoute(tab = activeTab.value) {
  const nextTab = availableTabs.has(tab) ? tab : 'intervention'
  const currentTab = route.query.tab == null ? '' : String(route.query.tab)
  const currentPatientId = route.params.id == null ? '' : String(route.params.id)
  const nextPatientId = route.params.id == null ? '' : String(route.params.id)
  if (currentTab === nextTab && currentPatientId === nextPatientId) {
    return false
  }
  await router.replace({
    name: 'PatientDetail',
    params: { id: nextPatientId },
    query: { ...route.query, tab: nextTab }
  })
  return true
}

function handleTabChange() {
  if (activeTab.value === 'clinicaljobs') {
    void loadClinicalJobs()
  }
  if (activeTab.value === 'cloud') {
    void loadCloudData()
  }
  void syncTabToRoute(activeTab.value)
}

watch(
  () => route.query.tab,
  (tab) => {
    syncTabFromRoute(tab)
    if (String(tab || '') === 'clinicaljobs') {
      void loadClinicalJobs()
    }
  },
  { immediate: true }
)

async function loadPatientDetail() {
  const patientId = route.params.id
  loading.value = true
  try {
    patient.value = await patientApi.get(patientId)
    const [a, f, r, paperCases, reports] = await Promise.all([
      assessmentApi.getByPatient(patientId),
      followUpApi.getByPatient(patientId),
      healthRecordApi.getByPatient(patientId),
      paperCaseApi.getByPatient(patientId),
      healthReportApi.getByPatient(patientId, 'doctor_assessment').catch(() => [])
    ])
    assessments.value = a
    followUps.value = f
    healthRecords.value = r
    paperCaseRecords.value = Array.isArray(paperCases) ? paperCases : []
    doctorReports.value = Array.isArray(reports) ? reports : []
    await loadClinicalJobs(true)
    resetCloudState()
    if (activeTab.value === 'cloud') {
      await loadCloudData()
    }
  } catch (e) {
    console.error('Load patient detail error:', e)
  } finally {
    loading.value = false
  }
}

async function loadClinicalJobs(force = false) {
  const patientId = Number(route.params.id)
  if (!patientId) {
    patientClinicalJobItems.value = []
    patientRetryableJobs.value = []
    return
  }
  if (!force && patientClinicalJobItems.value.length > 0) {
    return
  }
  clinicalJobsLoading.value = true
  try {
    const [patientJobs, retryable] = await Promise.all([
      clinicalDataJobApi.listByPatient(patientId),
      clinicalDataJobApi.listRetryableByPatient(patientId).catch(() => [])
    ])
    patientClinicalJobItems.value = Array.isArray(patientJobs)
      ? patientJobs.filter(item => ['review_pending', 'failed', 'dead_letter'].includes(item?.jobStatus))
      : []
    patientRetryableJobs.value = Array.isArray(retryable) ? retryable : []
  } catch (e) {
    patientClinicalJobItems.value = []
    patientRetryableJobs.value = []
    ElMessage.error(e.response?.data?.message || e.message || '加载临床作业失败')
  } finally {
    clinicalJobsLoading.value = false
  }
}

function goToHealthRecordForm(job) {
  router.push({
    name: 'HealthRecord',
    query: {
      patientId: String(route.params.id),
      jobId: job?.id ? String(job.id) : '',
      jobType: job?.jobType || '',
      source: 'clinicaljob'
    }
  })
}

async function downloadPaperCase(row) {
  if (!row?.id) {
    ElMessage.warning('当前病历记录缺少文件标识')
    return
  }
  downloadingPaperCaseId.value = row.id
  try {
    const blob = await paperCaseApi.downloadFile(row.id)
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = row.originalFileName || `paper-case-${row.id}`
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '病历原件下载失败')
  } finally {
    downloadingPaperCaseId.value = null
  }
}

function resetCloudState() {
  cloudLoadedPatientId.value = null
  medplumPatientData.value = {}
  medplumObservationMeta.value = {}
  medplumPaperCaseMeta.value = {}
}

async function loadCloudData(force = false) {
  const patientId = route.params.id
  if (!patientId) {
    resetCloudState()
    return
  }
  if (!force && cloudLoadedPatientId.value === patientId) {
    return
  }
  cloudLoading.value = true
  try {
    const [patientData, observationData, paperCaseData] = await Promise.all([
      medplumApi.getPatient(patientId),
      medplumApi.getObservations(patientId, 20),
      medplumApi.getPaperCaseResources(patientId, 20)
    ])
    medplumPatientData.value = patientData || {}
    medplumObservationMeta.value = observationData || {}
    medplumPaperCaseMeta.value = paperCaseData || {}
    cloudLoadedPatientId.value = patientId
  } catch (e) {
    medplumPatientData.value = {}
    medplumObservationMeta.value = {
      observations: [],
      message: e.response?.data?.message || e.message || '读取云端健康指标失败'
    }
    medplumPaperCaseMeta.value = {
      encounters: [],
      conditions: [],
      documentReferences: [],
      message: e.response?.data?.message || e.message || '读取云端病历记录失败'
    }
  } finally {
    cloudLoading.value = false
  }
}

onMounted(async () => {
  await loadPatientDetail()
})

watch(
  () => route.params.id,
  async (id, previousId) => {
    if (id != null && id !== previousId) {
      await loadPatientDetail()
    }
  }
)
</script>

<style scoped>
.patient-detail-shell{display:grid;gap:18px}
.patient-detail-back{margin-bottom:-6px}
.patient-detail-hero{display:grid;grid-template-columns:minmax(0,1.45fr) minmax(240px,.9fr);gap:18px}
.patient-detail-hero__copy,.patient-detail-hero__stats,.patient-detail-card{border:1px solid rgba(31,51,64,.08);border-radius:28px;box-shadow:0 18px 34px rgba(17,28,38,.08)}
.patient-detail-hero__copy{padding:28px 30px;background:radial-gradient(circle at top right,rgba(201,223,235,.46),transparent 30%),linear-gradient(135deg,rgba(255,255,255,.92),rgba(241,245,247,.9))}
.patient-detail-hero__eyebrow{display:inline-block;font-size:12px;font-weight:700;letter-spacing:.12em;color:rgba(43,108,143,.72)}
.patient-detail-hero__copy h1{margin:12px 0 10px;font-size:32px;line-height:1.12;color:#1f3340}
.patient-detail-hero__copy p{margin:0;max-width:640px;line-height:1.8;color:#66757f}
.patient-detail-hero__chips{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px}
.patient-detail-hero__stats{display:grid;gap:12px;padding:18px;background:rgba(255,255,255,.82)}
.patient-detail-stat{padding:16px 18px;border-radius:22px;background:rgba(247,243,236,.84)}
.patient-detail-stat span{display:block;font-size:12px;color:#66757f}
.patient-detail-stat strong{display:block;margin:10px 0 6px;font-size:24px;color:#152635}
.patient-detail-card{background:rgba(255,255,255,.84)}
.patient-detail-actions{display:flex;gap:10px;flex-wrap:wrap}
.patient-detail-card :deep(.el-card__body){padding:0}
.patient-detail-tabs{padding:18px 22px 22px}
.intervention-workbench{display:grid;gap:18px}
.intervention-banner,.intervention-section,.intervention-detail-card{border:1px solid rgba(31,51,64,.08);border-radius:24px;background:linear-gradient(135deg,rgba(255,255,255,.94),rgba(244,247,249,.9))}
.intervention-banner{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:22px 24px;background:radial-gradient(circle at top right,rgba(206,227,237,.52),transparent 32%),linear-gradient(135deg,rgba(255,255,255,.95),rgba(244,247,249,.92))}
.intervention-banner h3,.intervention-section__head h3,.intervention-detail-card h3{margin:10px 0 8px;font-size:22px;color:#1f3340}
.intervention-banner p,.intervention-section__head p,.intervention-detail-card p{margin:0;line-height:1.75;color:#66757f}
.intervention-kpi-grid,.workflow-step-grid,.recovery-story-grid,.intervention-detail-grid{display:grid;gap:14px}
.intervention-kpi-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.intervention-kpi{padding:18px 20px;border-radius:22px;background:rgba(247,243,236,.74);border:1px solid rgba(31,51,64,.06)}
.intervention-kpi span{display:block;font-size:12px;color:#66757f}
.intervention-kpi strong{display:block;margin:10px 0 8px;font-size:20px;line-height:1.4;color:#132331}
.intervention-kpi p{font-size:13px}
.intervention-section{display:grid;gap:16px;padding:22px 24px}
.intervention-section__head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.intervention-section__head p{max-width:520px}
.workflow-step-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.recovery-story-grid{grid-template-columns:repeat(4,minmax(0,1fr))}
.care-timeline{display:grid;gap:14px}
.care-timeline__item{display:grid;grid-template-columns:20px minmax(0,1fr);gap:14px;align-items:flex-start;padding:16px 18px;border-radius:20px;border:1px solid rgba(31,51,64,.06);background:#fff}
.care-timeline__item[data-tone="stable"]{background:linear-gradient(180deg,rgba(241,250,245,.92),#fff)}
.care-timeline__item[data-tone="attention"]{background:linear-gradient(180deg,rgba(255,247,233,.96),#fff)}
.care-timeline__item[data-tone="danger"]{background:linear-gradient(180deg,rgba(254,241,240,.96),#fff)}
.care-timeline__item[data-tone="waiting"]{background:linear-gradient(180deg,rgba(244,247,249,.96),#fff)}
.care-timeline__dot{width:12px;height:12px;margin-top:6px;border-radius:999px;background:#409eff;box-shadow:0 0 0 4px rgba(64,158,255,.12)}
.care-timeline__item[data-tone="attention"] .care-timeline__dot{background:#e6a23c;box-shadow:0 0 0 4px rgba(230,162,60,.14)}
.care-timeline__item[data-tone="danger"] .care-timeline__dot{background:#f56c6c;box-shadow:0 0 0 4px rgba(245,108,108,.14)}
.care-timeline__item[data-tone="waiting"] .care-timeline__dot{background:#909399;box-shadow:0 0 0 4px rgba(144,147,153,.12)}
.care-timeline__body{display:grid;gap:8px}
.care-timeline__meta{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.care-timeline__meta strong{font-size:16px;line-height:1.5;color:#1f3340}
.care-timeline__meta span{font-size:12px;color:#66757f}
.care-timeline__body p{margin:0;font-size:13px;line-height:1.8;color:#4a5b64}
.workflow-step-card,.recovery-story-card{display:grid;gap:12px;padding:18px;border-radius:22px;border:1px solid rgba(31,51,64,.06);background:#fff}
.workflow-step-card[data-tone="stable"],.recovery-story-card[data-tone="stable"]{background:linear-gradient(180deg,rgba(241,250,245,.92),#fff)}
.workflow-step-card[data-tone="attention"],.recovery-story-card[data-tone="attention"]{background:linear-gradient(180deg,rgba(255,247,233,.96),#fff)}
.workflow-step-card[data-tone="danger"],.recovery-story-card[data-tone="danger"]{background:linear-gradient(180deg,rgba(254,241,240,.96),#fff)}
.workflow-step-card[data-tone="waiting"],.recovery-story-card[data-tone="waiting"]{background:linear-gradient(180deg,rgba(244,247,249,.96),#fff)}
.workflow-step-card__meta,.recovery-story-card__meta,.intervention-detail-card__headline{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}
.workflow-step-card__meta span,.recovery-story-card__meta span{font-size:12px;font-weight:700;letter-spacing:.08em;color:#66757f}
.workflow-step-card strong,.recovery-story-card strong{font-size:18px;line-height:1.5;color:#1f3340}
.workflow-step-card p,.recovery-story-card p{font-size:13px}
.intervention-detail-grid{grid-template-columns:repeat(3,minmax(0,1fr))}
.intervention-detail-card{display:grid;gap:14px;padding:20px 22px}
.intervention-inline-note{padding:12px 14px;border-radius:16px;background:rgba(247,243,236,.72);font-size:13px;line-height:1.75;color:#4a5b64}
.intervention-task-list{margin:0;padding-left:18px;color:#31434d}
.intervention-task-list li{margin-bottom:8px;line-height:1.75}
.report-purpose-note{padding:12px 14px;border-radius:16px;background:rgba(241,250,245,.88);font-size:13px;line-height:1.75;color:#335b46}
.report-preview{padding:14px 16px;border-radius:18px;background:rgba(247,243,236,.72);font-size:13px;line-height:1.8;color:#31434d;max-height:280px;overflow:auto}
.doctor-report-dialog{display:grid;gap:16px}
.doctor-report-dialog__meta{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.doctor-report-dialog__section{display:grid;gap:10px}
.doctor-report-dialog__content{padding:18px;border-radius:20px;background:rgba(247,243,236,.72);border:1px solid rgba(31,51,64,.06)}
.doctor-report-reference-list,.doctor-report-agent-list{display:grid;gap:10px}
.doctor-report-reference-card,.doctor-report-agent-item{padding:14px 16px;border-radius:18px;background:#fff;border:1px solid rgba(31,51,64,.06)}
.doctor-report-reference-card p,.doctor-report-agent-item p{margin:8px 0 0;line-height:1.75;color:#4a5b64}
.doctor-report-agent-item span{display:block;font-size:12px;color:#66757f;margin-bottom:6px}
.detail-grid,.detail-history-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}
.detail-grid--cloud{margin-bottom:14px}
.detail-item{padding:16px 18px;border-radius:20px;background:rgba(247,243,236,.8)}
.detail-item--wide{grid-column:1 / -1}
.detail-item span{display:block;margin-bottom:8px;font-size:12px;color:#66757f}
.detail-item strong{display:block;font-size:14px;line-height:1.8;color:#1f3340;white-space:pre-wrap;word-break:break-word}
.detail-table :deep(.el-table){border-radius:18px;overflow:hidden}
.detail-table :deep(th.el-table__cell){background:rgba(247,243,236,.8);color:#4a5b64}
.detail-table :deep(td.el-table__cell){color:#31434d}
.recommendation-list{margin:0;padding-left:16px}
.recommendation-list li{margin-bottom:6px;font-size:12px;line-height:1.7}
.check-tag-list,.history-alerts{display:flex;gap:6px;flex-wrap:wrap}
.history-empty{color:#909399}
.paper-case-job{display:grid;gap:4px}
.paper-case-job__line{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.paper-case-job__error{font-size:12px;line-height:1.6;color:#c45656;word-break:break-word}
.paper-case-main{font-weight:600;color:#1f3340}
.paper-case-sub{font-size:12px;line-height:1.6;color:#66757f;word-break:break-word}
.cloud-panel{display:grid;gap:16px}
.cloud-panel__head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.cloud-panel__eyebrow{display:inline-block;font-size:12px;font-weight:700;letter-spacing:.12em;color:rgba(43,108,143,.72)}
.cloud-panel__head h3{margin:10px 0 8px;font-size:22px;color:#1f3340}
.cloud-panel__head p{margin:0;line-height:1.7;color:#66757f}
.cloud-panel__alert{margin-top:-2px}
.cloud-resource-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.cloud-section{display:grid;gap:10px}
.cloud-section__title{font-size:14px;font-weight:700;color:#31434d}
@media (max-width:1100px){.patient-detail-hero{grid-template-columns:1fr}}
@media (max-width:1200px){.intervention-kpi-grid,.workflow-step-grid,.recovery-story-grid,.intervention-detail-grid,.doctor-report-dialog__meta{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media (max-width:720px){.patient-detail-hero__copy h1{font-size:26px}.detail-grid,.detail-history-grid,.cloud-resource-grid,.intervention-kpi-grid,.workflow-step-grid,.recovery-story-grid,.intervention-detail-grid,.doctor-report-dialog__meta{grid-template-columns:1fr}.patient-detail-tabs{padding-left:18px;padding-right:18px}.cloud-panel__head,.intervention-banner,.intervention-section__head{flex-direction:column}}
</style>
