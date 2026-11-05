package com.example.fished.common;

import java.math.BigDecimal;
import java.util.Objects;

public class DiffBuilder {
    private static final String EMPTY_TEXT= "提交了但没有任何字段发生变化";

    private final StringBuilder sb=new StringBuilder();

    public DiffBuilder appendIfChanged(String name, Object before, Object after) {
        if (isSame(before, after)) {
            return this;
        }
        if (!sb.isEmpty()) {
            sb.append("；");
        }
        sb.append(name).append(before).append(" → ").append(after);
        return this;
    }
    public String text() {
        return sb.isEmpty() ? EMPTY_TEXT : sb.toString();
    }
    private static boolean isSame(Object before,
                                  Object after) {
        if (before instanceof BigDecimal b && after instanceof BigDecimal a) {
            return b.compareTo(a) == 0;
        }
        return Objects.equals(before, after);
    }
}
