/**
 * <h2>cn.code91.facility.excel</h2>
 *
 * <p><b>Purpose:</b> Excel (xls/xlsx) read/write facade over Apache POI
 * (optional). Read goes through the POI usermodel API — {@code
 * WorkbookFactory} auto-detects xls vs. xlsx, and every cell is
 * stringified via {@code DataFormatter} (formula cells take their computed
 * value first). Write produces xlsx only, via {@code SXSSFWorkbook} for
 * constant-memory streaming output.</p>
 *
 * <p><b>Entry classes:</b> {@code ExcelUtil}.</p>
 *
 * <p><b>Design (ADR-0021):</b> runtime class-probe degradation — the
 * facade has no Spring bean and no properties to gate on {@code
 * @ConditionalOnClass}, so POI availability is instead checked at each
 * call via a cached double class-probe; when POI is absent every method
 * returns {@code err(EXCEL_LIB_MISSING)} instead of letting a {@code
 * NoClassDefFoundError} escape. All POI types are isolated inside the
 * package-private {@code ExcelSupport}, which the facade only delegates
 * to once the probe passes — so {@code ExcelUtil} itself never references
 * a POI class and stays loadable on a POI-less classpath. No Spring bean
 * / no autoconfiguration / no properties.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result}; Apache POI
 * ({@code poi} + {@code poi-ooxml}, both Maven optional) at runtime only
 * inside {@code ExcelSupport}.</p>
 *
 * <p><b>Depended on by:</b> none (leaf component; downstream application
 * code consumes {@code ExcelUtil} directly).</p>
 */
package cn.code91.facility.excel;
