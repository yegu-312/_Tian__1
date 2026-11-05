package com.example.fished.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String timestamp;

    private static final DateTimeFormatter FORMATTER=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String now(){
        return LocalDateTime.now().format(FORMATTER);
    }

    public static <T> Result<T> success(T data){
        Result<T> r = new Result<>();
        r.setCode(ResultCodeEnum.SUCCESS.getCode());
        r.setMessage(ResultCodeEnum.SUCCESS.getMessage());
        r.setData(data);
        r.setTimestamp(now());
        return r;
    }
    public static <T> Result<T> success(){
        return success(null);
    }

    public static <T> Result<T> fail(int code,String message){
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(null);
        r.setTimestamp(now());
        return r;
    }
    public static <T> Result<T> fail(String message){
        return fail(ResultCodeEnum.FAIL.getCode(),message);
    }
}
