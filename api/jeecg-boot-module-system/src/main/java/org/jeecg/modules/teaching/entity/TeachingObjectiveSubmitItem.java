package org.jeecg.modules.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("teaching_objective_submit_item")
@ApiModel(value = "teaching_objective_submit_item对象", description = "客观题提交明细")
public class TeachingObjectiveSubmitItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ID_WORKER_STR)
    @ApiModelProperty(value = "主键")
    private String id;

    @ApiModelProperty(value = "提交ID")
    private String submitId;

    @ApiModelProperty(value = "题目ID")
    private String questionId;

    @ApiModelProperty(value = "题目快照")
    private String questionSnapshotJson;

    @ApiModelProperty(value = "学生答案")
    private String studentAnswer;

    @ApiModelProperty(value = "是否正确")
    private Boolean isCorrect;

    @ApiModelProperty(value = "得分")
    private Integer awardedScore;
}
