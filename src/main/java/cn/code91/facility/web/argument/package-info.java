/**
 * <h2>cn.code91.facility.web.argument</h2>
 *
 * <p><b>Purpose:</b> Controller argument base types. {@code PageQuery} is the
 * pageable-query DTO base (pageNum / pageSize / orderBy + {@code getOffset()}
 * derivation) that application query objects extend.</p>
 *
 * <p><b>Entry classes:</b> {@code PageQuery}.</p>
 *
 * <p><b>Depends on:</b> {@code web.response} ({@code PageBaseResponse} is the paired
 * reply shape) and {@code jakarta.validation} constraint annotations — these are
 * <em>active</em> here: consuming controllers trigger them via {@code @Validated}
 * binding (unlike configuration properties, where ADR-0013 removed validation).</p>
 *
 * <p><b>Depended on by:</b> no facility package — downstream application query DTOs
 * extend {@code PageQuery} directly.</p>
 */
package cn.code91.facility.web.argument;
