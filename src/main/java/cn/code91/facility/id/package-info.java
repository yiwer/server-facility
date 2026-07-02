/**
 * <h2>cn.code91.facility.id</h2>
 *
 * <p><b>Purpose:</b> Snowflake ID generation — static facade {@code IdUtil} (snow id +
 * UUID variants + id parsing), {@code SnowIdGenerator} (41+2+2+10 bit layout, clock-backwards
 * handling, test clock seam), and its configuration knobs {@code FacilityIdProperties}
 * (prefix {@code facility.id}; re-homed here from autoconfigure — C3 cycle break, spec §4.4).</p>
 *
 * <p><b>Entry classes:</b> {@code IdUtil}, {@code SnowIdGenerator}, {@code FacilityIdProperties}.</p>
 *
 * <p><b>Depends on:</b> {@code context} ({@code IdUtil} resolves the Spring-managed
 * generator via {@code SpringContextHolder}, with non-latching DEFAULT fallback — RV2-06),
 * Spring Boot configuration-properties annotations, Jakarta validation annotations.</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityIdAutoConfiguration}
 * wires {@code SnowIdGenerator} from {@code FacilityIdProperties}), downstream application code.</p>
 */
package cn.code91.facility.id;
