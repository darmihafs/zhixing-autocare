package com.hongcheng.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务项目新增/修改请求。
 */
@Data
public class ServiceItemSaveRequest {

    @NotNull(message = "所属门店不能为空")
    private Long shopId;

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 64, message = "服务名称最长 64 字符")
    private String itemName;

    @NotBlank(message = "服务品类不能为空")
    @Size(max = 16)
    private String category;

    @Size(max = 255)
    private String coverImg;

    @Size(max = 500, message = "描述最长 500 字符")
    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    private BigDecimal originalPrice;

    @Min(value = 1, message = "服务时长至少 1 分钟")
    private Integer durationMin;
}