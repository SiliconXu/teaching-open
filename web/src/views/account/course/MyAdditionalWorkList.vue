<template>
  <div :class="['container']">
    <a-card class="search-card" :bordered="false">
      <j-dict-select-tag :defaultShowAll="true" type="radioButton" @change="handleChangeStatus"
        v-model="queryParam.status" :trigger-change="true" :defaultDictOptions="[{ title: '全部', text: '全部', description: '', value: '' },
        { title: '未提交', text: '未提交', description: '', value: 'false' },
        { title: '已提交', text: '已提交', description: '', value: 'true' }]" />
    </a-card>
    <a-divider />
    <a-card :bordered="false">
      <a-list item-layout="horizontal" :dataSource="datasource" :pagination="pagination">
        <a-list-item slot="renderItem" slot-scope="work">
          <a-list-item-meta>
            <img class="work-cover" slot="avatar" :src="work.workCover_url" alt="" />
            <template slot="title" class="title" href="#">
              <h3>
                {{ work.workName }}
                <a-tag :color="work.assignmentMode === 'objective' ? 'gold' : 'blue'">
                  {{ work.assignmentMode === 'objective' ? '客观题' : work.codeType_dictText }}
                </a-tag>
                <a-tag v-if="work.assignmentMode === 'objective' && work.redoLimit > 0" color="orange">
                  可重做 {{ work.redoLimit }} 次
                </a-tag>
              </h3>
            </template>
            <template slot="description">
              <pre class="work-desc">{{ work.workDesc }}</pre>
              <div class="work-info">
                <a-tag>班级：{{ work.departName }}</a-tag>
                <a-divider type="vertical" />
                <a-tag>老师：{{ work.createBy_dictText }}</a-tag>
                <template v-if="work.assignmentMode === 'objective' && work.objectiveScore != null">
                  <a-divider type="vertical" />
                  <a-tag color="green">得分：{{ work.objectiveScore }}/{{ work.objectiveTotalScore }}</a-tag>
                </template>
              </div>
            </template>
          </a-list-item-meta>
          <div slot="extra" class="btns">
            <a-tooltip>
              <template slot="title">
                <p>{{ work.comment }}</p>
              </template>
              <a-rate v-if="work.score" :disabled="true" :value="work.score" />
            </a-tooltip>
            <a-button v-if="work.workDocumentUrl" @click="openWorkFile(work.workDocumentUrl)">作业资料</a-button>
            <!-- <a-divider v-if="work.workDocumentUrl != null" type="vertical" /> -->
            <a-button type="primary" @click="toAdditionalWork(work, false)">
              {{ actionText(work) }}
            </a-button>
            <a-divider v-if="showRedo(work)" type="vertical" />
            <a-button type="primary" v-if="showRedo(work)" @click="toAdditionalWork(work, true)"> 重做 </a-button>
          </div>
        </a-list-item>
      </a-list>
    </a-card>
    <TeachingWorkSubmitModal ref="submitModal" />
  </div>
</template>

<script>
import { getAction } from '@/api/manage'
import { mixinDevice } from '@/utils/mixin.js'
import JDictSelectTag from '@/components/dict/JDictSelectTag.vue'
import TeachingWorkSubmitModal from '@/views/teaching/modules/TeachingWorkSubmitModal'
import { getFileAccessHttpUrl, getFilePrevew } from '@/api/manage'

export default {
  mixins: [mixinDevice],
  components: {
    JDictSelectTag,
    TeachingWorkSubmitModal
  },
  data() {
    return {
      datasource: [],
      pagination: {
        onChange: (page) => {
          console.log(page)
        },
        pageSize: 8,
      },
      loading: true,
      queryParam: { status: 'false' },
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getFilePrevew,
    normalizeSubmitStatus() {
      if (this.queryParam.status === '') {
        return undefined
      }
      if (this.queryParam.status === 'true' || this.queryParam.status === true) {
        return true
      }
      if (this.queryParam.status === 'false' || this.queryParam.status === false) {
        return false
      }
      return undefined
    },
    actionText(work) {
      if (work.assignmentMode === 'objective') {
        return work.objectiveScore == null ? '开始答题' : '查看结果'
      }
      return work.mineWorkStatus == null ? '去做作业' : '修改作业'
    },
    showRedo(work) {
      if (work.assignmentMode === 'objective') {
        return Number(work.remainingRedoCount || 0) > 0 && work.objectiveScore != null
      }
      return work.mineWorkStatus != null && work.mineWorkStatus < 2
    },
    getList() {
      this.loading = true
      this.datasource = []
      getAction('/teaching/teachingWork/mineAdditionalWork', {
        pageSize: 999,
        submit: this.normalizeSubmitStatus()
      }).then((res) => {
        console.log(res)
        if (res.success) {
          this.datasource = res.result
        }
        this.loading = false
      })
    },
    handleChangeStatus(v) {
      this.queryParam.status = v.target.value
      this.getList()
    },
    openWorkFile(workUrl) {
      if (workUrl.startsWith('aes') || workUrl.endsWith('ppt') || workUrl.endsWith('pptx') || workUrl.endsWith('doc') || workUrl.endsWith('docx') || workUrl.endsWith('xls') || workUrl.endsWith('xlsx')) {
        window.open(getFilePrevew(workUrl))
      } else {
        window.open(workUrl)
      }
    },
    toAdditionalWork(item, reset) {
      if (item.assignmentMode === 'objective') {
        const mode = reset || item.objectiveScore == null ? 'answer' : 'review'
        const routeData = this.$router.resolve({
          path: '/objective-homework',
          query: {
            sourceType: 'additional',
            sourceId: item.additionalWorkId,
            departId: item.departId,
            mode,
            reset: reset ? '1' : '0'
          }
        })
        window.open(routeData.href, '_blank')
        return
      }
      console.log(item);
      var workUrl
      switch (item.codeType) {
        case 1:
          workUrl =
            '/scratch3/index.html?scene=additional&additionalId=' +
            item.additionalWorkId +
            '&departId=' +
            item.departId +
            '&workName=' +
            item.workName
          break
        case 2:
          workUrl =
            '/scratch3/index.html?scene=additional&additionalId=' +
            item.additionalWorkId +
            '&departId=' +
            item.departId +
            '&workName=' +
            item.workName
          break
        case 3:
          workUrl = '/scratchjr/editor.html?scene=additional&mode=edit&additionalId=' +
            item.additionalWorkId +
            '&departId=' +
            item.departId +
            '&workName=' +
            item.workName
          break
        case 4:
          workUrl =
            '/python/index.html?scene=additional&lang=turtle&additionalId=' +
            item.additionalWorkId +
            '&departId=' +
            item.departId +
            '&workName=' +
            item.workName
          break
        default:
          //workUrl = item.workUrl_url
          this.$refs.submitModal.open({
            workName: item.workName,
            additionalId: item.additionalWorkId,
            departId: item.departId,
            workType: 0
          })
          return
      }

      if (!reset && item.mineWorkUrl) {
        workUrl += "&workFile=" + item.mineWorkUrl
      } else if (item.workUrl) {
        workUrl += "&workFile=" + getFileAccessHttpUrl(item.workUrl)
      }
      window.open(workUrl)
    },
  },
}
</script>

<style lang="less" scoped>
.ant-list-item {
  height: 180px;

  .work-cover {
    height: 150px;
    max-width: 100%;
  }

  .title {
    display: block;
    margin-top: 20px;
    font-size: 16px;
    line-height: 20px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .meta {
    margin-top: 8px;
    font-size: 12px;
    line-height: 16px;
    color: #999;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .work-desc {
    white-space: pre-wrap;
    word-wrap: break-word;
    margin-right: 10px;
    max-height: 100px;
  }

  .work-info {}

  .btns {

    .ant-rate,
    .ant-btn {
      display: block;
      margin: 10px 0;
    }
  }
}
</style>
