package com.example.fished.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增鱼塘的请求参数。 */
@Data
public class PondCreateDTO {

    @NotBlank(message = "鱼塘名称不能为空")
    @Size(max = 64, message = "鱼塘名称最多 64 个字符")
    private String pondName;
}
