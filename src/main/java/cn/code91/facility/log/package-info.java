/**
 * Legacy logging compatibility: LogUtil, LogContext and the LogPostHandler SPI remain available.
 * New code uses its owning class's standard SLF4J logger and reviewed fields, without secondary
 * static dispatch. Generic logs are not an audit store. Legacy masking cannot discover arbitrary
 * secrets and does not sanitize Throwable graphs. No logging implementation is required by the
 * library; bindings, appenders, retention and audit policy belong to the application. See ADR0049.
 */
package cn.code91.facility.log;
