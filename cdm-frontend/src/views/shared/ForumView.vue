<template>
  <div class="forum-page" :class="themeClass">
    <section class="forum-hero">
      <div class="forum-hero__content">
        <span class="forum-hero__eyebrow">{{ userStore.isDoctor ? 'Clinical Community' : 'Health Exchange' }}</span>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageSubtitle }}</p>
        <div class="forum-hero__chips">
          <el-tag effect="plain" round>{{ roleLabel }}</el-tag>
          <el-tag effect="plain" round type="info">帖子 {{ posts.length }}</el-tag>
          <el-tag effect="plain" round type="success">评论 {{ totalComments }}</el-tag>
          <el-tag effect="plain" round type="warning">热门标签 {{ uniqueTagCount }}</el-tag>
        </div>
      </div>
      <div class="forum-hero__actions">
        <el-button type="primary" size="large" @click="showNewPost = true">发布帖子</el-button>
        <div class="forum-hero__aside">
          <strong>{{ recentPostCount }}</strong>
          <span>近 7 天有更新的讨论</span>
        </div>
      </div>
    </section>

    <section class="forum-stat-grid">
      <article class="forum-stat-card">
        <span>帖子总数</span>
        <strong>{{ posts.length }}</strong>
        <p>集中展示医患之间的经验分享、提问和健康交流。</p>
      </article>
      <article class="forum-stat-card">
        <span>累计评论</span>
        <strong>{{ totalComments }}</strong>
        <p>包含评论与楼中回复，用于观察社区活跃度。</p>
      </article>
      <article class="forum-stat-card">
        <span>医生发帖</span>
        <strong>{{ doctorPostCount }}</strong>
        <p>医生可以发布知识解读、提醒和病例经验。</p>
      </article>
      <article class="forum-stat-card">
        <span>患者发帖</span>
        <strong>{{ patientPostCount }}</strong>
        <p>患者可以记录日常管理经验、问题和感受。</p>
      </article>
    </section>

    <section class="forum-layout">
      <div class="forum-main">
        <div class="forum-toolbar">
          <div>
            <span class="forum-toolbar__eyebrow">Discussion Feed</span>
            <h2>社区讨论</h2>
          </div>
          <el-button plain @click="loadPosts">刷新列表</el-button>
        </div>

        <div v-loading="loading" class="forum-post-list">
          <el-empty
            v-if="posts.length === 0 && !loading"
            description="暂无帖子，快来发布第一个帖子吧"
            :image-size="88"
          />

          <article
            v-for="post in posts"
            :key="post.id"
            class="forum-post-card"
            @click="openPost(post)"
          >
            <div class="forum-post-card__head">
              <div class="forum-post-card__identity">
                <el-avatar
                  :size="42"
                  :src="resolveAvatar('', post.authorRole, post.author)"
                  :class="[
                    'forum-avatar',
                    post.authorRole === 'doctor' ? 'forum-avatar--doctor' : 'forum-avatar--patient'
                  ]"
                >
                  {{ (post.author || '').charAt(0) }}
                </el-avatar>
                <div>
                  <div class="forum-post-card__author-row">
                    <span class="forum-post-card__author">{{ post.author }}</span>
                    <el-tag :type="post.authorRole === 'doctor' ? 'primary' : 'success'" size="small" effect="plain">
                      {{ post.authorRole === 'doctor' ? '医生' : '患者' }}
                    </el-tag>
                  </div>
                  <div class="forum-post-card__time">{{ formatTime(post.createdAt) }}</div>
                </div>
              </div>
              <div class="forum-post-card__actions">
                <el-tag size="small" type="info" effect="plain">
                  {{ resolvePostTotalComments(post) }} 条评论
                </el-tag>
                <el-button
                  v-if="isOwnedByCurrentUser(post)"
                  link
                  type="danger"
                  @click.stop="confirmDeletePost(post)"
                >
                  删除
                </el-button>
              </div>
            </div>

            <h3 class="forum-post-card__title">{{ post.title }}</h3>
            <p class="forum-post-card__content">{{ truncate(post.content, 220) }}</p>

            <div class="forum-post-card__footer">
              <div class="forum-post-card__tags">
                <el-tag
                  v-for="tag in (post.tags || [])"
                  :key="tag"
                  size="small"
                  type="info"
                  effect="plain"
                >
                  {{ tag }}
                </el-tag>
              </div>
              <el-button link type="primary">
                查看详情
              </el-button>
            </div>
          </article>
        </div>
      </div>

      <aside class="forum-side">
        <div class="forum-side-card">
          <span class="forum-side-card__eyebrow">Popular Tags</span>
          <h3>高频主题</h3>
          <div class="forum-tag-cloud">
            <el-tag
              v-for="item in hotTags"
              :key="item.name"
              effect="plain"
              round
              type="info"
            >
              {{ item.name }} · {{ item.count }}
            </el-tag>
            <span v-if="hotTags.length === 0" class="forum-side-card__empty">发布帖子后这里会显示主题聚合。</span>
          </div>
        </div>

        <div class="forum-side-card">
          <span class="forum-side-card__eyebrow">Participation Guide</span>
          <h3>参与建议</h3>
          <ul class="forum-guide-list">
            <li>标题尽量明确，例如直接写指标波动、症状问题或经验主题。</li>
            <li>涉及用药、过敏和并发症时，优先提供完整背景，避免信息缺失。</li>
            <li>经验分享适合放社区，紧急情况仍应通过医生随访或线下就诊处理。</li>
          </ul>
        </div>

        <div class="forum-side-card">
          <span class="forum-side-card__eyebrow">Role Snapshot</span>
          <h3>社区构成</h3>
          <div class="forum-side-metrics">
            <div>
              <strong>{{ doctorPostCount }}</strong>
              <span>医生发帖</span>
            </div>
            <div>
              <strong>{{ patientPostCount }}</strong>
              <span>患者发帖</span>
            </div>
            <div>
              <strong>{{ uniqueTagCount }}</strong>
              <span>标签种类</span>
            </div>
          </div>
        </div>
      </aside>
    </section>

    <el-dialog v-model="showNewPost" title="发布帖子" width="640px" class="forum-dialog" append-to-body align-center>
      <div class="forum-dialog__intro">
        <strong>发布新讨论</strong>
        <p>适合分享健康管理经验、提出具体问题，或补充医生与患者之间的连续沟通背景。</p>
      </div>
      <el-form :model="newPost" label-width="72px" class="forum-form">
        <el-form-item label="标题">
          <el-input v-model="newPost.title" placeholder="请输入帖子标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="newPost.content" type="textarea" :rows="7" placeholder="分享您的健康心得或提问..." />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="newPost.tags" multiple placeholder="选择标签" style="width: 100%">
            <el-option label="高血压" value="高血压" />
            <el-option label="糖尿病" value="糖尿病" />
            <el-option label="饮食" value="饮食" />
            <el-option label="运动" value="运动" />
            <el-option label="用药" value="用药" />
            <el-option label="经验分享" value="经验分享" />
            <el-option label="求助" value="求助" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewPost = false">取消</el-button>
        <el-button type="primary" @click="submitPost" :loading="posting">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showPostDetail"
      :title="currentPost?.title"
      width="820px"
      class="forum-dialog forum-dialog--detail"
      append-to-body
      align-center
    >
      <template v-if="currentPost">
        <div class="post-detail">
          <div class="post-detail__author">
            <div class="forum-post-card__identity">
              <el-avatar
                :size="44"
                :src="resolveAvatar('', currentPost.authorRole, currentPost.author)"
                :class="[
                  'forum-avatar',
                  currentPost.authorRole === 'doctor' ? 'forum-avatar--doctor' : 'forum-avatar--patient'
                ]"
              >
                {{ (currentPost.author || '').charAt(0) }}
              </el-avatar>
              <div>
                <div class="forum-post-card__author-row">
                  <span class="forum-post-card__author">{{ currentPost.author }}</span>
                  <el-tag :type="currentPost.authorRole === 'doctor' ? 'primary' : 'success'" size="small" effect="plain">
                    {{ currentPost.authorRole === 'doctor' ? '医生' : '患者' }}
                  </el-tag>
                </div>
                <div class="forum-post-card__time">{{ formatTime(currentPost.createdAt) }}</div>
              </div>
            </div>
            <div class="forum-post-card__tags">
              <el-tag
                v-for="tag in (currentPost.tags || [])"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>

          <div v-if="isOwnedByCurrentUser(currentPost)" class="post-detail__actions">
            <el-button plain type="danger" @click="confirmDeletePost(currentPost)">删除帖子</el-button>
          </div>

          <div class="post-detail__body">{{ currentPost.content }}</div>

          <div class="post-detail__section-title">
            <span>评论区</span>
            <strong>{{ (currentPost.comments || []).length }}</strong>
          </div>

          <div v-if="(currentPost.comments || []).length === 0" class="post-detail__empty">
            还没有评论，欢迎补充你的经验或问题。
          </div>

          <div
            v-for="comment in (currentPost.comments || [])"
            :key="comment.id"
            class="comment-card"
          >
            <div class="comment-card__head">
              <div class="comment-card__author">
                <span>{{ comment.author }}</span>
                <el-tag :type="comment.authorRole === 'doctor' ? 'primary' : 'success'" size="small" effect="plain">
                  {{ comment.authorRole === 'doctor' ? '医生' : '患者' }}
                </el-tag>
              </div>
              <div class="comment-card__actions">
                <span class="comment-card__time">{{ formatTime(comment.createdAt) }}</span>
                <el-button
                  v-if="isOwnedByCurrentUser(comment)"
                  link
                  size="small"
                  type="danger"
                  @click="confirmDeleteComment(comment)"
                >
                  删除
                </el-button>
              </div>
            </div>
            <p class="comment-card__content">{{ comment.content }}</p>

            <div
              v-for="reply in (comment.replies || [])"
              :key="reply.id"
              class="reply-card"
            >
              <div class="comment-card__head">
                <div class="comment-card__author">
                  <span>{{ reply.author }}</span>
                  <el-tag :type="reply.authorRole === 'doctor' ? 'primary' : 'success'" size="small" effect="plain">
                    {{ reply.authorRole === 'doctor' ? '医生' : '患者' }}
                  </el-tag>
                </div>
                <div class="comment-card__actions">
                  <span class="comment-card__time">{{ formatTime(reply.createdAt) }}</span>
                  <el-button
                    v-if="isOwnedByCurrentUser(reply)"
                    link
                    size="small"
                    type="danger"
                    @click="confirmDeleteReply(reply)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
              <p class="comment-card__content">{{ reply.content }}</p>
            </div>

            <div class="comment-card__reply">
              <el-button link size="small" type="primary" @click="replyTarget = replyTarget === comment.id ? null : comment.id">
                回复
              </el-button>
              <div v-if="replyTarget === comment.id" class="comment-card__reply-box">
                <el-input v-model="replyContent" size="small" placeholder="回复..." />
                <el-button size="small" type="primary" @click="submitReply(comment.id)">发送</el-button>
              </div>
            </div>
          </div>

          <div class="post-detail__composer">
            <el-input v-model="commentContent" placeholder="发表评论..." />
            <el-button type="primary" @click="submitComment">评论</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { forumApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import { resolveAvatar } from '../../utils/avatar'
import dayjs from 'dayjs'

const userStore = useUserStore()
const posts = ref([])
const loading = ref(false)
const showNewPost = ref(false)
const showPostDetail = ref(false)
const currentPost = ref(null)
const posting = ref(false)
const commentContent = ref('')
const replyTarget = ref(null)
const replyContent = ref('')

const newPost = ref({ title: '', content: '', tags: [] })

const themeClass = computed(() => userStore.isDoctor ? 'forum-page--doctor' : 'forum-page--patient')
const roleLabel = computed(() => userStore.isDoctor ? '医生社区视角' : '患者社区视角')
const pageTitle = computed(() => userStore.isDoctor ? '医患社区讨论区' : '健康经验交流社区')
const pageSubtitle = computed(() => {
  if (userStore.isDoctor) {
    return '汇总患者提问、知识分享与管理经验，让社区讨论与临床管理信息保持连续。'
  }
  return '在这里查看医生建议、患者经验和日常管理心得，获得更贴近真实场景的讨论反馈。'
})
const totalComments = computed(() => posts.value.reduce((sum, post) => sum + resolvePostTotalComments(post), 0))
const doctorPostCount = computed(() => posts.value.filter(post => post.authorRole === 'doctor').length)
const patientPostCount = computed(() => posts.value.filter(post => post.authorRole !== 'doctor').length)
const recentPostCount = computed(() => posts.value.filter(post => dayjs(post.createdAt).isAfter(dayjs().subtract(7, 'day'))).length)
const hotTags = computed(() => {
  const map = new Map()
  posts.value.forEach((post) => {
    ;(post.tags || []).forEach((tag) => {
      map.set(tag, (map.get(tag) || 0) + 1)
    })
  })
  return Array.from(map.entries())
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)
})
const uniqueTagCount = computed(() => hotTags.value.length)
const currentActor = computed(() => ({
  username: userStore.loginName,
  displayName: userStore.username,
  role: userStore.role
}))

onMounted(() => loadPosts())

async function loadPosts() {
  loading.value = true
  try {
    posts.value = normalizePostList(await forumApi.getPosts())
  } finally {
    loading.value = false
  }
}

async function submitPost() {
  if (!newPost.value.title.trim() || !newPost.value.content.trim()) {
    return ElMessage.warning('请填写标题和内容')
  }
  posting.value = true
  try {
    await forumApi.createPost({
      ...newPost.value,
      author: userStore.username,
      authorUsername: userStore.loginName,
      authorRole: userStore.role
    }, currentActor.value)
    ElMessage.success('帖子发布成功')
    showNewPost.value = false
    newPost.value = { title: '', content: '', tags: [] }
    await loadPosts()
  } catch (e) {
    ElMessage.error('发布失败')
  } finally {
    posting.value = false
  }
}

async function openPost(post) {
  try {
    currentPost.value = await forumApi.getPost(post.id)
    upsertPostSummary(currentPost.value)
    showPostDetail.value = true
  } catch (e) {
    currentPost.value = post
    showPostDetail.value = true
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) return
  try {
    await forumApi.addComment(currentPost.value.id, {
      content: commentContent.value,
      author: userStore.username,
      authorUsername: userStore.loginName,
      authorRole: userStore.role
    }, currentActor.value)
    commentContent.value = ''
    currentPost.value = await forumApi.getPost(currentPost.value.id)
    upsertPostSummary(currentPost.value)
    loadPosts().catch(() => {})
  } catch (e) {
    ElMessage.error('评论失败')
  }
}

async function submitReply(commentId) {
  if (!replyContent.value.trim()) return
  try {
    await forumApi.addReply(commentId, {
      content: replyContent.value,
      author: userStore.username,
      authorUsername: userStore.loginName,
      authorRole: userStore.role
    }, currentActor.value)
    replyContent.value = ''
    replyTarget.value = null
    currentPost.value = await forumApi.getPost(currentPost.value.id)
    upsertPostSummary(currentPost.value)
    loadPosts().catch(() => {})
  } catch (e) {
    ElMessage.error('回复失败')
  }
}

async function confirmDeletePost(post) {
  try {
    await ElMessageBox.confirm('删除后帖子、评论和回复都会被彻底清除，且不可恢复。是否继续？', '删除帖子', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await forumApi.deletePost(post.id, currentActor.value)
    if (currentPost.value?.id === post.id) {
      showPostDetail.value = false
      currentPost.value = null
    }
    posts.value = posts.value.filter(item => item?.id !== post.id)
    await loadPosts()
    ElMessage.success('帖子已删除')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    const msg = e.response?.data?.message || '删除帖子失败'
    ElMessage.error(msg)
  }
}

async function confirmDeleteComment(comment) {
  try {
    await ElMessageBox.confirm('删除后该评论下的所有回复也会被彻底清除。是否继续？', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await forumApi.deleteComment(comment.id, currentActor.value)
    await refreshCurrentPostAfterMutation('评论已删除')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    const msg = e.response?.data?.message || '删除评论失败'
    ElMessage.error(msg)
  }
}

async function confirmDeleteReply(reply) {
  try {
    await ElMessageBox.confirm('删除后该回复将被彻底清除，且不可恢复。是否继续？', '删除回复', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await forumApi.deleteReply(reply.id, currentActor.value)
    await refreshCurrentPostAfterMutation('回复已删除')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    const msg = e.response?.data?.message || '删除回复失败'
    ElMessage.error(msg)
  }
}

async function refreshCurrentPostAfterMutation(successMessage) {
  if (!currentPost.value?.id) return
  currentPost.value = await forumApi.getPost(currentPost.value.id)
  upsertPostSummary(currentPost.value)
  await loadPosts()
  if (successMessage) {
    ElMessage.success(successMessage)
  }
}

function resolvePostTotalComments(post) {
  if (!post || typeof post !== 'object') return 0
  const detailComments = Array.isArray(post.comments) ? post.comments : []
  if (detailComments.length > 0) {
    return detailComments.length + detailComments.reduce((sum, comment) => sum + countReplies(comment), 0)
  }
  const totalCommentCount = Number(post.totalCommentCount)
  if (Number.isFinite(totalCommentCount)) return totalCommentCount
  const baseCommentCount = Number(post.commentCount)
  if (Number.isFinite(baseCommentCount)) {
    const replyCount = Number(post.replyCount)
    return baseCommentCount + (Number.isFinite(replyCount) ? replyCount : 0)
  }
  return 0
}

function countReplies(comment) {
  if (!comment || typeof comment !== 'object') return 0
  const list = Array.isArray(comment.replies) ? comment.replies : []
  return list.length
}

function normalizePostList(list) {
  if (!Array.isArray(list)) return []
  return list.map((item) => {
    const post = item && typeof item === 'object' ? { ...item } : {}
    const baseCommentCount = Number(post.commentCount)
    if (!Number.isFinite(post.totalCommentCount)) {
      post.totalCommentCount = Number.isFinite(baseCommentCount) ? baseCommentCount : 0
    }
    if (!Array.isArray(post.comments)) {
      post.comments = []
    }
    return post
  })
}

function upsertPostSummary(detailPost) {
  if (!detailPost || detailPost.id == null) return
  const comments = Array.isArray(detailPost.comments) ? detailPost.comments : []
  const commentCount = comments.length
  const replyCount = comments.reduce((sum, comment) => sum + countReplies(comment), 0)
  const totalCommentCount = commentCount + replyCount
  const idx = posts.value.findIndex(item => item?.id === detailPost.id)
  if (idx === -1) return
  posts.value[idx] = {
    ...posts.value[idx],
    commentCount,
    replyCount,
    totalCommentCount,
    comments
  }
}

function isOwnedByCurrentUser(item) {
  if (!item || typeof item !== 'object') return false
  if (!userStore.role) return false
  if ((item.authorRole || '') !== userStore.role) return false
  const authorUsername = String(item.authorUsername || '').trim()
  const currentUsername = String(userStore.loginName || '').trim()
  if (authorUsername && currentUsername) {
    return authorUsername === currentUsername
  }
  return String(item.author || '').trim() === String(userStore.username || '').trim()
}

function truncate(text, maxLen) {
  if (!text) return ''
  return text.length > maxLen ? `${text.slice(0, maxLen)}...` : text
}

function formatTime(ts) {
  return ts ? dayjs(ts).format('YYYY-MM-DD HH:mm') : ''
}
</script>

<style scoped>
.forum-page {
  display: grid;
  gap: 22px;
}

.forum-page--doctor {
  --forum-accent: #294a60;
  --forum-accent-strong: #1f3849;
  --forum-accent-soft: rgba(198, 220, 232, 0.42);
  --forum-surface: rgba(255, 255, 255, 0.9);
  --forum-surface-soft: rgba(247, 249, 250, 0.9);
  --forum-border: rgba(31, 51, 64, 0.08);
}

.forum-page--patient {
  --forum-accent: #2e6f8b;
  --forum-accent-strong: #24576d;
  --forum-accent-soft: rgba(203, 229, 238, 0.46);
  --forum-surface: rgba(255, 255, 255, 0.9);
  --forum-surface-soft: rgba(247, 250, 251, 0.92);
  --forum-border: rgba(39, 81, 104, 0.08);
}

.forum-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  color: #f7fbff;
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
}

.forum-page--doctor .forum-hero {
  background:
    radial-gradient(circle at top right, rgba(142, 176, 197, 0.18), transparent 34%),
    linear-gradient(135deg, #22313d 0%, #34495a 52%, #466173 100%);
}

.forum-page--patient .forum-hero {
  background:
    radial-gradient(circle at top right, rgba(150, 198, 214, 0.2), transparent 34%),
    linear-gradient(135deg, #1f4f63 0%, #2f6f8b 52%, #4c91a5 100%);
}

.forum-hero__content {
  max-width: 760px;
}

.forum-hero__eyebrow,
.forum-toolbar__eyebrow,
.forum-side-card__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.forum-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.forum-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.forum-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.forum-hero__actions {
  min-width: 220px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.forum-hero__aside {
  display: grid;
  gap: 6px;
  text-align: right;
}

.forum-hero__aside strong {
  font-size: 34px;
  line-height: 1;
}

.forum-hero__aside span {
  font-size: 13px;
  color: rgba(237, 244, 249, 0.82);
}

.forum-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.forum-stat-card,
.forum-main,
.forum-side-card {
  border-radius: 24px;
  border: 1px solid var(--forum-border);
  background: var(--forum-surface);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.forum-stat-card {
  padding: 18px 20px;
}

.forum-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.forum-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.forum-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.forum-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: start;
}

.forum-main {
  padding: 22px;
}

.forum-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.forum-toolbar h2,
.forum-side-card h3 {
  margin: 10px 0 0;
  color: #1f3340;
}

.forum-toolbar__eyebrow,
.forum-side-card__eyebrow {
  background: var(--forum-accent-soft);
  color: var(--forum-accent);
}

.forum-post-list {
  display: grid;
  gap: 16px;
}

.forum-post-card {
  padding: 20px;
  border-radius: 22px;
  background: var(--forum-surface-soft);
  border: 1px solid rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.forum-post-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 30px rgba(17, 28, 38, 0.08);
  border-color: rgba(80, 126, 151, 0.18);
}

.forum-post-card__head,
.forum-post-card__identity,
.forum-post-card__author-row,
.forum-post-card__footer,
.forum-post-card__actions,
.comment-card__head,
.comment-card__author,
.comment-card__actions,
.post-detail__actions,
.post-detail__author {
  display: flex;
  align-items: center;
}

.forum-post-card__head,
.forum-post-card__footer,
.comment-card__head,
.post-detail__actions,
.post-detail__author {
  justify-content: space-between;
  gap: 12px;
}

.forum-post-card__identity,
.forum-post-card__author-row,
.comment-card__author,
.forum-post-card__actions,
.comment-card__actions {
  gap: 8px;
}

.forum-avatar {
  box-shadow: 0 12px 24px rgba(17, 28, 38, 0.12);
}

.forum-avatar--doctor {
  background: var(--forum-accent-strong);
  color: #ffffff;
}

.forum-avatar--patient {
  background: linear-gradient(135deg, #57a882 0%, #79bf9a 100%);
  color: #ffffff;
}

.forum-post-card__author {
  font-size: 15px;
  font-weight: 700;
  color: #233846;
}

.forum-post-card__time,
.comment-card__time {
  font-size: 12px;
  color: #7d8e98;
}

.forum-post-card__title {
  margin: 16px 0 10px;
  font-size: 20px;
  line-height: 1.35;
  color: #1f3340;
}

.forum-post-card__content,
.comment-card__content,
.forum-side-card__empty,
.post-detail__empty {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #5d707d;
}

.forum-post-card__tags,
.forum-tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.forum-side {
  display: grid;
  gap: 16px;
}

.forum-side-card {
  padding: 20px;
}

.forum-guide-list {
  margin: 16px 0 0;
  padding-left: 18px;
  color: #5d707d;
  font-size: 14px;
  line-height: 1.8;
}

.forum-side-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.forum-side-metrics div {
  padding: 14px 12px;
  border-radius: 18px;
  background: var(--forum-accent-soft);
}

.forum-side-metrics strong {
  display: block;
  font-size: 22px;
  color: var(--forum-accent-strong);
}

.forum-side-metrics span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #5f7482;
}

.forum-dialog__intro {
  margin-bottom: 16px;
  padding: 16px 18px;
  border-radius: 18px;
  background: var(--forum-accent-soft);
}

.forum-dialog__intro strong {
  display: block;
  color: var(--forum-accent-strong);
}

.forum-dialog__intro p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #5f7482;
}

.post-detail {
  display: grid;
  gap: 18px;
}

.post-detail__body {
  padding: 18px 20px;
  border-radius: 22px;
  background: var(--forum-surface-soft);
  font-size: 14px;
  line-height: 1.9;
  color: #2d3f4c;
  white-space: pre-wrap;
}

.post-detail__actions {
  justify-content: flex-end;
}

.post-detail__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #1f3340;
}

.post-detail__section-title span {
  font-size: 18px;
  font-weight: 700;
}

.post-detail__section-title strong {
  font-size: 20px;
}

.comment-card {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid var(--forum-border);
  background: #ffffff;
}

.reply-card {
  margin: 12px 0 0 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: var(--forum-surface-soft);
  border: 1px dashed rgba(80, 126, 151, 0.18);
}

.comment-card__reply {
  margin-top: 10px;
}

.comment-card__reply-box,
.post-detail__composer {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

@media (max-width: 1200px) {
  .forum-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .forum-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .forum-hero,
  .forum-toolbar,
  .forum-post-card__head,
  .forum-post-card__footer,
  .forum-post-card__actions,
  .post-detail__author,
  .post-detail__actions,
  .post-detail__composer,
  .comment-card__actions,
  .comment-card__reply-box {
    flex-direction: column;
    align-items: stretch;
  }

  .forum-hero {
    padding: 20px;
  }

  .forum-hero__actions {
    align-items: stretch;
  }

  .forum-hero__aside {
    text-align: left;
  }

  .forum-stat-grid,
  .forum-side-metrics {
    grid-template-columns: 1fr;
  }

  .forum-hero h1 {
    font-size: 28px;
  }
}
</style>
