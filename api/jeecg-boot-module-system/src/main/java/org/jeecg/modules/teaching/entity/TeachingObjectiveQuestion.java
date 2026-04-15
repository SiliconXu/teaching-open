package org.jeecg.modules.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("teaching_objective_question")
@ApiModel(value = "teaching_objective_question对象", description = "客观题题目")
public class TeachingObjectiveQuestion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ID_WORKER_STR)
    @ApiModelProperty(value = "主键")
    private String id;

    @ApiModelProperty(value = "作业ID")
    private String homeworkId;

    @ApiModelProperty(value = "题号")
    private Integer questionNo;

    @ApiModelProperty(value = "题型")
    private String questionType;

    @ApiModelProperty(value = "题干文本")
    private String stemText;

    @ApiModelProperty(value = "题干图片")
    private String stemImages;

    @ApiModelProperty(value = "解析文本")
    private String analysisText;

    @ApiModelProperty(value = "解析图片")
    private String analysisImages;

    @ApiModelProperty(value = "正确答案")
    private String correctAnswer;

    @ApiModelProperty(value = "题目分值")
    private Integer score;

    @ApiModelProperty(value = "排序")
    private Integer sortOrder;
}
