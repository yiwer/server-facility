/**
 * Explicit-node SnowId compatibility and identifier migration. New business code uses JDK UUID.
 * SnowId preserves its55-bit layout/instance epoch parser while bounding state admission and clock waits.
 * Auto-configuration is opt-in and requires both node components. IdUtil remains a deprecated facade,
 * with an explicit process provider or current Spring provider; it has no default node or retained Spring bean.
 * Properties use Boot configuration metadata/validation annotations; generator logic uses JDK concurrency.
 * UUID and explicit-node construction can be consumed without Spring at runtime.
 */
package cn.code91.facility.id;
