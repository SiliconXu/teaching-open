<template>
  <a-card :bordered="false">
    <div class="table-page-search-wrapper">
      <a-form layout="inline" @keyup.enter.native="searchQuery">
        <a-row :gutter="24">
          <a-col :xl="6" :lg="7" :md="8" :sm="24">
            <a-form-item label="课程包名">
              <j-dict-select-tag type="list" v-model="queryParam.courseId" dictCode="teaching_course,course_name,id" placeholder="请选择课程包" />
            </a-form-item>
          </a-col>

          <a-col :xl="6" :lg="7" :md="8" :sm="24">
            <span style="float: left; overflow: hidden" class="table-page-search-submitButtons">
              <a-button type="primary" @click="searchQuery" icon="search">查询</a-button>
              <a-button type="primary" @click="searchReset" icon="reload" style="margin-left: 8px">重置</a-button>
            </span>
          </a-col>
        </a-row>
      </a-form>
    </div>

    <div class="table-operator">
      <a-button v-if="canAdd" @click="handleAdd" type="primary" icon="plus">{{ addButtonText }}</a-button>
      <template v-if="!hideImportExport">
        <a-button type="primary" icon="download" @click="handleExportXls('课程单元')">导出</a-button>
        <a-upload name="file" :showUploadList="false" :multiple="false" :headers="tokenHeader" :action="importExcelUrl" @change="handleImportExcel">
          <a-button type="primary" icon="import">导入</a-button>
        </a-upload>
      </template>
      <a-dropdown v-if="canDelete && selectedRowKeys.length > 0">
        <a-menu slot="overlay">
          <a-menu-item key="1" @click="batchDel"><a-icon type="delete" />删除</a-menu-item>
        </a-menu>
        <a-button style="margin-left: 8px"> 批量操作 <a-icon type="down" /></a-button>
      </a-dropdown>
    </div>

    <div>
      <div class="ant-alert ant-alert-info" style="margin-bottom: 16px">
        <i class="anticon anticon-info-circle ant-alert-icon"></i> 已选择 <a style="font-weight: 600">{{ selectedRowKeys.length }}</a>项
        <a style="margin-left: 24px" @click="onClearSelected">清空</a>
      </div>

      <a-table
        ref="table"
        size="middle"
        bordered
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :pagination="ipagination"
        :loading="loading"
        :rowSelection="{ fixed: true, selectedRowKeys: selectedRowKeys, onChange: onSelectChange }"
        @change="handleTableChange"
      >
        <template slot="htmlSlot" slot-scope="text">
          <div v-html="text"></div>
        </template>
        <template slot="imgSlot" slot-scope="text">
          <span v-if="!text" style="font-size: 12px; font-style: italic">无此图片</span>
          <img v-else :src="getFileAccessHttpUrl(text)" height="25px" alt="图片不存在" style="max-width: 80px; font-size: 12px; font-style: italic" />
        </template>
        <template slot="fileSlot" slot-scope="text">
          <span v-if="!text" style="font-size: 12px; font-style: italic">无此文件</span>
          <a-button v-else :ghost="true" type="primary" icon="download" size="small" @click="uploadFile(text)">
            下载
          </a-button>
        </template>

        <span slot="action" slot-scope="text, record">
          <a v-if="canEditRecord(record)" @click="handleEdit(record)">编辑</a>
          <template v-if="canEditRecord(record) && canDeleteRecord(record)">
            <a-divider type="vertical" />
          </template>
          <a-dropdown v-if="canDeleteRecord(record)">
            <a class="ant-dropdown-link">更多 <a-icon type="down" /></a>
            <a-menu slot="overlay">
              <a-menu-item>
                <a-popconfirm title="确定删除吗?" @confirm="() => handleDelete(record.id)">
                  <a>删除</a>
                </a-popconfirm>
              </a-menu-item>
            </a-menu>
          </a-dropdown>
          <span v-if="!canEditRecord(record) && !canDeleteRecord(record)" style="color: rgba(0, 0, 0, 0.25)">无权限</span>
        </span>
      </a-table>
    </div>

    <teachingCourseUnit-modal ref="modalForm" @ok="modalFormOk"></teachingCourseUnit-modal>
  </a-card>
</template>

<script>
import { JeecgListMixin } from '@/mixins/JeecgListMixin'
import TeachingCourseUnitModal from './modules/TeachingCourseUnitModal'
import { hasButtonPermission } from '@/utils/buttonPermission'

export default {
  name: 'TeachingCourseUnitList',
  mixins: [JeecgListMixin],
  components: {
    TeachingCourseUnitModal,
  },
  props: {
    assignmentModeFilter: {
      type: String,
      default: '',
    },
    permissionConfig: {
      type: Object,
      default: () => ({}),
    },
    hideImportExport: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      queryParam: this.assignmentModeFilter ? { assignmentMode: this.assignmentModeFilter } : {},
      description: '课程单元管理页面',
      columns: [
        {
          title: '#',
          dataIndex: '',
          key: 'rowIndex',
          width: 60,
          align: 'center',
          customRender: function (t, r, index) {
            return parseInt(index) + 1
          },
        },
        {
          title: '单元名称',
          align: 'center',
          dataIndex: 'unitName',
        },
        {
          title: '单元简介',
          align: 'center',
          dataIndex: 'unitIntro',
        },
        {
          title: '单元封面',
          align: 'center',
          dataIndex: 'unitCover',
          scopedSlots: { customRender: 'imgSlot' },
        },
        {
          title: '课程名',
          align: 'center',
          dataIndex: 'courseName',
        },
        {
          title: '作业类型',
          align: 'center',
          dataIndex: 'courseWorkType_dictText',
        },
        {
          title: '作业形态',
          align: 'center',
          dataIndex: 'assignmentMode_dictText',
          customRender: (text, record) => {
            if (record.assignmentMode === 'objective') {
              return '线上客观题'
            }
            if (record.assignmentMode === 'file') {
              return '文件作业'
            }
            return text
          },
        },
        {
          title: '创建人',
          align: 'center',
          dataIndex: 'createBy',
        },
        {
          title: '创建日期',
          align: 'center',
          dataIndex: 'createTime',
        },
        {
          title: '排序',
          align: 'center',
          dataIndex: 'orderNum',
          sorter: true,
        },
        {
          title: '操作',
          dataIndex: 'action',
          align: 'center',
          scopedSlots: { customRender: 'action' },
        },
      ],
      url: {
        list: '/teaching/teachingCourseUnit/list',
        delete: '/teaching/teachingCourseUnit/delete',
        deleteBatch: '/teaching/teachingCourseUnit/deleteBatch',
        exportXlsUrl: '/teaching/teachingCourseUnit/exportXls',
        importExcelUrl: 'teaching/teachingCourseUnit/importExcel',
      },
      dictOptions: {},
    }
  },
  computed: {
    importExcelUrl() {
      return `${window._CONFIG['domianURL']}/${this.url.importExcelUrl}`
    },
    isObjectiveManageMode() {
      return this.assignmentModeFilter === 'objective'
    },
    addButtonText() {
      return this.isObjectiveManageMode ? '新增客观题单元' : '新增'
    },
    canAdd() {
      return this.checkPermission(this.permissionConfig.add)
    },
    canDelete() {
      return this.checkPermission(this.permissionConfig.delete)
    },
  },
  created() {
    const courseId = this.$route.query.courseId
    if (courseId) {
      this.queryParam.courseId = courseId
      this.searchQuery()
    }
  },
  methods: {
    initDictConfig() {},
    getDefaultQueryParam() {
      return this.assignmentModeFilter ? { assignmentMode: this.assignmentModeFilter } : {}
    },
    checkPermission(permission) {
      return !permission || hasButtonPermission(permission)
    },
    canEditRecord() {
      return this.checkPermission(this.permissionConfig.edit)
    },
    canDeleteRecord() {
      return this.checkPermission(this.permissionConfig.delete)
    },
    handleAdd() {
      if (this.isObjectiveManageMode && this.$refs.modalForm && this.$refs.modalForm.addObjective) {
        this.$refs.modalForm.addObjective()
        this.$refs.modalForm.title = '新增客观题单元'
        this.$refs.modalForm.disableSubmit = false
        return
      }
      this.$refs.modalForm.add()
      this.$refs.modalForm.title = '新增'
      this.$refs.modalForm.disableSubmit = false
    },
    searchReset() {
      this.queryParam = this.getDefaultQueryParam()
      this.loadData(1)
    },
  },
}
</script>
<style scoped>
@import '~@assets/less/common.less';
</style>
