package org.jeecg.modules.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("teaching_objective_question_option")
@ApiModel(value = "teaching_objective_question_option对象", description = "客观题选项")
public class TeachingObjectiveQuestionOption implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ID_WORKER_STR)
    @ApiModelProperty(value = "主键")
    private String id;

    @ApiModelProperty(value = "题目ID")
    private String questionId;

    @ApiModelProperty(value = "选项键")
    private String optionKey;

    @ApiModelProperty(value = "选项文本")
    private String optionText;

    @ApiModelProperty(value = "选项图片")
    private String optionImage;

    @ApiModelProperty(value = "排序")
    private Integer sortOrder;
}
