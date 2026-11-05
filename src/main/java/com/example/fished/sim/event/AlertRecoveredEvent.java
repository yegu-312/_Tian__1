package com.example.fished.sim.event;

import com.example.fished.common.enums.AlertTypeEnum;
import java.time.LocalDateTime;

public record AlertRecoveredEvent(
        long pondId,
        LocalDateTime pondTime, AlertTypeEnum alertType,double doValue
        ) implements SimEvent {
}
