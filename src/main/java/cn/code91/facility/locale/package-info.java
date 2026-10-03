/**
 * Application-owned localization at presentation boundaries. Boot basenames explicitly order
 * host and facility bundles; a named host MessageSource wins. The library supplies only its own
 * UTF-8 fallback when no host policy exists. ErrorTypeInterface remains pure metadata.
 * LocaleUtil and explicit AggregatedMessageSource composition retain historical compatibility;
 * new callers constructor-inject MessageSource and pass Locale. See ADR0049.
 */
package cn.code91.facility.locale;
