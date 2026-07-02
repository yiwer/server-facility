package cn.code91.facility.copy;

/**
 * <b>拷贝接口特性</b>
 * <p>
 * 定义对象深拷贝的标准接口。实现此接口的类应该提供完整的深拷贝实现，
 * 即所有嵌套的可变对象也应该被拷贝，而不是简单的引用复制。
 * </p>
 *
 * <h3>实现原则：</h3>
 * <ul>
 *     <li>实现类的所有可变属性都应该被深拷贝</li>
 *     <li>如果属性对象也实现了CopyTrait，应调用其copy()方法</li>
 *     <li>集合类型属性需要创建新集合并深拷贝元素</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public class Person implements CopyTrait<Person> {
 *     private String name;
 *     private Address address; // Address也实现CopyTrait
 *
 *     @Override
 *     public Person copy() {
 *         Person copy = new Person();
 *         copy.name = this.name;
 *         copy.address = this.address != null ? this.address.copy() : null;
 *         return copy;
 *     }
 * }
 * }</pre>
 *
 * @param <S> 实现类的类型（自引用泛型）
 * @author yvvb
 * @since 2025/5/20
 * @see CopyUtil#copyList(java.util.List)
 */
public interface CopyTrait<S extends CopyTrait<S>> {

    /**
     * <b>创建当前对象的深拷贝</b>
     *
     * @return 当前对象的完整深拷贝副本
     */
    S copy();
}
