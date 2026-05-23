
<template>
  <div class="objective-editor">
    <a-alert
      type="info"
      showIcon
      message="客观题作业编辑器"
      description="左侧直接编辑整张试卷，右侧实时预览。支持上传图片，也支持直接粘贴剪贴板里的图片。保存时自动解析成客观题结构。"
      style="margin-bottom: 16px"
    />

    <a-card size="small" title="作业设置" :bordered="false" class="setting-card">
      <a-row :gutter="16">
        <a-col :span="12">
          <div class="setting-item">
            <div class="label">可重做次数</div>
            <a-input-number v-model="redoLimit" :min="0" :max="10" style="width: 120px" />
          </div>
        </a-col>
        <a-col :span="12">
          <div class="setting-item">
            <div class="label">提交后显示答案与解析</div>
            <a-switch v-model="showResultAfterSubmit" checkedChildren="显示" unCheckedChildren="不显示" />
          </div>
        </a-col>
      </a-row>
    </a-card>

    <a-card size="small" title="试卷编辑" :bordered="false" class="editor-card">
      <template slot="extra">
        <div class="editor-extra">
          <a-checkbox v-model="syncScroll">同步滚动</a-checkbox>
          <a-button icon="file-text" @click="fillTemplate">填入示例模板</a-button>
          <a-button icon="picture" @click="triggerImageUpload">插入图片</a-button>
          <a-button icon="check-circle" @click="refreshPreview(true)">刷新预览</a-button>
        </div>
      </template>

      <div class="editor-tip" v-pre>
        建议格式：`## 选择题 / ## 判断题` + `{{ select(n) }}` + 底部 `yaml answers`。解析可选写成 `:::analysis` 块。
      </div>

      <input ref="imageInput" type="file" accept="image/*" style="display:none" @change="handleFileChange" />

      <div class="status-bar">
        <a-tag color="blue">共 {{ parsedQuestions.length }} 题</a-tag>
        <a-tag color="green">总分 {{ totalScore }} 分</a-tag>
        <a-tag v-if="!parseError" color="success">解析正常</a-tag>
        <a-tag v-else color="red">解析异常</a-tag>
      </div>

      <a-alert v-if="parseError" type="error" showIcon :message="parseError" style="margin-bottom:12px" />

      <div class="split-layout">
        <div class="pane pane-editor">
          <div class="pane-title">Markdown 编辑</div>
          <j-code-editor
            ref="markdownEditor"
            class="markdown-editor"
            language="markdown"
            :lineNumbers="true"
            :fullScreen="true"
            placeholder="请在这里直接编辑整张试卷；支持粘贴图片。"
            v-model="editorContent"
          />
        </div>

        <div ref="previewScroll" class="pane pane-preview" @scroll="handlePreviewScroll">
          <div class="pane-title sticky">实时预览</div>
          <a-empty v-if="!parsedQuestions.length && !parseError" description="开始输入后，这里会显示试卷效果" />
          <div v-for="(question, index) in parsedQuestions" :key="question._key || index" class="preview-question-card">
            <div class="preview-question-head">
              <div>第 {{ index + 1 }} 题 · {{ question.questionType === 'judge' ? '判断题' : '选择题' }}</div>
              <div class="preview-head-right">
                <a-tag>{{ question.score }} 分</a-tag>
                <a-tag v-if="!question._hasAnswer" color="orange">未配置答案</a-tag>
              </div>
            </div>
            <div class="preview-rich" v-html="renderRichText(question.stemText)"></div>
            <div class="preview-option-list">
              <div v-for="option in previewOptions(question)" :key="option.optionKey" class="preview-option" :class="{ correct: option.optionKey === question.correctAnswer }">
                <div class="preview-option-main">
                  <span class="preview-option-key">{{ option.optionKey }}.</span>
                  <div class="preview-rich option-content" v-html="renderRichText(option.optionText)"></div>
                </div>
              </div>
            </div>
            <div v-if="question.analysisText" class="analysis-box">
              <div class="analysis-title">解析</div>
              <div class="preview-rich" v-html="renderRichText(question.analysisText)"></div>
            </div>
          </div>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script>
import JCodeEditor from '@/components/jeecg/JCodeEditor'
import { getAction, uploadAction, getFileAccessHttpUrl } from '@/api/manage'

const UPLOAD_TARGET_LOCAL = 'local'
const UPLOAD_TARGET_QINIU = 'qiniu'

function uid(prefix = 'id') {
  return `${prefix}_${Date.now()}_${Math.random().toString(16).slice(2, 8)}`
}

function uuidGenerator() {
  const chars = '0123456789abcdef'
  const values = []
  for (let i = 0; i < 32; i += 1) {
    values.push(chars[Math.floor(Math.random() * chars.length)])
  }
  return values.join('')
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInlineMarkdown(value) {
  let result = escapeHtml(value)
  result = result.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img alt="$1" src="$2" />')
  result = result.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  result = result.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  result = result.replace(/\*([^*]+)\*/g, '<em>$1</em>')
  result = result.replace(/`([^`]+)`/g, '<code>$1</code>')
  return result
}

function markdownToHtml(markdown) {
  const source = String(markdown || '').trim()
  if (!source) {
    return ''
  }
  if (/<[a-z][\s\S]*>/i.test(source)) {
    return source
  }
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const html = []
  let paragraph = []
  let inCodeBlock = false
  let codeLang = ''
  let codeBuffer = []

  const flushParagraph = () => {
    if (!paragraph.length) {
      return
    }
    html.push(`<p>${renderInlineMarkdown(paragraph.join('<br/>'))}</p>`)
    paragraph = []
  }

  const flushCode = () => {
    if (!codeBuffer.length) {
      inCodeBlock = false
      codeLang = ''
      return
    }
    const langClass = codeLang ? ` class="language-${escapeHtml(codeLang)}"` : ''
    html.push(`<pre class="objective-code-block"><code${langClass}>${escapeHtml(codeBuffer.join('\n'))}</code></pre>`)
    inCodeBlock = false
    codeLang = ''
    codeBuffer = []
  }

  lines.forEach(line => {
    const trimmed = line.trim()
    const codeMatch = trimmed.match(/^```\s*([A-Za-z0-9_-]+)?\s*$/)
    if (codeMatch) {
      flushParagraph()
      if (inCodeBlock) {
        flushCode()
      } else {
        inCodeBlock = true
        codeLang = codeMatch[1] || ''
      }
      return
    }
    if (inCodeBlock) {
      codeBuffer.push(line)
      return
    }
    if (!trimmed) {
      flushParagraph()
      return
    }
    const headingMatch = trimmed.match(/^(#{1,6})\s+(.*)$/)
    if (headingMatch) {
      flushParagraph()
      const level = headingMatch[1].length
      html.push(`<h${level}>${renderInlineMarkdown(headingMatch[2])}</h${level}>`)
      return
    }
    paragraph.push(line)
  })

  flushParagraph()
  if (inCodeBlock) {
    flushCode()
  }
  return html.join('')
}

function stripYamlFence(content) {
  const source = String(content || '')
  const yamlBlockRegex = /```yaml\s*([\s\S]*?)```\s*$/i
  const match = source.match(yamlBlockRegex)
  if (!match) {
    return { body: source, yaml: '' }
  }
  return { body: source.slice(0, match.index).trim(), yaml: match[1].trim() }
}

function parseAnswersYaml(text) {
  const lines = String(text || '').replace(/\r\n/g, '\n').split('\n')
  const answers = {}
  let inAnswers = false
  let currentKey = null
  lines.forEach(rawLine => {
    const line = rawLine.replace(/\t/g, '    ')
    if (!line.trim()) {
      return
    }
    if (/^answers\s*:\s*$/i.test(line.trim())) {
      inAnswers = true
      currentKey = null
      return
    }
    if (!inAnswers) {
      return
    }
    const keyMatch = line.match(/^\s*['"]?([^:'"]+)['"]?\s*:\s*$/)
    if (keyMatch) {
      currentKey = String(keyMatch[1]).trim()
      answers[currentKey] = []
      return
    }
    const itemMatch = line.match(/^\s*-\s*(.+?)\s*$/)
    if (itemMatch && currentKey) {
      answers[currentKey].push(String(itemMatch[1]).replace(/^['"]|['"]$/g, '').trim())
    }
  })
  return answers
}

function normalizeJudgeAnswer(value) {
  const answer = String(value || '').trim().toUpperCase()
  if (['T', 'TRUE', 'RIGHT', 'Y', 'YES', 'A', '正确', '对'].includes(answer)) {
    return 'T'
  }
  if (['F', 'FALSE', 'WRONG', 'N', 'NO', 'B', '错误', '错'].includes(answer)) {
    return 'F'
  }
  return ''
}

function createQuestion(type) {
  return {
    _key: uid('question'),
    questionType: type,
    stemText: '',
    analysisText: '',
    score: 5,
    correctAnswer: type === 'judge' ? 'T' : 'A',
    options: [],
    _hasAnswer: false,
  }
}

function buildOption(optionMarkdown, index) {
  return {
    _key: uid('option'),
    optionKey: String.fromCharCode(65 + index),
    optionText: markdownToHtml(optionMarkdown.trim()),
    optionImage: '',
  }
}

function parseQuestionBlock(block, sectionType, answerMap) {
  const normalizedBlock = String(block || '').trim()
  if (!normalizedBlock) {
    return null
  }
  const lines = normalizedBlock.split('\n')
  const firstLine = lines.shift() || ''
  const numberMatch = firstLine.match(/^\s*(\d+)\.\s*(.*)$/)
  const titleLine = numberMatch ? numberMatch[2] : firstLine.trim()
  let questionNo = numberMatch ? numberMatch[1] : ''
  let selectKey = ''
  let inCodeBlock = false
  let beforeSelect = true
  let inAnalysis = false
  const stemLines = []
  const analysisLines = []
  const optionBlocks = []
  let currentOption = []

  const flushOption = () => {
    const text = currentOption.join('\n').trim()
    if (text) {
      optionBlocks.push(text)
    }
    currentOption = []
  }

  if (titleLine) {
    stemLines.push(titleLine)
  }

  lines.forEach(line => {
    const trimmed = line.trim()
    if (/^```\s*([A-Za-z0-9_-]+)?\s*$/.test(trimmed)) {
      inCodeBlock = !inCodeBlock
      if (inAnalysis) {
        analysisLines.push(line)
      } else if (beforeSelect) {
        stemLines.push(line)
      } else if (currentOption.length) {
        currentOption.push(line)
      }
      return
    }
    if (beforeSelect) {
      const selectMatch = trimmed.match(/^\{\{\s*select\(([^)]+)\)\s*\}\}$/)
      if (!inCodeBlock && selectMatch) {
        selectKey = String(selectMatch[1]).trim()
        if (!questionNo) {
          questionNo = selectKey
        }
        beforeSelect = false
        return
      }
      stemLines.push(line)
      return
    }
    if (!inCodeBlock && /^:::\s*analysis\s*$/i.test(trimmed)) {
      flushOption()
      inAnalysis = true
      return
    }
    if (inAnalysis) {
      if (!inCodeBlock && /^:::\s*$/.test(trimmed)) {
        inAnalysis = false
        return
      }
      analysisLines.push(line)
      return
    }
    if (!inCodeBlock && /^\-\s+/.test(line)) {
      flushOption()
      currentOption.push(line.replace(/^\s*\-\s+/, ''))
      return
    }
    if (currentOption.length) {
      currentOption.push(line)
    }
  })
  flushOption()

  const answerKey = selectKey || questionNo
  const answerInfo = answerMap[answerKey] || answerMap[questionNo] || []
  const questionType = sectionType === 'judge' ? 'judge' : 'single'
  const question = createQuestion(questionType)
  question.stemText = markdownToHtml(stemLines.join('\n').trim())
  question.analysisText = markdownToHtml(analysisLines.join('\n').trim())
  question.score = Number(answerInfo[1]) || 5
  question._hasAnswer = answerInfo.length > 0

  if (questionType === 'judge') {
    question.correctAnswer = normalizeJudgeAnswer(answerInfo[0]) || 'T'
    question.options = []
  } else {
    const finalOptions = optionBlocks.length ? optionBlocks : ['选项 A', '选项 B']
    question.options = finalOptions.map((item, index) => buildOption(item, index))
    const correctAnswer = String(answerInfo[0] || 'A').trim().toUpperCase()
    question.correctAnswer = question.options.some(option => option.optionKey === correctAnswer)
      ? correctAnswer
      : question.options[0].optionKey
  }
  return question
}

function parseObjectiveDocument(content) {
  const stripped = stripYamlFence(content)
  const answerMap = parseAnswersYaml(stripped.yaml)
  const lines = String(stripped.body || '').replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  let sectionType = 'single'
  let currentBlock = []
  let inCodeBlock = false

  const pushBlock = () => {
    const text = currentBlock.join('\n').trim()
    if (text) {
      blocks.push({ sectionType, content: text })
    }
    currentBlock = []
  }

  lines.forEach(line => {
    const trimmed = line.trim()
    if (/^```\s*([A-Za-z0-9_-]+)?\s*$/.test(trimmed)) {
      inCodeBlock = !inCodeBlock
      currentBlock.push(line)
      return
    }
    const headingMatch = trimmed.match(/^##\s*(.+)$/)
    if (!inCodeBlock && headingMatch) {
      pushBlock()
      sectionType = headingMatch[1].indexOf('判断') >= 0 ? 'judge' : 'single'
      return
    }
    if (!inCodeBlock && /^---+\s*$/.test(trimmed)) {
      pushBlock()
      return
    }
    currentBlock.push(line)
  })
  pushBlock()

  return blocks.map(item => parseQuestionBlock(item.content, item.sectionType, answerMap)).filter(Boolean)
}

function toEditorContent(value) {
  return value ? String(value).trim() : ''
}

function buildMarkdownDocument(config, normalizeImageUrl) {
  const questions = Array.isArray(config.questions) ? config.questions : []
  if (!questions.length) {
    return '## 选择题\n\n1. 请在这里输入题目\n\n{{ select(1) }}\n\n- 选项 A\n- 选项 B\n\n```yaml\ntype: objective\nanswers:\n  1:\n  - A\n  - 5\n```'
  }

  const sections = []
  const answerLines = ['type: objective', 'answers:']
  let currentSection = ''
  let sectionIndex = 0
  let overallIndex = 0

  questions.forEach(question => {
    overallIndex += 1
    const sectionTitle = question.questionType === 'judge' ? '判断题' : '选择题'
    if (currentSection !== sectionTitle) {
      currentSection = sectionTitle
      sectionIndex = 1
      sections.push(`## ${sectionTitle}`)
      sections.push('')
    } else {
      sectionIndex += 1
    }
    sections.push(`${sectionIndex}. ${toEditorContent(question.stemText) || '请填写题干'}`)
    sections.push('')
    sections.push(`{{ select(${overallIndex}) }}`)
    sections.push('')
    if (question.questionType === 'judge') {
      sections.push('- 正确')
      sections.push('- 错误')
    } else {
      const options = Array.isArray(question.options) && question.options.length ? question.options : []
      options.forEach(option => {
        sections.push(`- ${toEditorContent(option.optionText) || '选项内容'}`)
        if (option.optionImage) {
          sections.push(`  ![](${normalizeImageUrl(option.optionImage)})`)
        }
      })
    }
    if (question.analysisText) {
      sections.push('')
      sections.push(':::analysis')
      sections.push(toEditorContent(question.analysisText))
      sections.push(':::')
    }
    sections.push('---')
    sections.push('')

    const answerValue = question.questionType === 'judge'
      ? (question.correctAnswer === 'F' ? 'B' : 'A')
      : (question.correctAnswer || 'A')
    answerLines.push(`  ${overallIndex}:`)
    answerLines.push(`  - ${answerValue}`)
    answerLines.push(`  - ${Number(question.score) || 5}`)
  })

  while (sections.length && !sections[sections.length - 1].trim()) {
    sections.pop()
  }
  if (sections[sections.length - 1] === '---') {
    sections.pop()
  }
  return `${sections.join('\n')}\n\n\`\`\`yaml\n${answerLines.join('\n')}\n\`\`\``
}

export default {
  name: 'ObjectiveHomeworkEditor',
  components: { JCodeEditor },
  props: {
    value: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      redoLimit: 1,
      showResultAfterSubmit: true,
      editorContent: '',
      parsedQuestions: [],
      parseError: '',
      syncScroll: true,
      syncingFrom: '',
      uploadLoading: false,
      parseTimer: null,
      editorPasteHandler: null,
      editorScrollHandler: null,
    }
  },
  computed: {
    totalScore() {
      return this.parsedQuestions.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
    },
    uploadTarget() {
      return (this.$store.getters.sysConfig || {}).uploadType || UPLOAD_TARGET_LOCAL
    },
    qiniuDomain() {
      return (this.$store.getters.sysConfig || {}).qiniuDomain || ''
    },
    qiniuArea() {
      return (this.$store.getters.sysConfig || {}).qiniuArea || ''
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(value) {
        this.setValue(value)
      }
    },
    editorContent() {
      this.scheduleParse()
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.bindEditorEvents()
      this.scheduleParse()
    })
  },
  beforeDestroy() {
    if (this.parseTimer) {
      clearTimeout(this.parseTimer)
    }
    this.unbindEditorEvents()
  },
  methods: {
    fillTemplate() {
      this.editorContent = `## 选择题

1. 请输入第一道选择题题干

{{ select(1) }}

- 选项 A
- 选项 B
- 选项 C
- 选项 D
---

## 判断题

1. 请输入一道判断题题干

{{ select(2) }}

- 正确
- 错误

:::analysis
这里可以写解析，也可以继续插入图片。
:::

\`\`\`yaml
type: objective
answers:
  1:
  - A
  - 5
  2:
  - B
  - 5
\`\`\``
    },
    setValue(value) {
      const config = value || {}
      if (config.redoLimit != null) {
        this.redoLimit = Math.max(0, Number(config.redoLimit) || 0)
      } else {
        this.redoLimit = config.allowRedo === true ? 1 : 0
      }
      this.showResultAfterSubmit = config.showResultAfterSubmit !== false
      this.editorContent = config.sourceMarkdown
        ? config.sourceMarkdown
        : buildMarkdownDocument(config, this.normalizeImageUrl)
      this.scheduleParse()
    },
    scheduleParse() {
      if (this.parseTimer) {
        clearTimeout(this.parseTimer)
      }
      this.parseTimer = setTimeout(() => this.refreshPreview(false), 180)
    },
    refreshPreview(showMessage) {
      try {
        const questions = parseObjectiveDocument(this.editorContent)
        this.parsedQuestions = questions
        this.parseError = ''
        if (showMessage) {
          this.$message.success(`预览已刷新，共 ${questions.length} 道题`)
        }
      } catch (e) {
        this.parsedQuestions = []
        this.parseError = e.message || '解析失败，请检查格式'
        if (showMessage) {
          this.$message.error(this.parseError)
        }
      }
    },
    buildQuestionsForSave() {
      const questions = parseObjectiveDocument(this.editorContent)
      if (!questions.length) {
        throw new Error('请至少录入一道题目')
      }
      return questions.map((question, index) => {
        if (!question.stemText) {
          throw new Error(`第 ${index + 1} 题缺少题干`)
        }
        if (!question._hasAnswer) {
          throw new Error(`第 ${index + 1} 题没有在底部 YAML 中配置答案`)
        }
        if (!question.score || Number(question.score) <= 0) {
          throw new Error(`第 ${index + 1} 题分值必须大于 0`)
        }
        if (question.questionType === 'single') {
          if (!Array.isArray(question.options) || question.options.length < 2) {
            throw new Error(`第 ${index + 1} 题至少需要两个选项`)
          }
          if (!question.correctAnswer) {
            throw new Error(`第 ${index + 1} 题缺少正确答案`)
          }
        } else if (!['T', 'F'].includes(question.correctAnswer)) {
          throw new Error(`第 ${index + 1} 题判断答案无效`)
        }
        return {
          questionType: question.questionType,
          stemText: question.stemText,
          stemImages: '',
          analysisText: question.analysisText,
          analysisImages: '',
          score: Number(question.score) || 0,
          correctAnswer: question.correctAnswer,
          options: question.questionType === 'single'
            ? question.options.map(option => ({ optionKey: option.optionKey, optionText: option.optionText, optionImage: option.optionImage || '' }))
            : [],
        }
      })
    },
    getValue() {
      const questions = this.buildQuestionsForSave()
      return {
        allowRedo: this.redoLimit > 0,
        redoLimit: this.redoLimit,
        showResultAfterSubmit: this.showResultAfterSubmit,
        sourceMarkdown: this.editorContent,
        questionCount: questions.length,
        totalScore: questions.reduce((sum, item) => sum + (Number(item.score) || 0), 0),
        questions,
      }
    },
    validate() {
      try {
        const config = this.getValue()
        this.parsedQuestions = parseObjectiveDocument(this.editorContent)
        this.parseError = ''
        return Promise.resolve(config)
      } catch (e) {
        this.parseError = e.message || '解析失败，请检查格式'
        this.$message.error(this.parseError)
        return Promise.reject(e)
      }
    },
    previewOptions(question) {
      if (question.questionType === 'judge') {
        return [{ optionKey: 'T', optionText: '正确' }, { optionKey: 'F', optionText: '错误' }]
      }
      return question.options || []
    },
    renderRichText(value) {
      const content = String(value || '').trim()
      if (!content) {
        return '<p class="empty-text">未填写</p>'
      }
      return /<[a-z][\s\S]*>/i.test(content) ? content : markdownToHtml(content)
    },
    normalizeImageUrl(path) {
      if (!path) {
        return ''
      }
      if (/^(https?:)?\/\//i.test(path) || path.startsWith('data:')) {
        return path
      }
      return getFileAccessHttpUrl(path) || path
    },
    async getCodeMirror() {
      if (!this.$refs.markdownEditor) {
        return null
      }
      if (this.$refs.markdownEditor.coder) {
        return this.$refs.markdownEditor.coder
      }
      return typeof this.$refs.markdownEditor._getCoder === 'function' ? this.$refs.markdownEditor._getCoder() : null
    },
    async bindEditorEvents() {
      const editor = await this.getCodeMirror()
      if (!editor) {
        return
      }
      editor.setOption('lineWrapping', true)
      this.editorPasteHandler = event => this.handlePasteImage(event)
      editor.getWrapperElement().addEventListener('paste', this.editorPasteHandler)
      this.editorScrollHandler = () => this.handleEditorScroll()
      editor.on('scroll', this.editorScrollHandler)
    },
    async unbindEditorEvents() {
      const editor = await this.getCodeMirror()
      if (editor && this.editorScrollHandler) {
        editor.off('scroll', this.editorScrollHandler)
      }
      if (editor && this.editorPasteHandler) {
        editor.getWrapperElement().removeEventListener('paste', this.editorPasteHandler)
      }
    },
    async insertAtCursor(text) {
      const editor = await this.getCodeMirror()
      if (!editor) {
        this.editorContent = `${this.editorContent || ''}${text}`
        return
      }
      editor.replaceSelection(text)
      editor.focus()
    },
    triggerImageUpload() {
      if (!this.uploadLoading) {
        this.$refs.imageInput.value = ''
        this.$refs.imageInput.click()
      }
    },
    async handleFileChange(event) {
      const files = Array.from((event.target && event.target.files) || [])
      if (files.length) {
        await this.insertImages(files)
      }
      this.$refs.imageInput.value = ''
    },
    async handlePasteImage(event) {
      const files = Array.from((event.clipboardData && event.clipboardData.items) || [])
        .filter(item => item.kind === 'file' && item.type.indexOf('image/') === 0)
        .map(item => item.getAsFile())
        .filter(Boolean)
      if (!files.length) {
        return
      }
      event.preventDefault()
      await this.insertImages(files)
    },
    async insertImages(files) {
      this.uploadLoading = true
      try {
        for (let i = 0; i < files.length; i += 1) {
          const url = await this.uploadImage(files[i])
          await this.insertAtCursor(`\n![](${url})\n`)
        }
        this.$message.success(`已插入 ${files.length} 张图片`)
      } catch (e) {
        this.$message.error(e.message || '图片上传失败')
      } finally {
        this.uploadLoading = false
      }
    },
    compressImage(file) {
      return new Promise(resolve => {
        if (!file || !file.type || file.type.indexOf('image/') !== 0) {
          resolve(file)
          return
        }
        const reader = new FileReader()
        reader.onload = e => {
          const image = new Image()
          image.onload = () => {
            const maxWidth = 1200
            const ratio = image.width > maxWidth ? maxWidth / image.width : 1
            const width = Math.round(image.width * ratio)
            const height = Math.round(image.height * ratio)
            const canvas = document.createElement('canvas')
            canvas.width = width
            canvas.height = height
            const context = canvas.getContext('2d')
            context.drawImage(image, 0, 0, width, height)
            canvas.toBlob(blob => {
              if (!blob || blob.size >= file.size) {
                resolve(file)
                return
              }
              resolve(new File([blob], file.name, { type: blob.type || file.type, lastModified: Date.now() }))
            }, file.type === 'image/png' ? 'image/png' : 'image/jpeg', 0.9)
          }
          image.onerror = () => resolve(file)
          image.src = e.target.result
        }
        reader.onerror = () => resolve(file)
        reader.readAsDataURL(file)
      })
    },
    async uploadImage(file) {
      const compressedFile = await this.compressImage(file)
      if (this.uploadTarget === UPLOAD_TARGET_QINIU) {
        const tokenRes = await getAction('/common/qiniu/getToken', {})
        if (!tokenRes.success) {
          throw new Error(tokenRes.message || '七牛上传凭证获取失败')
        }
        const key = `${uuidGenerator()}_${compressedFile.name}`
        const formData = new FormData()
        formData.append('file', compressedFile, key)
        formData.append('token', tokenRes.result)
        formData.append('key', key)
        const result = await uploadAction(`//upload-${this.qiniuArea}.qiniup.com`, formData)
        return `${this.qiniuDomain}/${result.key || key}`
      }
      const formData = new FormData()
      formData.append('file', compressedFile, compressedFile.name)
      formData.append('biz', 'objective-homework')
      const result = await uploadAction(`${window._CONFIG['domianURL']}/sys/common/upload`, formData)
      if (!result.success) {
        throw new Error(result.message || '图片上传失败')
      }
      return getFileAccessHttpUrl(result.message) || result.message
    },
    async handleEditorScroll() {
      if (!this.syncScroll || this.syncingFrom === 'preview') {
        return
      }
      const editor = await this.getCodeMirror()
      const preview = this.$refs.previewScroll
      if (!editor || !preview) {
        return
      }
      const info = editor.getScrollInfo()
      const editorMax = Math.max(1, info.height - info.clientHeight)
      const previewMax = Math.max(0, preview.scrollHeight - preview.clientHeight)
      this.syncingFrom = 'editor'
      preview.scrollTop = previewMax * (info.top / editorMax)
      this.$nextTick(() => { this.syncingFrom = '' })
    },
    async handlePreviewScroll() {
      if (!this.syncScroll || this.syncingFrom === 'editor') {
        return
      }
      const editor = await this.getCodeMirror()
      const preview = this.$refs.previewScroll
      if (!editor || !preview) {
        return
      }
      const info = editor.getScrollInfo()
      const editorMax = Math.max(0, info.height - info.clientHeight)
      const previewMax = Math.max(1, preview.scrollHeight - preview.clientHeight)
      this.syncingFrom = 'preview'
      editor.scrollTo(null, editorMax * (preview.scrollTop / previewMax))
      this.$nextTick(() => { this.syncingFrom = '' })
    },
  }
}
</script>

<style lang="less" scoped>
.objective-editor {
  .setting-card,
  .editor-card { margin-bottom: 16px; }
  .setting-item { display: flex; align-items: center; justify-content: space-between; min-height: 32px; }
  .label { color: #555; }
  .editor-extra, .status-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
  .editor-tip { margin-bottom: 12px; color: #666; font-size: 12px; }
  .status-bar { margin-bottom: 12px; }
  .split-layout { display: flex; gap: 16px; min-height: 680px; height: 680px; align-items: stretch; }
  .pane { display: flex; flex: 1; min-width: 0; min-height: 0; border: 1px solid #f0f0f0; border-radius: 10px; background: #fff; }
  .pane-editor, .pane-preview { display: flex; flex: 1; flex-direction: column; min-height: 0; padding: 12px; }
  .pane-preview { overflow: auto; background: linear-gradient(180deg, #fcfcfc 0%, #f8fafc 100%); }
  .pane-title { margin-bottom: 12px; font-weight: 600; color: #222; }
  .sticky { position: sticky; top: 0; z-index: 2; background: linear-gradient(180deg, #fcfcfc 0%, #f8fafc 100%); }
  .markdown-editor { flex: 1; min-height: 0; }
  .markdown-editor /deep/ .full-screen-child { min-height: 0; max-height: none; height: 100%; }
  .markdown-editor /deep/ .CodeMirror { height: 100%; font-size: 15px; line-height: 1.8; }
  .preview-question-card { padding: 16px; margin-bottom: 12px; border: 1px solid #eceff3; border-radius: 12px; background: #fff; }
  .preview-question-head, .preview-option-main { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .preview-head-right { display: flex; gap: 8px; flex-wrap: wrap; }
  .preview-option-list { margin-top: 12px; }
  .preview-option { padding: 10px 12px; margin-bottom: 10px; border: 1px solid #e8eaef; border-radius: 10px; background: #fcfcfd; }
  .preview-option.correct { border-color: #52c41a; background: #f6ffed; }
  .preview-option-key { min-width: 22px; font-weight: 700; line-height: 1.8; }
  .option-content { flex: 1; min-width: 0; }
  .analysis-box { margin-top: 12px; padding: 12px; border-radius: 10px; background: #f8fafc; border: 1px dashed #dbe2ea; }
  .analysis-title { margin-bottom: 8px; font-weight: 600; color: #334155; }
  .preview-rich { word-break: break-word; color: #1f2937; }
  .preview-rich /deep/ p, .preview-rich /deep/ pre, .preview-rich /deep/ ul, .preview-rich /deep/ ol, .preview-rich /deep/ h1, .preview-rich /deep/ h2, .preview-rich /deep/ h3, .preview-rich /deep/ h4, .preview-rich /deep/ blockquote { margin-bottom: 12px; }
  .preview-rich /deep/ p:last-child, .preview-rich /deep/ pre:last-child, .preview-rich /deep/ ul:last-child, .preview-rich /deep/ ol:last-child, .preview-rich /deep/ h1:last-child, .preview-rich /deep/ h2:last-child, .preview-rich /deep/ h3:last-child, .preview-rich /deep/ h4:last-child, .preview-rich /deep/ blockquote:last-child { margin-bottom: 0; }
  .preview-rich /deep/ img { display: block; max-width: 100%; height: auto; margin-top: 8px; border-radius: 10px; }
  .preview-rich /deep/ pre { padding: 12px; overflow: auto; background: #0f172a; border-radius: 10px; color: #e2e8f0; }
  .preview-rich /deep/ code { padding: 2px 6px; border-radius: 4px; background: #f1f5f9; color: #0f172a; }
  .preview-rich /deep/ pre code { padding: 0; background: transparent; color: inherit; }
  .preview-rich /deep/ a { color: #1677ff; text-decoration: underline; }
  .preview-rich /deep/ .empty-text { color: #999; }
}
</style>
