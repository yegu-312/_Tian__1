package com.example.fished.agent.tool;

import com.example.fished.agent.llm.LlmModels;
import com.example.fished.common.BusinessException;
import com.example.fished.common.PageResult;
import com.example.fished.dto.AeratorLogQueryDTO;
import com.example.fished.dto.AlertQueryDTO;
import com.example.fished.service.AeratorLogService;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.service.AlertService;
import com.example.fished.service.ElectricityPriceService;
import com.example.fished.service.PondConfigService;
import com.example.fished.service.SimSettingService;
import com.example.fished.sim.DoPoint;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import com.example.fished.sim.StrategyParams;
import com.example.fished.vo.AeratorLogVO;
import com.example.fished.vo.AlertVO;
import com.example.fished.vo.PondVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：只能查数据，不能改状态。
 */
@Component
public class ReadTools {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PondManager pondManager;
    private final AlertService alertService;
    private final AeratorLogService aeratorLogService;
    private final AeratorStrategyService aeratorStrategyService;
    private final SimSettingService simSettingService;
    private final PondConfigService pondConfigService;
    private final ElectricityPriceService electricityPriceService;
    private final ObjectMapper mapper;

    public ReadTools(PondManager pondManager,
                     AlertService alertService,
                     AeratorLogService aeratorLogService,
                     AeratorStrategyService aeratorStrategyService,
                     SimSettingService simSettingService,
                     PondConfigService pondConfigService,
                     ElectricityPriceService electricityPriceService,
                     ObjectMapper mapper) {
        this.pondManager = pondManager;
        this.alertService = alertService;
        this.aeratorLogService = aeratorLogService;
        this.aeratorStrategyService = aeratorStrategyService;
        this.simSettingService = simSettingService;
        this.pondConfigService = pondConfigService;
        this.electricityPriceService = electricityPriceService;
        this.mapper = mapper;
    }

    public List<AgentTool> all() {
        return List.of(
                tool("get_current_time",
                        "查询现实世界的当前日期、时间、星期和时区。用户问“现在几点”“今天几号/星期几”，或需要基于现实时间计算间隔时调用。注意：这是服务器所在现实世界的时间，不是鱼塘仿真时间。",
                        object(props(
                                "timezone", prop("string", "可选，IANA 时区，如 Asia/Shanghai、UTC，默认使用服务器时区")),
                                List.of()),
                        (args, ctx) -> currentTime(args)),

                tool("list_ponds",
                        "列出系统中所有鱼塘的 pondId 和名称。当用户提到“所有鱼塘”“哪口塘”或需要确定 pondId 时先调用。",
                        object(new LinkedHashMap<>(), List.of()),
                        (args, ctx) -> listPonds()),

                tool("get_pond_realtime",
                        "查询指定鱼塘的实时状态：当前溶氧、水温、增氧机开关、暂停/倍速、开机/关机/安全阈值，以及未来一段时间的预测最低溶氧。用户问“现在/当前”数据时调用。",
                        object(props(
                                "pondId", prop("integer", "鱼塘 id，可先调用 list_ponds 获取"),
                                "predictMinutes", prop("integer", "预测未来多少分钟，0-240，默认 30")),
                                List.of("pondId")),
                        (args, ctx) -> realtime(args)),

                tool("get_pond_history",
                        "查询指定鱼塘最近的历史溶氧采样点，并返回首值、末值、最小值、最大值等趋势摘要。用户问“最近/过去/走势/变化”时调用。",
                        object(props(
                                "pondId", prop("integer", "鱼塘 id"),
                                "limit", prop("integer", "返回的采样点数量，1-200，默认 60")),
                                List.of("pondId")),
                        (args, ctx) -> history(args)),

                tool("compare_ponds",
                        "对比多口鱼塘的实时溶氧、水温、增氧机状态和预测最低值。用户问“对比”“哪口塘更危险”时调用；不传 pondIds 则对比全部鱼塘（最多 4 口）。",
                        object(props(
                                "pondIds", arrayProp("要对比的鱼塘 id 列表，最多 4 个"),
                                "predictMinutes", prop("integer", "预测未来多少分钟，0-240，默认 30")),
                                List.of()),
                        (args, ctx) -> compare(args)),

                tool("list_alerts",
                        "分页查询告警记录，可按鱼塘、状态（active/acknowledged）、等级（warning/critical 等）筛选。用户问“有哪些告警”“最近的告警”时调用。",
                        object(props(
                                "pondId", prop("integer", "可选，只查某个鱼塘"),
                                "status", prop("string", "可选，告警状态"),
                                "level", prop("string", "可选，告警等级"),
                                "limit", prop("integer", "返回条数，1-50，默认 20")),
                                List.of()),
                        (args, ctx) -> alerts(args)),

                tool("list_aerator_logs",
                        "分页查询增氧机的开关动作记录，包含触发原因、当时溶氧、操作人。用户问“增氧机什么时候开过”“最近的增氧记录”时调用。",
                        object(props(
                                "pondId", prop("integer", "可选，只查某个鱼塘"),
                                "action", prop("string", "可选，动作类型，如 auto_on/auto_off/manual_on/manual_off"),
                                "limit", prop("integer", "返回条数，1-50，默认 20")),
                                List.of()),
                        (args, ctx) -> aeratorLogs(args)),

                tool("get_aerator_strategy",
                        "查询指定鱼塘的增氧策略：控制模式、开机/关机阈值、安全下限、提前开机分钟数、手动开关状态。",
                        object(props("pondId", prop("integer", "鱼塘 id")), List.of("pondId")),
                        (args, ctx) -> strategy(args)),

                tool("get_pond_settings",
                        "查询指定鱼塘的运行设置：安全否决是否启用、仿真步长、采样间隔。",
                        object(props("pondId", prop("integer", "鱼塘 id")), List.of("pondId")),
                        (args, ctx) -> settings(args)),

                tool("get_pond_config",
                        "查询指定鱼塘的水体模型参数：温度模式、基础水温、日温差、溶氧范围、产氧/耗氧系数、增氧速率。",
                        object(props("pondId", prop("integer", "鱼塘 id")), List.of("pondId")),
                        (args, ctx) -> pondConfig(args)),

                tool("get_electricity",
                        "查询当前分时电价配置：峰/平/谷时段、每小时电价、当前所处时段与电价。",
                        object(new LinkedHashMap<>(), List.of()),
                        (args, ctx) -> electricity()),

                tool("predict_do",
                        "用物理模型预测指定鱼塘未来 N 分钟的溶氧走势，返回最低值、最高值、结束值，以及抽样后的预测点。预测基于当前增氧机状态不变。",
                        object(props(
                                "pondId", prop("integer", "鱼塘 id"),
                                "minutes", prop("integer", "预测时长，1-240 分钟，默认 30")),
                                List.of("pondId")),
                        (args, ctx) -> predict(args))
        );
    }

    private AgentTool tool(String name, String description, Map<String, Object> parameters,
                           java.util.function.BiFunction<JsonNode, ToolContext, String> handler) {
        return new SimpleAgentTool(new LlmModels.ToolSpec(name, description, parameters),
                true,
                (args, ctx) -> handler.apply(args == null ? mapper.createObjectNode() : args, ctx),
                null);
    }

    private String currentTime(JsonNode args) {
        String timezone = textArg(args, "timezone");
        ZoneId zone;
        try {
            zone = timezone == null ? ZoneId.systemDefault() : ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new BusinessException("时区格式不正确：" + timezone + "，示例 Asia/Shanghai 或 UTC");
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        ObjectNode node = mapper.createObjectNode();
        node.put("date", now.toLocalDate().toString());
        node.put("time", now.format(TIME_FORMAT));
        node.put("weekday", weekdayOf(now.getDayOfWeek()));
        node.put("timezone", zone.getId());
        node.put("datetime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        node.put("epochMillis", now.toInstant().toEpochMilli());
        node.put("note", "现实世界时间，不是鱼塘仿真时间；查鱼塘时间请用 get_pond_realtime");
        return json(node);
    }

    private static String weekdayOf(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private String listPonds() {
        ArrayNode arr = mapper.createArrayNode();
        for (PondVO pond : pondManager.listPonds()) {
            ObjectNode node = arr.addObject();
            node.put("pondId", pond.getId());
            node.put("name", pond.getPondName());
        }
        return json(arr);
    }

    private String realtime(JsonNode args) {
        long pondId = pondId(args);
        int predictMinutes = clamp(intArg(args, "predictMinutes", 30), 0, 240);
        SimulationEngine engine = requireEngine(pondId);
        PondSnapshot snapshot = engine.getSnapshot();
        StrategyParams strategy = engine.getStrategyParams();

        ObjectNode node = mapper.createObjectNode();
        node.put("pondId", pondId);
        node.put("pondName", pondManager.nameOf(pondId));
        node.put("pondTime", snapshot.pondTime().toString());
        node.put("currentDo", round2(snapshot.currentDo()));
        node.put("temperature", round2(snapshot.temperature()));
        node.put("aeratorOn", snapshot.aeratorOn());
        node.put("paused", snapshot.paused());
        node.put("speed", snapshot.speed());
        node.put("elapsedMinutes", snapshot.elapsedMinutes());

        ObjectNode thresholds = node.putObject("thresholds");
        thresholds.put("safetyFloor", round2(strategy.safetyFloor()));
        thresholds.put("onThreshold", round2(strategy.onThreshold()));
        thresholds.put("offThreshold", round2(strategy.offThreshold()));

        if (predictMinutes > 0) {
            List<DoPoint> prediction = engine.predict(predictMinutes);
            node.put("predictMinutes", predictMinutes);
            node.put("predictedMin", round2(prediction.stream()
                    .mapToDouble(DoPoint::doValue).min().orElse(snapshot.currentDo())));
        }
        node.put("dataUnit", "溶氧 mg/L，水温 ℃");
        return json(node);
    }

    private String history(JsonNode args) {
        long pondId = pondId(args);
        int limit = clamp(intArg(args, "limit", 60), 1, 200);
        List<DoPoint> points = requireEngine(pondId).getHistory(limit);

        ObjectNode node = mapper.createObjectNode();
        node.put("pondId", pondId);
        node.put("pondName", pondManager.nameOf(pondId));
        node.put("count", points.size());
        if (points.isEmpty()) {
            node.put("note", "当前内存中还没有历史采样点");
            return json(node);
        }

        DoPoint first = points.get(0);
        DoPoint last = points.get(points.size() - 1);
        node.put("firstDo", first.doValue());
        node.put("lastDo", last.doValue());
        node.put("minDo", points.stream().mapToDouble(DoPoint::doValue).min().orElse(0));
        node.put("maxDo", points.stream().mapToDouble(DoPoint::doValue).max().orElse(0));
        node.put("firstMinuteOffset", first.minuteOffset());
        node.put("lastMinuteOffset", last.minuteOffset());

        ArrayNode arr = node.putArray("points");
        int step = Math.max(1, points.size() / 60);
        for (int i = 0; i < points.size(); i += step) {
            DoPoint point = points.get(i);
            ObjectNode item = arr.addObject();
            item.put("minuteOffset", point.minuteOffset());
            item.put("currentDo", point.doValue());
            item.put("temperature", point.temperature());
            item.put("aeratorOn", point.aeratorOn());
        }
        return json(node);
    }

    private String compare(JsonNode args) {
        List<Long> ids = pondIdsArg(args);
        if (ids.isEmpty()) {
            ids = new ArrayList<>(pondManager.pondIds());
        }
        if (ids.size() > 4) {
            throw new BusinessException("一次最多对比 4 口鱼塘");
        }
        int predictMinutes = clamp(intArg(args, "predictMinutes", 30), 0, 240);

        ArrayNode arr = mapper.createArrayNode();
        for (Long id : ids) {
            SimulationEngine engine = requireEngine(id);
            PondSnapshot snapshot = engine.getSnapshot();
            StrategyParams strategy = engine.getStrategyParams();
            ObjectNode node = arr.addObject();
            node.put("pondId", id);
            node.put("pondName", pondManager.nameOf(id));
            node.put("currentDo", round2(snapshot.currentDo()));
            node.put("temperature", round2(snapshot.temperature()));
            node.put("aeratorOn", snapshot.aeratorOn());
            node.put("paused", snapshot.paused());
            node.put("safetyFloor", round2(strategy.safetyFloor()));
            node.put("onThreshold", round2(strategy.onThreshold()));
            if (predictMinutes > 0) {
                List<DoPoint> prediction = engine.predict(predictMinutes);
                node.put("predictedMin", round2(prediction.stream()
                        .mapToDouble(DoPoint::doValue).min().orElse(snapshot.currentDo())));
            }
        }
        ObjectNode root = mapper.createObjectNode();
        root.set("ponds", arr);
        root.put("predictMinutes", predictMinutes);
        root.put("dataUnit", "溶氧 mg/L，水温 ℃");
        return json(root);
    }

    private String alerts(JsonNode args) {
        AlertQueryDTO dto = new AlertQueryDTO();
        dto.setPage(1);
        dto.setSize(clamp(intArg(args, "limit", 20), 1, 50));
        if (args.hasNonNull("pondId")) {
            dto.setPondId(pondId(args));
        }
        dto.setStatus(textArg(args, "status"));
        dto.setLevel(textArg(args, "level"));

        PageResult<AlertVO> page = alertService.pageQuery(dto);
        ObjectNode root = mapper.createObjectNode();
        root.put("total", page.getTotal());
        ArrayNode records = root.putArray("records");
        for (AlertVO alert : page.getRecords()) {
            ObjectNode node = records.addObject();
            node.put("id", alert.getId());
            node.put("pondId", alert.getPondId());
            node.put("pondName", alert.getPondId() == null ? null : pondManager.nameOf(alert.getPondId()));
            node.put("type", alert.getAlertTypeDesc());
            node.put("level", alert.getLevelDesc());
            node.put("status", alert.getStatusDesc());
            node.put("message", alert.getMessage());
            putNumber(node, "doValue", alert.getDoValue());
            node.put("pondTime", alert.getPondTime() == null ? null : alert.getPondTime().toString());
            node.put("ackBy", alert.getAckBy());
        }
        return json(root);
    }

    private String aeratorLogs(JsonNode args) {
        AeratorLogQueryDTO dto = new AeratorLogQueryDTO();
        dto.setPage(1);
        dto.setSize(clamp(intArg(args, "limit", 20), 1, 50));
        if (args.hasNonNull("pondId")) {
            dto.setPondId(pondId(args));
        }
        dto.setAction(textArg(args, "action"));
        dto.setStartTime(dateTimeArg(args, "startTime"));
        dto.setEndTime(dateTimeArg(args, "endTime"));

        PageResult<AeratorLogVO> page = aeratorLogService.pageQuery(dto);
        ObjectNode root = mapper.createObjectNode();
        root.put("total", page.getTotal());
        ArrayNode records = root.putArray("records");
        for (AeratorLogVO log : page.getRecords()) {
            ObjectNode node = records.addObject();
            node.put("id", log.getId());
            node.put("pondId", log.getPondId());
            node.put("pondName", log.getPondId() == null ? null : pondManager.nameOf(log.getPondId()));
            node.put("action", log.getActionDesc());
            node.put("reason", log.getTriggerReason());
            putNumber(node, "doValue", log.getDoValue());
            node.put("operator", log.getOperator());
            node.put("pondTime", log.getPondTime() == null ? null : log.getPondTime().toString());
        }
        return json(root);
    }

    private String strategy(JsonNode args) {
        long pondId = pondId(args);
        requireEngine(pondId);
        return json(mapper.valueToTree(aeratorStrategyService.getStrategy(pondId)));
    }

    private String settings(JsonNode args) {
        long pondId = pondId(args);
        requireEngine(pondId);
        return json(mapper.valueToTree(simSettingService.getSetting(pondId)));
    }

    private String pondConfig(JsonNode args) {
        long pondId = pondId(args);
        requireEngine(pondId);
        return json(mapper.valueToTree(pondConfigService.getConfig(pondId)));
    }

    private String electricity() {
        return json(mapper.valueToTree(electricityPriceService.getConfig()));
    }

    private String predict(JsonNode args) {
        long pondId = pondId(args);
        int minutes = clamp(intArg(args, "minutes", 30), 1, 240);
        SimulationEngine engine = requireEngine(pondId);
        PondSnapshot snapshot = engine.getSnapshot();
        List<DoPoint> points = engine.predict(minutes);
        if (points.isEmpty()) {
            return json(mapper.createObjectNode()
                    .put("pondId", pondId)
                    .put("note", "没有可用的预测数据"));
        }

        ObjectNode node = mapper.createObjectNode();
        node.put("pondId", pondId);
        node.put("pondName", pondManager.nameOf(pondId));
        node.put("minutes", minutes);
        node.put("currentDo", round2(snapshot.currentDo()));
        node.put("aeratorOn", snapshot.aeratorOn());
        node.put("predictedMin", round2(points.stream()
                .mapToDouble(DoPoint::doValue).min().orElse(snapshot.currentDo())));
        node.put("predictedMax", round2(points.stream()
                .mapToDouble(DoPoint::doValue).max().orElse(snapshot.currentDo())));
        node.put("predictedEnd", points.get(points.size() - 1).doValue());
        node.put("note", "预测基于当前增氧机状态保持不变");

        ArrayNode arr = node.putArray("points");
        int step = Math.max(1, points.size() / 60);
        for (int i = 0; i < points.size(); i += step) {
            DoPoint point = points.get(i);
            ObjectNode item = arr.addObject();
            item.put("minuteOffset", point.minuteOffset());
            item.put("currentDo", point.doValue());
        }
        return json(node);
    }

    // ------------------------------------------------------------------

    private SimulationEngine requireEngine(long pondId) {
        if (!pondManager.pondIds().contains(pondId)) {
            throw new BusinessException("鱼塘不存在：" + pondId + "，请先调用 list_ponds");
        }
        return pondManager.requireEngine(pondId);
    }

    private static long pondId(JsonNode args) {
        if (args == null || !args.hasNonNull("pondId")) {
            throw new BusinessException("请提供 pondId，可先调用 list_ponds 获取");
        }
        return args.get("pondId").asLong();
    }

    private static List<Long> pondIdsArg(JsonNode args) {
        List<Long> ids = new ArrayList<>();
        JsonNode node = args == null ? null : args.get("pondIds");
        if (node == null || node.isNull()) {
            return ids;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.canConvertToLong()) {
                    ids.add(item.asLong());
                }
            }
        } else if (node.isTextual()) {
            for (String part : node.asText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        ids.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException e) {
                        throw new BusinessException("pondIds 格式错误：" + trimmed);
                    }
                }
            }
        } else if (node.canConvertToLong()) {
            ids.add(node.asLong());
        }
        return ids;
    }

    private static int intArg(JsonNode args, String name, int fallback) {
        JsonNode node = args == null ? null : args.get(name);
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return fallback;
        }
        return node.asInt();
    }

    private static String textArg(JsonNode args, String name) {
        JsonNode node = args == null ? null : args.get(name);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static LocalDateTime dateTimeArg(JsonNode args, String name) {
        String value = textArg(args, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(name + " 需要 ISO 时间格式，例如 2026-11-05T08:00:00");
        }
    }

    private static void putNumber(ObjectNode node, String field, java.math.BigDecimal value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String json(JsonNode node) {
        return node.toString();
    }

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

    private static Map<String, Object> arrayProp(String description) {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "integer");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "array");
        map.put("items", items);
        map.put("description", description);
        return map;
    }
}
