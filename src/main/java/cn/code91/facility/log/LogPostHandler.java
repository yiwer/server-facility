package cn.code91.facility.log;

import org.springframework.core.Ordered;

/**
 * <b>日志后处理器接口</b>
 * <p>
 * 定义日志记录后的额外处理逻辑，可用于：
 * </p>
 * <ul>
 *     <li>日志上报到监控系统（如Prometheus、ELK等）</li>
 *     <li>关键日志发送告警通知（如邮件、钉钉、企微等）</li>
 *     <li>日志异步存储到数据库</li>
 * </ul>
 *
 * <h3>实现示例：</h3>
 * <pre>{@code
 * @Component
 * public class AlertLogHandler implements LogPostHandler {
 *     @Override
 *     public void handle(LogContext context) {
 *         if (context.getLevel() == Level.ERROR) {
 *             // 发送告警通知
 *         }
 *     }
 *
 *     @Override
 *     public int getOrder() {
 *         return 0; // 优先级，数值越小优先级越高
 *     }
 * }
 * }</pre>
 *
 * @author yvvb
 * @since 2025/5/4
 * @see LogPostHandlerComposite
 * @see LogUtil
 * @see LogContext
 */
public interface LogPostHandler extends Ordered {

    /**
     * <b>处理日志</b>
     *
     * @param context {@link LogContext} 日志上下文，包含日志的完整信息
     */
    void handle(LogContext context);

    /**
     * <b>获取处理器优先级</b>
     * <p>
     * 数值越小优先级越高，默认为最低优先级。
     * </p>
     *
     * @return 优先级顺序值
     */
    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
