/**
 * Optional Excel access with positive resource budgets and explicit ownership (ADR0039).
 * {@link cn.code91.facility.excel.ExcelUtil} reads only the first sheet: bounded XLS via
 * HSSF, XLSX via SAX after bounded ZIP snapshot/preflight. Cached formula values and
 * caller-selected locale are used; formulas are never evaluated. SXSSF exports text cells
 * through a one-row window and an operation-owned temporary workspace.
 *
 * <p>The facade and budget/value types contain no POI references. POI/OOXML and their
 * production dependencies must be installed as a complete graph; absent format engines
 * return EXCEL_LIB_MISSING. Real graph consumers cover this contract. POI types are
 * confined to package-private read/write support. No Spring bean or global POI policy is
 * installed. Depends on error/result and optional POI/Commons Compress.</p>
 *
 * <p>Borrowed streams stay open. Path streams and temporary files are closed/deleted;
 * cleanup failures remain visible. Callback/iterator errors propagate after cleanup,
 * prior effects and partial outputs remain. Default collection is finite; use forEach
 * for larger XLSX with explicit budgets. XML metadata retains separately bounded state,
 * so streaming does not imply constant heap for every possible workbook.</p>
 */
package cn.code91.facility.excel;
