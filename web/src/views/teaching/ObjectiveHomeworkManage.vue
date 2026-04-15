<template>
  <a-card :bordered="false" class="objective-homework-manage-page">
    <div class="page-header">
      <div>
        <h2>客观题作业管理</h2>
        <p>集中管理线上客观题作业，分别处理班级作业和课程单元作业。</p>
      </div>
      <a-alert
        type="info"
        show-icon
        message="权限说明"
        description="是否允许老师创建、编辑、删除客观题，以及是否允许切换到客观题形态，都已经纳入后台菜单/按钮权限。"
      />
    </div>

    <a-tabs default-active-key="additional">
      <a-tab-pane key="additional" tab="班级客观题作业">
        <teaching-additional-work-list
          assignment-mode-filter="objective"
          :permission-config="additionalPermissionConfig"
        />
      </a-tab-pane>
      <a-tab-pane key="courseUnit" tab="课程单元客观题作业">
        <teaching-course-unit-list
          assignment-mode-filter="objective"
          :permission-config="courseUnitPermissionConfig"
          :hide-import-export="true"
        />
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>

<script>
import TeachingAdditionalWorkList from './TeachingAdditionalWorkList'
import TeachingCourseUnitList from './TeachingCourseUnitList'

export default {
  name: 'ObjectiveHomeworkManage',
  components: {
    TeachingAdditionalWorkList,
    TeachingCourseUnitList,
  },
  data() {
    return {
      additionalPermissionConfig: {
        add: 'objective:additional:add',
        edit: 'objective:additional:edit',
        delete: 'objective:additional:delete',
      },
      courseUnitPermissionConfig: {
        add: 'objective:courseunit:add',
        edit: 'objective:courseunit:edit',
        delete: 'objective:courseunit:delete',
      },
    }
  },
}
</script>

<style scoped>
.objective-homework-manage-page {
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.page-header h2 {
  margin-bottom: 8px;
}

.page-header p {
  margin-bottom: 0;
  color: rgba(0, 0, 0, 0.45);
}

.page-header .ant-alert {
  max-width: 520px;
}
</style>
