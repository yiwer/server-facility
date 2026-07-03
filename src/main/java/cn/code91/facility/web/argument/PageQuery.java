package cn.code91.facility.web.argument;

import cn.code91.facility.web.response.PageBaseResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * <b>分页查询基类</b>
 * <p>
 * 作为分页查询DTO的基类，提供页码、每页大小和排序字段。
 * 配合 {@link PageBaseResponse} 使用。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public class UserQuery extends PageQuery {
 *     private String username;
 *     private Integer status;
 * }
 *
 * // 在 Controller 中
 * @GetMapping("/users")
 * public PageR<User> list(@Validated UserQuery query) {
 *     long offset = query.getOffset();
 *     // ...
 * }
 * }</pre>
 *
 * @author yvvb
 * @see PageBaseResponse
 * @since 2.0.0
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码（从1开始）
     */
    @Min(value = 1, message = "页码最小为1")
    private int pageNum = 1;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 200, message = "每页大小最大为200")
    private int pageSize = 10;

    /**
     * 排序字段（如 "create_time desc"）
     */
    private String orderBy;

    // ==================== 派生方法 ====================

    /**
     * 获取偏移量（用于SQL OFFSET）
     *
     * @return 偏移量
     */
    public long getOffset() {
        return (long) (pageNum - 1) * pageSize;
    }
}
