/**
 * <h2>cn.code91.facility.path</h2>
 *
 * <p><b>Purpose:</b> Filename safety — path-traversal defence ({@code sanitize}),
 * extension extraction and allow/deny checks. Pure string manipulation, no IO.</p>
 *
 * <p><b>Entry classes:</b> {@code Filenames}.</p>
 *
 * <p><b>Depends on:</b> {@code error} / {@code result}, Spring core
 * ({@code StringUtils} path cleaning).</p>
 *
 * <p><b>Depended on by:</b> {@code web} ({@code SafeUpload} defence-in-depth,
 * arriving in P6), downstream application code.</p>
 */
package cn.code91.facility.path;
