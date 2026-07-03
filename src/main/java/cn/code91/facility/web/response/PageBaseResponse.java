package cn.code91.facility.web.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.List;

/**
 * <b>分页响应封装</b>
 * <p>
 * 继承 {@link BaseResponse}，增加分页相关字段（总条数、页码、每页大小）。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * List<User> users = userService.findPage(pageNum, pageSize);
 * long total = userService.count();
 * PageR<User> pageR = PageR.of(users, total, pageNum, pageSize);
 * }</pre>
 *
 * @param <T> 列表元素类型
 *
 * @author yvvb
 * @see BaseResponse
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PageBaseResponse<T> extends BaseResponse<List<T>> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 总条数
     */
    private long total;

    /**
     * 当前页码
     */
    private int pageNum;

    /**
     * 每页大小
     */
    private int pageSize;

    // ==================== 构造函数 ====================

    private PageBaseResponse(List<T> data, long total, int pageNum, int pageSize) {
        super(200, SUCCESS_MESSAGE, data, "");
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建分页响应
     *
     * @param list     数据列表
     * @param total    总条数
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      列表元素类型
     *
     * @return 分页响应
     */
    public static <T> PageBaseResponse<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageBaseResponse<>(list, total, pageNum, pageSize);
    }
}
