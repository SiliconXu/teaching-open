package org.jeecg.modules.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("teaching_objective_homework")
@ApiModel(value = "teaching_objective_homework", description = "objective homework config")
public class TeachingObjectiveHomework implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ID_WORKER_STR)
    @ApiModelProperty(value = "id")
    private String id;

    @ApiModelProperty(value = "createBy")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "createTime")
    private Date createTime;

    @ApiModelProperty(value = "updateBy")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "updateTime")
    private Date updateTime;

    @ApiModelProperty(value = "sysOrgCode")
    private String sysOrgCode;

    @ApiModelProperty(value = "sourceType")
    private String sourceType;

    @ApiModelProperty(value = "sourceId")
    private String sourceId;

    @ApiModelProperty(value = "allowRedo")
    private Boolean allowRedo;

    @ApiModelProperty(value = "showResultAfterSubmit")
    private Boolean showResultAfterSubmit;

    @ApiModelProperty(value = "questionCount")
    private Integer questionCount;

    @ApiModelProperty(value = "totalScore")
    private Integer totalScore;

    @ApiModelProperty(value = "sourceMarkdown")
    private String sourceMarkdown;
}
