/**
 * <h2>cn.code91.facility.context</h2>
 *
 * <p><b>Purpose:</b> Static handle to the Spring {@code ApplicationContext}, enabling
 * programmatic bean lookup outside of injection-managed components. Lookups return
 * {@code Result} instead of throwing.</p>
 *
 * <p><b>Entry classes:</b> {@code SpringContextHolder}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result} (lookup failures surface as
 * {@code Result<T, WrappedError>}), Spring context/beans, SLF4J.</p>
 *
 * <p><b>Depended on by:</b> {@code log} ({@code LogUtil} resolves the post-handler
 * composite), {@code locale} and {@code id} (arriving in later phases),
 * {@code autoconfigure} (registers the holder as an {@code ApplicationContextAware} bean).</p>
 */
package cn.code91.facility.context;
