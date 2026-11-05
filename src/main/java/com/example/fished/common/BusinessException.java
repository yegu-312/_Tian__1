package com.example.fished.common;

public class BusinessException extends RuntimeException {
    private static final long serialVersionUID= 1L;

    private final int code;

    public BusinessException(String message){
        super(message);
        this.code=ResultCodeEnum.FAIL.getCode();
    }
    public BusinessException(int code,String message){
        super(message);
        this.code=code;
    }

    public int getCode(){
        return code;
    }


}
