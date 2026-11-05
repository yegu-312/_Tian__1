package com.example.fished.sim;

public record Decision(boolean on,boolean changed,String reason) {

    public static Decision turnOn(String reason){
        return new Decision(true,true,reason);
    }
    public static Decision turnOff(String reason){
        return new Decision(false,true,reason);
    }
    public static Decision keep(boolean currentOn){
        return new Decision(currentOn,false,null);
    }
    public  boolean shouldLog(){
        return changed;
    }
}
