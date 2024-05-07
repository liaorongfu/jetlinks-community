package org.jetlinks.community.dashboard.measurements.sys;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MonitorUtils类提供了一些实用工具方法。
 */
class MonitorUtils {

    /**
     * 将浮点数四舍五入到最接近的整数个十进制。
     *
     * @param val 需要四舍五入的浮点数值。
     * @return 四舍五入后的浮点数，保留1位小数。
     */
    static float round(float val) {
        // 使用BigDecimal来保证精确的四舍五入
        return BigDecimal
            .valueOf(val) // 将浮点数转换为BigDecimal类型
            .setScale(1, RoundingMode.HALF_UP) // 设置保留1位小数，使用半上舍入模式
            .floatValue(); // 将BigDecimal转换回浮点数
    }

}

