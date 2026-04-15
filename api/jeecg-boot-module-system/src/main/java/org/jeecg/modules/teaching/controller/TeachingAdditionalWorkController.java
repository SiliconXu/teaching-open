package org.jeecg.modules.teaching.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.system.service.ISysDepartService;
import org.jeecg.modules.teaching.entity.TeachingAdditionalWork;
import org.jeecg.modules.teaching.enums.DepartDayLogType;
import org.jeecg.modules.teaching.model.MineAdditionalWorkModel;
import org.jeecg.modules.teaching.service.ITeachingAdditionalWorkService;
import org.jeecg.modules.teaching.service.ITeachingDepartDayLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* @Description: 附加作业
*/
@Api(tags="附加作业")
@RestController
@RequestMapping("/teaching/teachingAdditionalWork")
@Slf4j
public class TeachingAdditionalWorkController extends JeecgController<TeachingAdditionalWork, ITeachingAdditionalWorkService> {
   @Autowired
   private ITeachingAdditionalWorkService teachingAdditionalWorkService;
   @Autowired
   private ISysDepartService sysDepartService;
   @Autowired
   private ITeachingDepartDayLogService teachingDepartDayLogService;
   @Autowired
   private RedisUtil redisUtil;
   @Autowired
   private JdbcTemplate jdbcTemplate;

   @ApiOperation("获取附加作业详情")
   @GetMapping("getWorkInfo")
   public Result<MineAdditionalWorkModel> getWorkInfo(@RequestParam String id){
       return teachingAdditionalWorkService.getWorkInfo(id);
   }

    /**
     * 我的附加作业列表
     * @param status ''全部 0未提交 1已提交 2已结束
     * @return
     */
//	@ApiOperation("我的附加作业列表")
//	@GetMapping("mineAdditionalWork")
//	public Result<List<MineAdditionalWorkModel>> mineAdditionalWork(@RequestParam(defaultValue = "0") Integer status){
//		LoginUser user = getCurrentUser();
//		return teachingAdditionalWorkService.mineAdditionalWork(user.getId(), status);
//	}

   /**
    * 分页列表查询
    *
    * @param teachingAdditionalWork
    * @param pageNo
    * @param pageSize
    * @param req
    * @return
    */
   @AutoLog(value = "附加作业-分页列表查询")
   @ApiOperation(value="附加作业-分页列表查询", notes="附加作业-分页列表查询")
   @GetMapping(value = "/list")
   public Result<?> queryPageList(TeachingAdditionalWork teachingAdditionalWork,
                                  @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                  @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
                                  HttpServletRequest req) {
       QueryWrapper<TeachingAdditionalWork> queryWrapper = new QueryWrapper<>();
       //非admin和dev角色，只显示自己管理的部门下的用户
       List<String> myDeptIds = new ArrayList<>();
       if(!hasRole("admin") && !hasRole("dev")){
           myDeptIds = sysDepartService.getMySubDepIdsByDepId(getCurrentUser().getDepartIds());
           if (myDeptIds==null || myDeptIds.isEmpty()){
               return Result.error("您没有负责的班级");
           }
           List<String> finalMyDeptIds = myDeptIds;
           queryWrapper.and(q->{
               for (String depId: finalMyDeptIds){
                   q.or().like("work_dept",depId);
               }
               return q;
           });
       }
       QueryGenerator.installMplus(queryWrapper, teachingAdditionalWork, req.getParameterMap());
       Page<TeachingAdditionalWork> page = new Page<TeachingAdditionalWork>(pageNo, pageSize);
       IPage<TeachingAdditionalWork> pageList = teachingAdditionalWorkService.page(page, queryWrapper);
       return Result.ok(pageList);
   }

    /**
     * 老师附加作业列表
     *
     * @param teachingAdditionalWork
     * @param pageNo
     * @param pageSize
     * @param req
     * @return
     */
    @ApiOperation(value="老师附加作业列表")
    @GetMapping(value = "/teacherList")
    public Result<?> teacherPageList(TeachingAdditionalWork teachingAdditionalWork,
                                   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
                                   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
                                   HttpServletRequest req) {
        QueryWrapper<TeachingAdditionalWork> queryWrapper = QueryGenerator.initQueryWrapper(teachingAdditionalWork, req.getParameterMap());
        Page<TeachingAdditionalWork> page = new Page<TeachingAdditionalWork>(pageNo, pageSize);
        queryWrapper.eq("create_by", getCurrentUser().getUsername());
        IPage<TeachingAdditionalWork> pageList = teachingAdditionalWorkService.page(page, queryWrapper);
        return Result.ok(pageList);
    }

   /**
    *   添加
    *
    * @param teachingAdditionalWork
    * @return
    */
   @AutoLog(value = "附加作业-添加")
   @ApiOperation(value="附加作业-添加", notes="附加作业-添加")
   @PostMapping(value = "/add")
   public Result<?> add(@RequestBody TeachingAdditionalWork teachingAdditionalWork) {
       if (teachingAdditionalWork.getAssignmentMode() == null) {
           teachingAdditionalWork.setAssignmentMode("file");
       }
       if ("objective".equals(teachingAdditionalWork.getAssignmentMode())) {
           return Result.error("线上客观题请在客观题作业管理中保存");
       }
       for (String departId: teachingAdditionalWork.getWorkDept().split(",")){
           String key = String.format("departLog:addiWorkAssign:%s", departId);
           if (!redisUtil.sHasKey(key, teachingAdditionalWork.getId())) {
               redisUtil.sSet(key, teachingAdditionalWork.getId());
               teachingDepartDayLogService.addLog(departId, DepartDayLogType.COURSE_WORK_ASSIGN_COUNT);
           }
       }
       return teachingAdditionalWorkService.addNewAdditionalWork(teachingAdditionalWork);
   }

   /**
    *  编辑
    *
    * @param teachingAdditionalWork
    * @return
    */
   @AutoLog(value = "附加作业-编辑")
   @ApiOperation(value="附加作业-编辑", notes="附加作业-编辑")
   @PutMapping(value = "/edit")
   public Result<?> edit(@RequestBody TeachingAdditionalWork teachingAdditionalWork) {
       if (teachingAdditionalWork.getAssignmentMode() == null) {
           teachingAdditionalWork.setAssignmentMode("file");
       }
       if ("objective".equals(teachingAdditionalWork.getAssignmentMode())) {
           return Result.error("线上客观题请在客观题作业管理中保存");
       }
       TeachingAdditionalWork oldWork = teachingAdditionalWorkService.getById(teachingAdditionalWork.getId());
       Result<?> permissionCheck = ensureObjectivePermission(oldWork, "objective:additional:edit");
       if (permissionCheck != null) {
           return permissionCheck;
       }
       for (String departId: teachingAdditionalWork.getWorkDept().split(",")){
           String key = String.format("departLog:addiWorkAssign:%s", departId);
           if (!redisUtil.sHasKey(key, teachingAdditionalWork.getId())) {
               redisUtil.sSet(key, teachingAdditionalWork.getId());
               teachingDepartDayLogService.addLog(departId, DepartDayLogType.COURSE_WORK_ASSIGN_COUNT);
           }
       }
       teachingAdditionalWorkService.updateById(teachingAdditionalWork);
       if (!"objective".equals(teachingAdditionalWork.getAssignmentMode())) {
           clearObjectiveHomework("additional", teachingAdditionalWork.getId());
       }
       return Result.ok("编辑成功!");
   }

   /**
    *   通过id删除
    *
    * @param id
    * @return
    */
   @AutoLog(value = "附加作业-通过id删除")
   @ApiOperation(value="附加作业-通过id删除", notes="附加作业-通过id删除")
   @DeleteMapping(value = "/delete")
   public Result<?> delete(@RequestParam(name="id",required=true) String id) {
       TeachingAdditionalWork oldWork = teachingAdditionalWorkService.getById(id);
       Result<?> permissionCheck = ensureObjectivePermission(oldWork, "objective:additional:delete");
       if (permissionCheck != null) {
           return permissionCheck;
       }
       clearObjectiveHomework("additional", id);
       teachingAdditionalWorkService.removeById(id);
       return Result.ok("删除成功!");
   }

   /**
    *  批量删除
    *
    * @param ids
    * @return
    */
   @AutoLog(value = "附加作业-批量删除")
   @ApiOperation(value="附加作业-批量删除", notes="附加作业-批量删除")
   @DeleteMapping(value = "/deleteBatch")
   public Result<?> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
       for (String id : ids.split(",")) {
           TeachingAdditionalWork oldWork = teachingAdditionalWorkService.getById(id);
           Result<?> permissionCheck = ensureObjectivePermission(oldWork, "objective:additional:delete");
           if (permissionCheck != null) {
               return permissionCheck;
           }
           clearObjectiveHomework("additional", id);
       }
       this.teachingAdditionalWorkService.removeByIds(Arrays.asList(ids.split(",")));
       return Result.ok("批量删除成功!");
   }

   /**
    * 通过id查询
    *
    * @param id
    * @return
    */
   @AutoLog(value = "附加作业-通过id查询")
   @ApiOperation(value="附加作业-通过id查询", notes="附加作业-通过id查询")
   @GetMapping(value = "/queryById")
   public Result<?> queryById(@RequestParam(name="id",required=true) String id) {
       TeachingAdditionalWork teachingAdditionalWork = teachingAdditionalWorkService.getById(id);
       if(teachingAdditionalWork==null) {
           return Result.error("未找到对应数据");
       }
       return Result.ok(teachingAdditionalWork);
   }

   private void clearObjectiveHomework(String sourceType, String sourceId) {
       jdbcTemplate.update(
           "delete si from teaching_objective_submit_item si inner join teaching_objective_submit s on s.id = si.submit_id inner join teaching_objective_homework h on h.id = s.homework_id where h.source_type=? and h.source_id=?",
           sourceType, sourceId);
       jdbcTemplate.update(
           "delete s from teaching_objective_submit s inner join teaching_objective_homework h on h.id = s.homework_id where h.source_type=? and h.source_id=?",
           sourceType, sourceId);
       jdbcTemplate.update(
           "delete qo from teaching_objective_question_option qo inner join teaching_objective_question q on q.id = qo.question_id inner join teaching_objective_homework h on h.id = q.homework_id where h.source_type=? and h.source_id=?",
           sourceType, sourceId);
       jdbcTemplate.update(
           "delete q from teaching_objective_question q inner join teaching_objective_homework h on h.id = q.homework_id where h.source_type=? and h.source_id=?",
           sourceType, sourceId);
       jdbcTemplate.update("delete from teaching_objective_homework where source_type=? and source_id=?", sourceType, sourceId);
   }

   private Result<?> ensureObjectivePermission(TeachingAdditionalWork work, String actionPerm) {
       if (work == null || !"objective".equals(work.getAssignmentMode())) {
           return null;
       }
       LoginUser currentUser = getCurrentUser();
       if (currentUser == null) {
           return Result.error("请先登录");
       }
       boolean teacherOperator = hasRole("admin") || hasRole("dev") || hasRole("teacher") || Integer.valueOf(2).equals(currentUser.getUserIdentity());
       if (!teacherOperator) {
           return Result.error("仅教师可操作客观题作业");
       }
       if (hasRole("admin") || hasRole("dev")) {
           return null;
       }
       if (!SecurityUtils.getSubject().isPermitted("objective:additional:mode") || (StringUtils.isNotBlank(actionPerm) && !SecurityUtils.getSubject().isPermitted(actionPerm))) {
           return Result.error("当前账号没有客观题作业权限");
       }
       return null;
   }
}
