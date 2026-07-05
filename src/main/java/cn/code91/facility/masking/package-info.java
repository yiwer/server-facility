/**
 * <b>脱敏门面</b>——固定规则集的敏感信息遮蔽。
 *
 * <p>核心类 {@link cn.code91.facility.masking.MaskUtil}:纯 JDK 正则单遍扫描,内置六类规则
 * (键值秘密/裸 JWT/身份证/银行卡/邮箱/手机号);身份证经 GB 11643 mod 11-2、银行卡经 Luhn
 * 校验通过才遮蔽,雪花 ID、时间戳等长数字串免于误遮。纯变换契约:从不抛异常,null 透传,
 * 无有效命中返回原实例。</p>
 *
 * <p>与 log 簇的关系:{@link cn.code91.facility.log.LogUtil} 在消息写盘与 LogPostHandler
 * 分发之前默认调用 {@code MaskUtil.mask}(写前脱敏,单向依赖 log → masking);本包自身
 * 零依赖、无 Spring 装配、无错误码。设计取舍见 ADR-0020。</p>
 *
 * @author yvvb
 * @since 1.0.0
 */
package cn.code91.facility.masking;
