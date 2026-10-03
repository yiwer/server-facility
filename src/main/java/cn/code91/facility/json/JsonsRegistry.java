package cn.code91.facility.json;

import cn.code91.facility.json.support.JsonConfig;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>多 namespace 的 Jsons 注册表</b>
 * <p>把 {@link JsonUtil} 内部原本的私有 namespace 表升级为一等公民，可被 Spring 注入：
 * <pre>{@code
 * @Autowired JsonsRegistry jsons;
 * String pretty = jsons.use("pretty").serializeUnsafe(obj);
 * }</pre>
 * Spring 应用中该 registry 是每个应用独有的实例，默认入口复用注入 Jsons。
 * generic/canonical/pretty 是显式独立预设，不代表应用 HTTP 政策；需要其他政策时显式注册。
 * JsonUtil.registry() 只管理 standalone 静态入口，应用启停不修改它。</p>
 */
public final class JsonsRegistry {

    public static final String DEFAULT = "default";
    public static final String GENERIC = "generic";
    public static final String CANONICAL = "canonical";
    public static final String PRETTY = "pretty";

    private final Map<String, Jsons> namespaces = new ConcurrentHashMap<>();
    private volatile Jsons defaultJsons;

    /**
     * 用 4 个内置 namespace（default/generic/canonical/pretty）构造，默认 namespace 使用
     * {@code JsonConfig.standard().useDefaultDateFormat()}。
     */
    public JsonsRegistry() {
        Jsons defaultJsons = new Jsons(JsonConfig.standard().useDefaultDateFormat().build());
        namespaces.put(DEFAULT, defaultJsons);
        namespaces.put(GENERIC, new Jsons(JsonConfig.standard().build()));
        namespaces.put(CANONICAL, new Jsons(JsonConfig.canonical().build()));
        namespaces.put(PRETTY, new Jsons(JsonConfig.prettyPrint().useDefaultDateFormat().build()));
        this.defaultJsons = defaultJsons;
    }

    /**
     * 提供一个外部构造的默认 {@link Jsons}（典型场景：Spring 自动配置注入 Spring 的 {@code ObjectMapper}）。
     * 其余 3 个非默认 namespace 仍走 {@link JsonConfig} 自造。
     */
    public JsonsRegistry(Jsons defaultFromSpring) {
        Objects.requireNonNull(defaultFromSpring, "defaultFromSpring cannot be null");
        namespaces.put(DEFAULT, defaultFromSpring);
        namespaces.put(GENERIC, new Jsons(JsonConfig.standard().build()));
        namespaces.put(CANONICAL, new Jsons(JsonConfig.canonical().build()));
        namespaces.put(PRETTY, new Jsons(JsonConfig.prettyPrint().useDefaultDateFormat().build()));
        this.defaultJsons = defaultFromSpring;
    }

    /**
     * 取指定 namespace 的 {@link Jsons}；未注册返回 {@code null}。
     */
    public Jsons use(String namespace) {
        if (namespace == null) return getDefault();
        return namespaces.get(namespace);
    }

    public Jsons getDefault() {
        return defaultJsons;
    }

    /**
     * 注册或替换一个 namespace 对应的 {@link Jsons}。
     */
    public void register(String namespace, Jsons jsons) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(jsons, "jsons cannot be null");
        namespaces.put(namespace, jsons);
        if (DEFAULT.equals(namespace)) {
            this.defaultJsons = jsons;
        }
    }
}
