package com.example.fished.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e){
        log.warn("业务异常",e.getMessage());
        return Result.fail(e.getCode(),e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e){
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "请求参数不合法" :
                fieldError.getField() + ":" + fieldError.getDefaultMessage();
        log.warn("参数校验失败：{}",msg);
        return Result.fail(ResultCodeEnum.FAIL.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e){

        String msg = e.getConstraintViolations().stream()
                .findFirst().map(ConstraintViolation::getMessage)
                .orElse("请求参数不合法");
        log.warn("参数校验失败：{}",msg);
        return Result.fail(ResultCodeEnum.FAIL.getCode(),msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e){
        String msg="参数"+e.getMessage()+"格式不对："+e.getValue();
        log.warn("参数类型转换失败:{}",msg);
        return Result.fail(ResultCodeEnum.FAIL.getCode(),msg);
    }

    /** 浏览器插件、扫描器会盲猜路径，找不到静态资源属于正常 404，不再当成系统异常刷日志。 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e){
        log.debug("资源不存在：{}", e.getResourcePath());
        return Result.fail(ResultCodeEnum.NOT_FOUND.getCode(),
                ResultCodeEnum.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e){
        log.error("系统异常",e);
        return Result.fail(ResultCodeEnum.INTERNAL_ERROR.getCode(),
                "系统繁忙，请稍后重试");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class
    )
    public Result<Void>
    handleMissingParam(MissingServletRequestParameterException e){
        String msg = "缺少必填参数：" + e.getParameterName();
        log.warn("参数缺失：{}", msg);
        return Result.fail(ResultCodeEnum.FAIL.getCode(), msg);
    }
}
