package com.example.fished.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCodeEnum {
    SUCCESS(200,"操作成功"),
    FAIL(400,"请求参数错误"),
    UNAUTHORIZED(401,"未授权"),
    FORBIDDEN(403,"无权限"),
    NOT_FOUND(404,"资源不存在"),
    INTERNAL_ERROR(500,"服务器内部错误");
    private final int code;
    private final String message;
}
