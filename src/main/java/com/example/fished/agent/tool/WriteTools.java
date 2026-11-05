package com.example.fished.agent.tool;

import com.example.fished.agent.llm.LlmModels;
import com.example.fished.common.BusinessException;
import com.example.fished.common.enums.AeratorModeEnum;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.StrategyUpdateDTO;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import com.example.fished.vo.StrategyVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：本身不直接执行，AgentService 先转成待确认操作，用户确认后再调用 {@link #execute}。
 * 所有操作人来自登录会话，模型无法伪造。
 */
@Component
public class WriteTools {

    private final PondManager pondManager;
    private final AeratorStrategyService aeratorStrategyService;
    private final OperationLogService operationLogService;
    private final ObjectMapper mapper;

    public WriteTools(PondManager pondManager,
                      AeratorStrategyService aeratorStrategyService,
                      OperationLogService operationLogService,
                      ObjectMapper mapper) {
        this.pondManager = pondManager;
        this.aeratorStrategyService = aeratorStrategyService;
        this.operationLogService = operationLogService;
        this.mapper = mapper;
    }

    public List<AgentTool> all() {
        return List.of(
                new SimpleAgentTool(spec("set_manual_aerator",
                        "切换指定鱼塘增氧机的手动开关，并自动把控制模式设为 manual。这是写操作，调用后不会立即生效，需要用户在前端确认。只有用户明确要求开关增氧机时才调用。",
                        object(props(
                                "pondId", prop("integer", "鱼塘 id，可先调用 list_ponds 获取"),
                                "on", prop("boolean", "true 表示开启增氧机，false 表示关闭")),
                                List.of("pondId", "on"))),
                        false,
                        (args, ctx) -> setManualAerator(args, ctx),
                        this::describeSetManual),

                new SimpleAgentTool(spec("update_aerator_strategy",
                        "修改指定鱼塘的增氧策略。只需传要修改的字段，其余保持当前值；会与当前策略合并后校验。这是写操作，调用后不会立即生效，需要用户在前端确认。不能修改安全否决开关。",
                        object(props(
                                "pondId", prop("integer", "鱼塘 id"),
                                "mode", prop("string", "控制模式：auto 自动 / manual 手动"),
                                "onThreshold", prop("number", "开机阈值，0-20 mg/L"),
                                "offThreshold", prop("number", "关机阈值，0-20 mg/L，必须大于开机阈值"),
                                "safetyFloor", prop("number", "安全下限，0-20 mg/L，不能高于开机阈值"),
                                "predictMinutes", prop("integer", "提前开机分钟数，0-1440"),
                                "manualOn", prop("boolean", "手动模式下增氧机是否开启")),
                                List.of("pondId"))),
                        false,
                        (args, ctx) -> updateStrategy(args, ctx),
                        this::describeUpdateStrategy),

                new SimpleAgentTool(spec("pause_pond",
                        "暂停指定鱼塘的仿真时钟，鱼塘时间停止推进。这是写操作，调用后不会立即生效，需要用户在前端确认。",
                        object(props("pondId", prop("integer", "鱼塘 id")), List.of("pondId"))),
                        false,
                        (args, ctx) -> setPaused(args, ctx, true),
                        this::describePause),

                new SimpleAgentTool(spec("resume_pond",
                        "继续指定鱼塘的仿真时钟。这是写操作，调用后不会立即生效，需要用户在前端确认。",
                        object(props("pondId", prop("integer", "鱼塘 id")), List.of("pondId"))),
                        false,
                        (args, ctx) -> setPaused(args, ctx, false),
                        this::describeResume)
        );
    }

    // ------------------------------------------------------------------ 执行

    private String setManualAerator(JsonNode args, ToolContext context) {
        long pondId = pondId(args);
        boolean on = boolArg(args, "on");
        StrategyVO strategy = aeratorStrategyService.switchManual(pondId, on, context.operator());
        ObjectNode node = mapper.createObjectNode();
        node.put("ok", true);
        node.put("message", pondManager.nameOf(pondId) + (on ? "增氧机已开启" : "增氧机已关闭") + "（手动模式）");
        node.put("pondId", pondId);
        node.put("mode", strategy.getMode());
        node.put("manualOn", strategy.getManualOn());
        return node.toString();
    }

    private String updateStrategy(JsonNode args, ToolContext context) {
        long pondId = pondId(args);
        StrategyVO current = aeratorStrategyService.getStrategy(pondId);

        StrategyUpdateDTO dto = new StrategyUpdateDTO();
        dto.setMode(textArg(args, "mode") == null ? current.getMode() : textArg(args, "mode"));
        dto.setOnThreshold(bigDecimalArg(args, "onThreshold", current.getOnThreshold()));
        dto.setOffThreshold(bigDecimalArg(args, "offThreshold", current.getOffThreshold()));
        dto.setSafetyFloor(bigDecimalArg(args, "safetyFloor", current.getSafetyFloor()));
        dto.setPredictMinutes(intArg(args, "predictMinutes", current.getPredictMinutes()));
        dto.setManualOn(boolArg(args, "manualOn", Boolean.TRUE.equals(current.getManualOn())));

        validate(dto);
        StrategyVO updated = aeratorStrategyService.updateStrategy(pondId, dto, context.operator());

        ObjectNode node = mapper.createObjectNode();
        node.put("ok", true);
        node.put("message", pondManager.nameOf(pondId) + "增氧策略已更新");
        node.set("strategy", mapper.valueToTree(updated));
        return node.toString();
    }

    private String setPaused(JsonNode args, ToolContext context, boolean pause) {
        long pondId = pondId(args);
        SimulationEngine engine = pondManager.requireEngine(pondId);
        PondSnapshot snapshot = pause ? engine.pause() : engine.resume();
        String detail = pause
                ? "鱼塘#" + pondId + " 暂停仿真，鱼塘时间停在 " + snapshot.pondTime()
                : "鱼塘#" + pondId + " 继续仿真，鱼塘时间从 " + snapshot.pondTime() + " 接着走";
        operationLogService.record(OperationModuleEnum.CLOCK,
                pause ? OperationActionEnum.PAUSE : OperationActionEnum.RESUME, detail, context.operator());

        ObjectNode node = mapper.createObjectNode();
        node.put("ok", true);
        node.put("message", pondManager.nameOf(pondId) + (pause ? "仿真已暂停" : "仿真已继续"));
        node.put("pondId", pondId);
        node.put("paused", snapshot.paused());
        node.put("pondTime", snapshot.pondTime().toString());
        return node.toString();
    }

    private void validate(StrategyUpdateDTO dto) {
        if (AeratorModeEnum.getByCode(dto.getMode()).isEmpty()) {
            throw new BusinessException("控制模式只能是 auto（自动）或 manual（手动）");
        }
        double on = dto.getOnThreshold().doubleValue();
        double off = dto.getOffThreshold().doubleValue();
        double floor = dto.getSafetyFloor().doubleValue();
        checkRange("开机阈值", on);
        checkRange("关机阈值", off);
        checkRange("安全下限", floor);
        if (off <= on) {
            throw new BusinessException("关机阈值必须大于开机阈值，否则增氧机会反复开关");
        }
        if (floor > on) {
            throw new BusinessException("安全下限不能高于开机阈值");
        }
        if (dto.getPredictMinutes() == null || dto.getPredictMinutes() < 0 || dto.getPredictMinutes() > 1440) {
            throw new BusinessException("提前开机分钟数必须在 0-1440 之间");
        }
    }

    private static void checkRange(String name, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 20) {
            throw new BusinessException(name + "必须在 0-20 mg/L 之间");
        }
    }

    // ------------------------------------------------------------------ 确认文案

    private String describeSetManual(JsonNode args) {
        long pondId = pondId(args);
        boolean on = boolArg(args, "on");
        return (on ? "开启" : "关闭") + pondManager.nameOf(pondId)
                + "增氧机，并将控制模式切换为手动。";
    }

    private String describeUpdateStrategy(JsonNode args) {
        long pondId = pondId(args);
        StrategyVO current = aeratorStrategyService.getStrategy(pondId);
        List<String> changes = new ArrayList<>();
        String mode = textArg(args, "mode");
        if (mode != null && !mode.equals(current.getMode())) {
            String desc = AeratorModeEnum.getByCode(mode).map(AeratorModeEnum::getDesc).orElse(mode);
            changes.add("控制模式改为" + desc);
        }
        appendChange(changes, "开机阈值", current.getOnThreshold(), args, "onThreshold", " mg/L");
        appendChange(changes, "关机阈值", current.getOffThreshold(), args, "offThreshold", " mg/L");
        appendChange(changes, "安全下限", current.getSafetyFloor(), args, "safetyFloor", " mg/L");
        appendChange(changes, "提前开机分钟数", current.getPredictMinutes(), args, "predictMinutes", " 分钟");
        if (args.hasNonNull("manualOn")) {
            boolean manualOn = boolArg(args, "manualOn");
            if (!Boolean.valueOf(manualOn).equals(current.getManualOn())) {
                changes.add("手动开关改为" + (manualOn ? "开启" : "关闭"));
            }
        }
        String name = pondManager.nameOf(pondId);
        if (changes.isEmpty()) {
            return "提交" + name + "增氧策略修改（参数与当前一致）。";
        }
        return "修改" + name + "增氧策略：" + String.join("，", changes) + "。";
    }

    private void appendChange(List<String> changes, String label, Object current,
                              JsonNode args, String field, String unit) {
        if (!args.hasNonNull(field)) {
            return;
        }
        String newValue = args.get(field).asText();
        String oldValue = current == null ? "-" : current.toString();
        changes.add(label + " " + oldValue + " → " + newValue + unit);
    }

    private String describePause(JsonNode args) {
        return "暂停" + pondManager.nameOf(pondId(args)) + "的仿真时钟，鱼塘时间将停止推进。";
    }

    private String describeResume(JsonNode args) {
        return "继续" + pondManager.nameOf(pondId(args)) + "的仿真时钟。";
    }

    // ------------------------------------------------------------------ 参数

    private long pondId(JsonNode args) {
        if (args == null || !args.hasNonNull("pondId")) {
            throw new BusinessException("请提供 pondId，可先调用 list_ponds 获取");
        }
        long pondId = args.get("pondId").asLong();
        pondManager.requireEngine(pondId);
        return pondId;
    }

    private static boolean boolArg(JsonNode args, String name) {
        if (args == null || !args.hasNonNull(name)) {
            throw new BusinessException("请提供 " + name);
        }
        return parseBoolean(args.get(name), name);
    }

    private static boolean boolArg(JsonNode args, String name, boolean fallback) {
        if (args == null || !args.hasNonNull(name)) {
            return fallback;
        }
        return parseBoolean(args.get(name), name);
    }

    private static boolean parseBoolean(JsonNode node, String name) {
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String value = node.asText("").trim().toLowerCase();
        if (value.equals("true") || value.equals("on") || value.equals("1")
                || value.equals("开启") || value.equals("开")) {
            return true;
        }
        if (value.equals("false") || value.equals("off") || value.equals("0")
                || value.equals("关闭") || value.equals("关")) {
            return false;
        }
        throw new BusinessException(name + " 必须是 true 或 false");
    }

    private static BigDecimal bigDecimalArg(JsonNode args, String name, BigDecimal fallback) {
        if (args == null || !args.hasNonNull(name)) {
            return fallback;
        }
        double value = args.get(name).asDouble(Double.NaN);
        if (!Double.isFinite(value)) {
            throw new BusinessException(name + " 必须是数字");
        }
        return BigDecimal.valueOf(value);
    }

    private static Integer intArg(JsonNode args, String name, Integer fallback) {
        if (args == null || !args.hasNonNull(name)) {
            return fallback;
        }
        if (!args.get(name).canConvertToInt()) {
            throw new BusinessException(name + " 必须是整数");
        }
        return args.get(name).asInt();
    }

    private static String textArg(JsonNode args, String name) {
        if (args == null || !args.hasNonNull(name)) {
            return null;
        }
        String value = args.get(name).asText().trim();
        return value.isEmpty() ? null : value;
    }

    // ------------------------------------------------------------------ JSON Schema

    private static LlmModels.ToolSpec spec(String name, String description, Map<String, Object> parameters) {
        return new LlmModels.ToolSpec(name, description, parameters);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("description", description);
        return map;
    }
}
