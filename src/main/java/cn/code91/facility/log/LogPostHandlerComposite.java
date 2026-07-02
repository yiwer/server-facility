package cn.code91.facility.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * <b>日志后处理器组合器</b>
 * <p>
 * 实现组合模式，管理多个{@link LogPostHandler}实例，
 * 当日志处理时按优先级依次调用所有注册的处理器。
 * </p>
 * <p>
 * 特性：
 * </p>
 * <ul>
 *     <li>支持处理器优先级排序</li>
 *     <li>单个处理器异常不影响其他处理器执行</li>
 *     <li>自动过滤自身避免循环调用</li>
 * </ul>
 *
 * <h3>应用场景：</h3>
 * <ul>
 *     <li>日志上报到监控系统</li>
 *     <li>日志异步存储到数据库</li>
 *     <li>关键日志告警通知</li>
 * </ul>
 *
 * @author yvvb
 * @since 2025/5/4
 * @see LogPostHandler
 * @see LogUtil
 */
public class LogPostHandlerComposite implements LogPostHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogPostHandlerComposite.class);

    /**
     * 日志后处理器列表（按优先级排序）
     */
    private final List<LogPostHandler> logPostHandlerList;

    /**
     * <b>构造函数</b>
     * <p>
     * 通过Spring自动注入所有{@link LogPostHandler}实现类，
     * 并按优先级排序。
     * </p>
     *
     * @param logPostHandlerList 所有日志后处理器实例列表
     */
    public LogPostHandlerComposite(List<LogPostHandler> logPostHandlerList) {
        if (CollectionUtils.isEmpty(logPostHandlerList)) {
            this.logPostHandlerList = new ArrayList<>();
        } else {
            // 过滤掉自身，避免循环调用，并按优先级排序
            this.logPostHandlerList = logPostHandlerList.stream()
                    .filter(h -> !(h instanceof LogPostHandlerComposite))
                    .sorted(Comparator.comparingInt(Ordered::getOrder))
                    .toList();
        }
    }

    /**
     * <b>处理日志</b>
     * <p>
     * 按优先级依次调用所有注册的日志后处理器。
     * 单个处理器异常不会中断后续处理器的执行。
     * </p>
     *
     * @param context 日志上下文
     */
    @Override
    public void handle(LogContext context) {
        for (LogPostHandler handler : logPostHandlerList) {
            try {
                handler.handle(context);
            } catch (Exception e) {
                // 单个处理器异常不影响其他处理器
                LOGGER.warn("日志后处理器[{}]执行异常: {}", handler.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * <b>获取处理器优先级</b>
     * <p>
     * Composite本身不参与排序，返回最高优先级。
     * </p>
     *
     * @return 优先级顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * <b>获取已注册的处理器数量</b>
     *
     * @return 处理器数量
     */
    public int getHandlerCount() {
        return logPostHandlerList.size();
    }
}
