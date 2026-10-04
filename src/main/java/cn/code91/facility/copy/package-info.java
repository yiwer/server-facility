/**
 * Legacy copying with explicit compatibility limits. New application DTOs use named values and explicit
 * constructors (see examples/order-mapping). CopyUtil collection helpers delegate element semantics to
 * CopyTrait/Function; deprecated autoCopy has documented shallow references, null defaults and finite work.
 * No object-graph mapper, container factory or immutable-field mutation is provided.
 * Dependencies: common NullSafe and standard SLF4J only; optional diagnostics do not dispatch via LogUtil.
 */
package cn.code91.facility.copy;
