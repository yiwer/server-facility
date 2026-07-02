package cn.code91.facility.copy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>字段拷贝控制注解</b>
 * <p>
 * 配合 {@link CopyUtil#autoCopy(Object)} 使用，控制反射自动拷贝时的字段行为。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public class MyData {
 *     private String name;           // 正常拷贝
 *
 *     @CopyField(ignore = true)
 *     private transient String cache; // 跳过拷贝
 * }
 * }</pre>
 *
 * @author yvvb
 * @see CopyUtil#autoCopy(Object)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CopyField {

    /**
     * 是否忽略该字段，不参与自动拷贝。
     * <p>默认为 {@code false}，即参与拷贝。</p>
     *
     * @return true 表示跳过该字段
     */
    boolean ignore() default false;
}
