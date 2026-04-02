package org.jeecg.modules.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("teaching_scratch_backpack")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "teaching_scratch_backpack对象", description = "Scratch背包")
public class TeachingScratchBackpack implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    private String id;

    @ApiModelProperty(value = "所属用户id")
    private String userId;

    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "mime")
    private String mime;

    @ApiModelProperty(value = "类型")
    private String type;

    @ApiModelProperty(value = "内容存储路径")
    private String bodyPath;

    @ApiModelProperty(value = "缩略图存储路径")
    private String thumbnailPath;

    @ApiModelProperty(value = "存储类型")
    private String storageType;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @ApiModelProperty(value = "删除状态")
    private Integer delFlag;
}
