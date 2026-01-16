package aster.runtime.workflow;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Workflow 元数据
 *
 * 包含 workflow 执行所需的配置信息，如重试策略、超时配置、补偿策略等。
 */
public class WorkflowMetadata {

    private final Map<String, Object> properties;

    /**
     * 创建空的 workflow 元数据
     */
    public WorkflowMetadata() {
        this.properties = new HashMap<>();
    }

    /**
     * 从已有属性映射创建 workflow 元数据
     *
     * @param properties 属性映射
     */
    public WorkflowMetadata(Map<String, Object> properties) {
        this.properties = new HashMap<>(properties);
    }

    /**
     * 获取元数据属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回 null
     */
    public Object get(String key) {
        return properties.get(key);
    }

    /**
     * 获取元数据属性（带类型转换）
     *
     * @param key 属性键
     * @param type 期望的类型
     * @param <T> 类型参数
     * @return 属性值，如果不存在或类型不匹配则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 设置元数据属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void set(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * 设置策略版本信息（Phase 3.1）
     *
     * @param policyId 策略 ID（模块名.函数名）
     * @param version 策略版本号
     * @param versionId 策略版本数据库 ID
     */
    public void setPolicyVersion(String policyId, Long version, Long versionId) {
        properties.put(Keys.POLICY_ID, policyId);
        properties.put(Keys.POLICY_VERSION, version);
        properties.put(Keys.POLICY_VERSION_ID, versionId);
    }

    /**
     * 获取策略版本 ID（Phase 3.1）
     *
     * @return 策略版本数据库 ID，如果不存在则返回 null
     */
    public Long getPolicyVersionId() {
        Object value = properties.get(Keys.POLICY_VERSION_ID);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    /**
     * 获取所有属性的只读视图
     *
     * @return 属性映射的只读视图
     */
    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * 常用元数据键常量
     */
    public static final class Keys {
        private Keys() {} // 禁止实例化

        /** 最大重试次数 */
        public static final String MAX_RETRIES = "maxRetries";

        /** 重试退避策略（exponential, linear, fixed） */
        public static final String BACKOFF_STRATEGY = "backoffStrategy";

        /** 初始退避延迟（毫秒） */
        public static final String INITIAL_BACKOFF_MS = "initialBackoffMs";

        /** 最大退避延迟（毫秒） */
        public static final String MAX_BACKOFF_MS = "maxBackoffMs";

        /** Workflow 执行超时（毫秒） */
        public static final String TIMEOUT_MS = "timeoutMs";

        /** 是否启用补偿 */
        public static final String ENABLE_COMPENSATION = "enableCompensation";

        /** 自定义标签（用于监控和过滤） */
        public static final String TAGS = "tags";

        /** 策略 ID（模块名.函数名）- Phase 3.1 */
        public static final String POLICY_ID = "policyId";

        /** 策略版本号 - Phase 3.1 */
        public static final String POLICY_VERSION = "policyVersion";

        /** 策略版本数据库 ID - Phase 3.1 */
        public static final String POLICY_VERSION_ID = "policyVersionId";
    }
}
