<template>
  <div class="objective-page">
    <a-spin :spinning="loading">
      <a-card v-if="page.title" class="hero-card" :bordered="false">
        <div class="hero-top">
          <div>
            <div class="eyebrow">线上客观题作业</div>
            <h1>{{ page.title }}</h1>
            <p class="desc" v-if="page.description">{{ page.description }}</p>
          </div>
          <div class="hero-stats">
            <a-tag color="blue">共 {{ page.questionCount || 0 }} 题</a-tag>
            <a-tag color="green">总分 {{ page.totalScore || 0 }} 分</a-tag>
            <a-tag v-if="page.redoLimit > 0" color="orange">可重做 {{ page.redoLimit }} 次</a-tag>
          </div>
        </div>
        <div v-if="page.documentUrl" class="doc-link">
          <a-button @click="openDoc(page.documentUrl)">查看作业资料</a-button>
        </div>
      </a-card>

      <a-alert
        v-if="page.submitted && page.latestResult"
        :type="page.showResultAfterSubmit ? 'success' : 'info'"
        showIcon
        class="result-banner"
        :message="resultMessage"
        :description="resultDescription"
      />

      <a-card
        v-for="(question, questionIndex) in page.questions"
        :key="question.id || questionIndex"
        class="question-card"
        :title="`第 ${questionIndex + 1} 题`"
      >
        <template slot="extra">
          <a-tag>{{ question.score }} 分</a-tag>
        </template>

        <div v-if="question.stemText" class="rich-content stem-text" v-html="renderRichText(question.stemText)"></div>
        <div v-if="splitImages(question.stemImages).length" class="image-grid">
          <img
            v-for="(image, imageIndex) in splitImages(question.stemImages)"
            :key="imageIndex"
            :src="getFileAccessHttpUrl(image)"
            alt="题目图片"
            class="zoomable-image stem-image"
            @click="previewImage(getFileAccessHttpUrl(image))"
          />
        </div>

        <a-radio-group v-model="answers[question.id]" class="answer-group" :disabled="questionReadonly">
          <a-radio
            v-for="option in displayOptions(question)"
            :key="option.optionKey"
            :value="option.optionKey"
            class="answer-option"
          >
            <div class="option-row">
              <span class="option-key">{{ option.optionKey }}.</span>
              <div v-if="option.optionText" class="option-text rich-content" v-html="renderRichText(option.optionText)"></div>
            </div>
            <div v-if="option.optionImage" class="option-image-wrap">
              <img
                :src="getFileAccessHttpUrl(option.optionImage)"
                alt="选项图片"
                class="option-image zoomable-image"
                @click="previewImage(getFileAccessHttpUrl(option.optionImage))"
              />
            </div>
          </a-radio>
        </a-radio-group>
      </a-card>

      <a-card v-if="showResultItems" class="review-card" title="提交结果">
        <div v-for="item in page.latestResult.items" :key="item.questionId" class="review-item">
          <div class="review-head">
            <div>第 {{ item.questionNo }} 题</div>
            <a-tag :color="item.correct ? 'green' : 'red'">{{ item.correct ? '答对' : '答错' }}</a-tag>
          </div>
          <div v-if="item.stemText" class="rich-content stem-text" v-html="renderRichText(item.stemText)"></div>
          <div v-if="splitImages(item.stemImages).length" class="image-grid compact">
            <img
              v-for="(image, imageIndex) in splitImages(item.stemImages)"
              :key="imageIndex"
              :src="getFileAccessHttpUrl(image)"
              alt="题目图片"
              class="zoomable-image stem-image compact-image"
              @click="previewImage(getFileAccessHttpUrl(image))"
            />
          </div>
          <div class="answer-line">你的答案：{{ answerLabel(item.studentAnswer) }}</div>
          <div v-if="page.showResultAfterSubmit" class="answer-line">正确答案：{{ answerLabel(item.correctAnswer) }}</div>
          <div v-if="page.showResultAfterSubmit && item.analysisText" class="analysis-box">
            <div class="analysis-title">解析</div>
            <div class="rich-content" v-html="renderRichText(item.analysisText)"></div>
          </div>
          <div v-if="page.showResultAfterSubmit && splitImages(item.analysisImages).length" class="image-grid compact">
            <img
              v-for="(image, imageIndex) in splitImages(item.analysisImages)"
              :key="imageIndex"
              :src="getFileAccessHttpUrl(image)"
              alt="解析图片"
              class="zoomable-image compact-image"
              @click="previewImage(getFileAccessHttpUrl(image))"
            />
          </div>
        </div>
      </a-card>

      <a-modal :visible="previewVisible" :footer="null" @cancel="closePreview" width="960px" centered>
        <img v-if="previewImageUrl" :src="previewImageUrl" alt="预览图片" class="preview-modal-image" />
      </a-modal>

      <div class="footer-actions">
        <a-button @click="reloadData">刷新</a-button>
        <a-button v-if="page.remainingRedoCount > 0 && page.submitted" @click="startRedo">重新作答</a-button>
        <a-button v-if="showSubmitButton" type="primary" :loading="submitting" :disabled="submitDisabled" @click="submitHomework">
          {{ submitButtonText }}
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<script>
import { getAction, httpAction, getFileAccessHttpUrl, getFilePrevew } from '@/api/manage'

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export default {
  name: 'ObjectiveHomeworkPage',
  data() {
    return {
      loading: false,
      submitting: false,
      answers: {},
      previewVisible: false,
      previewImageUrl: '',
      page: {
        sourceType: '',
        sourceId: '',
        title: '',
        description: '',
        documentUrl: '',
        departId: '',
        allowRedo: true,
        redoLimit: 1,
        remainingRedoCount: 0,
        showResultAfterSubmit: true,
        questionCount: 0,
        totalScore: 0,
        questions: [],
        submitted: false,
        canSubmit: true,
        latestResult: null,
      },
      viewMode: 'answer'
    }
  },
  watch: {
    $route() {
      this.reloadData()
    }
  },
  computed: {
    questionReadonly() {
      return this.submitting || this.viewMode === 'review'
    },
    submitDisabled() {
      return this.submitting || !this.page.canSubmit || !this.page.questions.length || this.viewMode === 'review'
    },
    submitButtonText() {
      if (this.page.submitted && this.page.allowRedo) {
        return '重新提交'
      }
      return '提交作业'
    },
    showSubmitButton() {
      return this.viewMode !== 'review'
    },
    showResultItems() {
      return this.page.latestResult && this.page.latestResult.items && this.page.latestResult.items.length
    },
    resultMessage() {
      if (!this.page.latestResult) {
        return ''
      }
      return `已提交，第 ${this.page.latestResult.attemptNo} 次作答，得分 ${this.page.latestResult.objectiveScore}/${this.page.latestResult.totalScore}`
    },
    resultDescription() {
      if (!this.page.latestResult) {
        return ''
      }
      if (!this.page.showResultAfterSubmit) {
        return `答对 ${this.page.latestResult.rightCount} 题，共 ${this.page.latestResult.questionCount} 题。当前显示每题对错和你的答案，不显示正确答案与解析。`
      }
      return `答对 ${this.page.latestResult.rightCount} 题，共 ${this.page.latestResult.questionCount} 题。`
    }
  },
  created() {
    this.reloadData()
  },
  methods: {
    getFileAccessHttpUrl,
    getFilePrevew,
    resolveViewMode() {
      const routeMode = this.$route.query.mode
      if (routeMode === 'review' || routeMode === 'answer') {
        return routeMode
      }
      return 'answer'
    },
    reloadData() {
      const sourceType = this.$route.query.sourceType
      const sourceId = this.$route.query.sourceId
      const departId = this.$route.query.departId
      if (!sourceType || !sourceId) {
        this.$message.error('缺少作业参数')
        return
      }
      this.loading = true
      this.viewMode = this.resolveViewMode()
      getAction('/teaching/objectiveHomework/studentView', { sourceType, sourceId, departId }).then(res => {
        if (res.success) {
          this.page = Object.assign({}, this.page, res.result)
          if (this.page.redoLimit == null) {
            this.page.redoLimit = this.page.allowRedo ? 1 : 0
          }
          if (this.page.remainingRedoCount == null) {
            this.page.remainingRedoCount = this.page.submitted ? Math.max(0, this.page.redoLimit - (this.page.latestResult ? this.page.latestResult.attemptNo : 0)) : this.page.redoLimit
          }
          this.syncPageState()
        } else {
          this.$message.error(res.message)
        }
      }).finally(() => {
        this.loading = false
      })
    },
    openDoc(path) {
      window.open(getFilePrevew(path))
    },
    splitImages(value) {
      if (!value) {
        return []
      }
      return String(value).split(',').map(item => item.trim()).filter(Boolean)
    },
    displayOptions(question) {
      if (question.questionType === 'judge') {
        return [
          { optionKey: 'T', optionText: '正确' },
          { optionKey: 'F', optionText: '错误' },
        ]
      }
      return question.options || []
    },
    answerLabel(value) {
      if (value === 'T') {
        return '正确'
      }
      if (value === 'F') {
        return '错误'
      }
      return value || '未作答'
    },
    renderRichText(value) {
      const content = String(value || '').trim()
      if (!content) {
        return ''
      }
      if (/<[a-z][\s\S]*>/i.test(content)) {
        return content
      }
      return `<p>${escapeHtml(content).replace(/\n/g, '<br/>')}</p>`
    },
    syncPageState() {
      const wantsReset = this.$route.query.reset === '1'
      if (wantsReset && this.page.remainingRedoCount > 0) {
        this.viewMode = 'answer'
        this.resetAnswers(false)
        return
      }
      if (this.page.submitted) {
        if (this.viewMode === 'review' || this.page.remainingRedoCount <= 0) {
          this.viewMode = 'review'
          this.fillAnswersFromLatest()
          return
        }
        this.resetAnswers(false)
        return
      }
      this.viewMode = 'answer'
      this.resetAnswers(false)
    },
    fillAnswersFromLatest() {
      const next = {}
      ;(this.page.questions || []).forEach(question => {
        next[question.id] = undefined
      })
      const items = (this.page.latestResult && this.page.latestResult.items) || []
      items.forEach(item => {
        next[item.questionId] = item.studentAnswer
      })
      this.answers = next
    },
    resetAnswers(showMsg = true) {
      const next = {}
      ;(this.page.questions || []).forEach(question => {
        next[question.id] = undefined
      })
      this.answers = next
      if (showMsg) {
        this.$message.success('已清空当前作答')
      }
    },
    previewImage(url) {
      if (!url) {
        return
      }
      this.previewImageUrl = url
      this.previewVisible = true
    },
    closePreview() {
      this.previewVisible = false
      this.previewImageUrl = ''
    },
    startRedo() {
      if (this.page.remainingRedoCount <= 0) {
        return
      }
      this.viewMode = 'answer'
      this.resetAnswers(false)
      this.$router.replace({
        path: this.$route.path,
        query: Object.assign({}, this.$route.query, {
          mode: 'answer',
          reset: '1'
        })
      })
      this.$message.success('已切换到重新作答模式')
    },
    submitHomework() {
      const missing = (this.page.questions || []).find(question => !this.answers[question.id])
      if (missing) {
        this.$message.error(`第 ${missing.questionNo || 1} 题还未作答`)
        return
      }
      this.submitting = true
      const payload = {
        sourceType: this.page.sourceType,
        sourceId: this.page.sourceId,
        departId: this.page.departId,
        answers: Object.keys(this.answers).map(questionId => ({
          questionId,
          answer: this.answers[questionId],
        })),
      }
      httpAction('/teaching/objectiveHomework/submit', payload, 'post').then(res => {
        if (res.success) {
          this.$message.success('提交成功')
          this.page.submitted = true
          this.page.latestResult = res.result
          this.page.remainingRedoCount = Number(res.result.remainingRedoCount || 0)
          this.page.redoLimit = Number(res.result.redoLimit || this.page.redoLimit || 0)
          this.page.canSubmit = this.page.remainingRedoCount > 0
          this.viewMode = 'review'
          this.fillAnswersFromLatest()
          this.$router.replace({
            path: this.$route.path,
            query: Object.assign({}, this.$route.query, {
              mode: 'review',
              reset: '0'
            })
          })
        } else {
          this.$message.error(res.message)
        }
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style lang="less" scoped>
.objective-page {
  max-width: 980px;
  margin: 24px auto 48px;
  padding: 0 16px;

  .hero-card,
  .question-card,
  .review-card {
    margin-bottom: 16px;
  }

  .hero-top {
    display: flex;
    justify-content: space-between;
    gap: 16px;
  }

  .eyebrow {
    color: #1890ff;
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 8px;
  }

  h1 {
    margin-bottom: 8px;
  }

  .desc {
    color: #666;
    white-space: pre-wrap;
  }

  .hero-stats {
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    gap: 8px;
    min-width: 180px;
    justify-content: flex-end;
  }

  .doc-link {
    margin-top: 16px;
  }

  .result-banner {
    margin-bottom: 16px;
  }

  .stem-text {
    font-size: 15px;
    line-height: 1.8;
  }

  .image-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
    margin-top: 12px;

    img {
      width: 100%;
      max-width: 420px;
      border-radius: 10px;
      border: 1px solid #f0f0f0;
      background: #fff;
    }
  }

  .image-grid.compact {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }

  .answer-group {
    width: 100%;
    margin-top: 16px;
  }

  .answer-option {
    display: block;
    padding: 12px;
    margin-bottom: 10px;
    border: 1px solid #f0f0f0;
    border-radius: 10px;
    background: #fcfcfc;
  }

  .answer-group /deep/ .ant-radio-wrapper-disabled {
    color: #22304a;
  }

  .answer-group /deep/ .ant-radio-wrapper-disabled .ant-radio-disabled + span {
    color: #22304a;
  }

  .answer-group /deep/ .ant-radio-disabled + span {
    color: #22304a;
  }

  .answer-group /deep/ .ant-radio-disabled .ant-radio-inner {
    border-color: #9bb7d7;
    background: #ffffff;
  }

  .answer-group /deep/ .ant-radio-disabled.ant-radio-checked .ant-radio-inner {
    border-color: #1677ff;
    background: #1677ff;
  }

  .answer-group /deep/ .ant-radio-disabled.ant-radio-checked .ant-radio-inner::after {
    background: #ffffff;
    transform: scale(0.5);
  }

  .answer-group /deep/ .ant-radio-wrapper-disabled.answer-option {
    background: #f8fbff;
    border-color: #d7e6f7;
    opacity: 1;
  }

  .option-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  .option-key {
    font-weight: 700;
    line-height: 1.8;
    margin-top: 1px;
  }

  .option-text {
    flex: 1;
    min-width: 0;
  }

  .option-image-wrap {
    margin-top: 10px;
    margin-left: 24px;
  }

  .option-image {
    max-width: 240px;
    border-radius: 8px;
    border: 1px solid #f0f0f0;
  }

  .stem-image {
    max-width: 420px;
  }

  .compact-image {
    max-width: 220px;
  }

  .zoomable-image {
    cursor: zoom-in;
    box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
    transition: transform 0.18s ease, box-shadow 0.18s ease;
  }

  .zoomable-image:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
  }

  .preview-modal-image {
    display: block;
    width: 100%;
    max-height: 80vh;
    object-fit: contain;
    margin: 0 auto;
  }

  .review-item {
    padding: 16px 0;
    border-bottom: 1px solid #f0f0f0;
  }

  .review-item:last-child {
    border-bottom: none;
  }

  .review-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .answer-line,
  .analysis-box {
    margin-top: 8px;
    color: #555;
  }

  .analysis-title {
    margin-bottom: 6px;
    font-weight: 600;
    color: #333;
  }

  .rich-content {
    word-break: break-word;
  }

  .rich-content /deep/ p,
  .rich-content /deep/ pre,
  .rich-content /deep/ ul,
  .rich-content /deep/ ol,
  .rich-content /deep/ blockquote,
  .rich-content /deep/ h1,
  .rich-content /deep/ h2,
  .rich-content /deep/ h3,
  .rich-content /deep/ h4 {
    margin-bottom: 12px;
  }

  .rich-content /deep/ p:last-child,
  .rich-content /deep/ pre:last-child,
  .rich-content /deep/ ul:last-child,
  .rich-content /deep/ ol:last-child,
  .rich-content /deep/ blockquote:last-child,
  .rich-content /deep/ h1:last-child,
  .rich-content /deep/ h2:last-child,
  .rich-content /deep/ h3:last-child,
  .rich-content /deep/ h4:last-child {
    margin-bottom: 0;
  }

  .rich-content /deep/ pre {
    padding: 12px;
    overflow: auto;
    background: #0f172a;
    border-radius: 10px;
    color: #e2e8f0;
  }

  .rich-content /deep/ code {
    padding: 2px 6px;
    background: #f3f4f6;
    border-radius: 4px;
    color: #1f2937;
  }

  .rich-content /deep/ pre code {
    padding: 0;
    background: transparent;
    color: inherit;
  }

  .rich-content /deep/ img {
    max-width: 100%;
    height: auto;
    border-radius: 10px;
  }

  .footer-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 24px;
  }
}
</style>
