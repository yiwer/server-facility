package cn.code91.facility.async;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <b>聚合异常</b>
 * <p>
 * 表示多个并行任务中收集到的所有失败原因。
 * 由 {@link Async#all} 或 {@link Async#any} 在所有任务均失败时抛出，
 * 而非只保留第一个错误，方便调用方定位每一个失败原因。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * Result<List<User>, Throwable> result = Async.all(tasks).await();
 * if (result.isErr() && result.getErr() instanceof AggregateException ae) {
 *     ae.causes().forEach(e -> log.error("subtask failed", e));
 * }
 * }</pre>
 *
 * @author yvvb
 * @since 1.0.0
 */
public final class AggregateException extends RuntimeException {

    private final List<Throwable> causes;

    AggregateException(List<Throwable> causes) {
        super(buildMessage(causes));
        this.causes = List.copyOf(causes);
    }

    private static String buildMessage(List<Throwable> causes) {
        return causes.size() + " task(s) failed: [" +
                causes.stream()
                        .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
                        .collect(Collectors.joining(", ")) +
                "]";
    }

    /**
     * 所有失败任务的异常列表（按完成顺序，不保证与提交顺序一致）
     */
    public List<Throwable> causes() {
        return causes;
    }
}
