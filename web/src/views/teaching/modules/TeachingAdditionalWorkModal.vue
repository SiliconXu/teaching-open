<template>
  <j-modal
    :title="title"
    :width="width"
    :visible="visible"
    :confirmLoading="confirmLoading"
    switchFullscreen
    @ok="handleOk"
    @cancel="handleCancel"
    cancelText="关闭">
    <a-spin :spinning="confirmLoading">
      <a-form :form="form">
        <a-form-item label="状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <j-dict-select-tag type="radio" v-decorator="['status',{rules: [{required: true, message: '请选择状态!'}]}]" :trigger-change="true" dictCode="additional_work_status" placeholder="请选择状态"/>
        </a-form-item>
        <a-form-item label="作业形态" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <a-radio-group v-decorator="['assignmentMode', { initialValue: 'file' }]" :disabled="objectiveModeReadonly" @change="onAssignmentModeChange">
            <a-radio-button value="file">文件作业</a-radio-button>
            <a-radio-button v-if="canUseObjectiveMode || currentAssignmentMode === 'objective'" value="objective" :disabled="!canUseObjectiveMode">线上客观题</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="currentAssignmentMode === 'file'" label="作业类型" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <j-dict-select-tag type="list" v-decorator="['codeType']" :trigger-change="true" dictCode="work_type" placeholder="请选择作业类型"/>
        </a-form-item>
        <a-form-item label="作业名" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <a-input v-decorator="['workName',{rules: [{required: true, message: '请输入作业名!'}]}]" placeholder="请输入作业名"></a-input>
        </a-form-item>
        <a-form-item label="作业描述" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <a-textarea v-decorator="['workDesc']" rows="4" placeholder="请输入作业描述"/>
        </a-form-item>
        <a-form-item label="作业封面" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <j-upload v-decorator="['workCover']" :number="1" :fileType="'image'" :trigger-change="true"></j-upload>
        </a-form-item>
        <a-form-item label="作业资料" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <j-upload v-decorator="['workDocumentUrl']" :number="1" :trigger-change="true"></j-upload>
        </a-form-item>
        <a-form-item v-if="currentAssignmentMode === 'file'" label="作业文件" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <j-upload v-decorator="['workUrl']" :number="1" :trigger-change="true"></j-upload>
        </a-form-item>
        <a-form-item label="分配班级" :labelCol="labelCol" :wrapperCol="wrapperCol">
          <j-select-depart :onlyLeaf="true" :onlyCategory="3" :rootOpened="true" :multi="true" v-decorator="['workDept',{rules: [{required: true, message: '请选择班级!'}]}]"/>
        </a-form-item>
      </a-form>

      <a-card v-if="currentAssignmentMode === 'objective'" title="客观题配置" :bordered="false">
        <objective-homework-editor ref="objectiveEditor" v-model="objectiveConfig" />
      </a-card>
    </a-spin>
  </j-modal>
</template>

<script>
import { httpAction, getAction } from '@/api/manage'
import pick from 'lodash.pick'
import JUpload from '@/components/jeecg/JUpload'
import JSelectDepart from '@/components/jeecgbiz/JSelectDepart'
import ObjectiveHomeworkEditor from './ObjectiveHomeworkEditor'
import { hasButtonPermission } from '@/utils/buttonPermission'

export default {
  name: 'TeachingAdditionalWorkModal',
  components: {
    JUpload,
    JSelectDepart,
    ObjectiveHomeworkEditor,
  },
  data () {
    return {
      form: this.$form.createForm(this),
      title: '操作',
      width: 1100,
      visible: false,
      model: {},
      currentAssignmentMode: 'file',
      objectiveConfig: {
        allowRedo: false,
        showResultAfterSubmit: true,
        questions: []
      },
      labelCol: {
        xs: { span: 24 },
        sm: { span: 5 },
      },
      wrapperCol: {
        xs: { span: 24 },
        sm: { span: 16 },
      },
      confirmLoading: false,
      url: {
        add: '/teaching/teachingAdditionalWork/add',
        edit: '/teaching/teachingAdditionalWork/edit',
        saveObjective: '/teaching/objectiveHomework/saveAdditional',
        loadObjective: '/teaching/objectiveHomework/getBySource',
      }
    }
  },
  computed: {
    canUseObjectiveMode() {
      return hasButtonPermission('objective:additional:mode')
    },
    objectiveModeReadonly() {
      return !this.canUseObjectiveMode && this.currentAssignmentMode === 'objective'
    },
  },
  methods: {
    add () {
      this.edit({ assignmentMode: 'file' })
    },
    addObjective () {
      this.edit({ assignmentMode: 'objective' })
    },
    edit (record) {
      this.form.resetFields()
      this.model = Object.assign({}, record)
      this.visible = true
      this.currentAssignmentMode = this.model.assignmentMode || 'file'
      this.objectiveConfig = {
        allowRedo: false,
        showResultAfterSubmit: true,
        questions: []
      }
      this.$nextTick(() => {
        this.form.setFieldsValue(pick(this.model,'codeType','createTime','workName','workDesc','workCover','workDocumentUrl', 'workUrl','workDept','status','assignmentMode'))
      })
      if (this.model.id && this.currentAssignmentMode === 'objective') {
        this.loadObjectiveConfig(this.model.id)
      }
    },
    loadObjectiveConfig(id) {
      getAction(this.url.loadObjective, { sourceType: 'additional', sourceId: id }).then((res) => {
        if (res.success) {
          this.objectiveConfig = res.result
        }
      })
    },
    close () {
      this.$emit('close')
      this.visible = false
    },
    onAssignmentModeChange(e) {
      if (this.objectiveModeReadonly) {
        this.form.setFieldsValue({ assignmentMode: 'objective' })
        return
      }
      this.currentAssignmentMode = e.target.value
      if (this.currentAssignmentMode === 'objective') {
        this.form.setFieldsValue({ workUrl: undefined, codeType: undefined })
      }
    },
    handleOk () {
      const that = this
      this.form.validateFields((err, values) => {
        if (!err) {
          that.confirmLoading = true
          let formData = Object.assign({}, this.model, values)
          if (!formData.assignmentMode) {
            formData.assignmentMode = this.currentAssignmentMode
          }
          if (formData.assignmentMode === 'objective' && !this.canUseObjectiveMode) {
            this.$message.warning('当前账号没有配置客观题作业的权限')
            that.confirmLoading = false
            return
          }
          if (formData.assignmentMode === 'objective') {
            this.$refs.objectiveEditor.validate().then((objectiveConfig) => {
              return httpAction(this.url.saveObjective, {
                additionalWork: Object.assign({}, formData, { codeType: null, workUrl: null }),
                objectiveConfig
              }, 'post')
            }).then((res) => {
              if (res.success) {
                that.$message.success('保存成功')
                that.$emit('ok')
                that.close()
              } else {
                that.$message.warning(res.message)
                that.close()
              }
            }).catch(() => {
            }).finally(() => {
              that.confirmLoading = false
            })
            return
          }
          const httpurl = this.model.id ? this.url.edit : this.url.add
          const method = this.model.id ? 'put' : 'post'
          httpAction(httpurl, formData, method).then((res) => {
            if (res.success) {
              that.$message.success(res.message)
              that.$emit('ok')
            } else {
              that.$message.warning(res.message)
            }
          }).finally(() => {
            that.confirmLoading = false
            that.close()
          })
        }
      })
    },
    handleCancel () {
      this.close()
    },
    popupCallback(row){
      this.form.setFieldsValue(pick(row,'codeType', 'createTime','workName','workDesc','workCover','workDocumentUrl','workUrl','workDept','workIntegral','status','assignmentMode'))
    },
  }
}
</script>
