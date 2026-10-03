package cn.code91.facility.idempotency;

/** Atomic record transition result; APPLIED says nothing about external business side effects. */
public enum ClaimUpdate { APPLIED, REJECTED, UNAVAILABLE }
