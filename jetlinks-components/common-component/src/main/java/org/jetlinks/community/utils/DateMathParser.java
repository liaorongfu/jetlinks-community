/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jetlinks.community.utils;


import lombok.SneakyThrows;
import org.jetlinks.core.metadata.types.DateTimeType;
import org.springframework.util.StringUtils;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.function.LongSupplier;


/**
 * 时间转换工具
 * DateMathParser 类提供了一种通过字符串表达式来计算日期时间的方法。
 * @author zhouhao
 */
public class DateMathParser {

    /**
     * 解析字符串表达式，返回计算后的毫秒时间戳。
     *
     * @param text 表达式字符串，可以以 "now()" 或 "now" 开头表示当前时间，或包含日期时间与数学运算的字符串。
     * @param now  提供当前时间的 LongSupplier，通常用于获取系统当前时间。
     * @return 计算后的时间戳，单位为毫秒。
     * @throws IllegalArgumentException 当表达式不合法时抛出。
     */
    @SneakyThrows
    public static long parse(String text, LongSupplier now) {
        long time;
        String mathString;
        // 处理以 "now()" 或 "now" 开头的字符串
        if (text.startsWith("now()")) {
            time = now.getAsLong();
            mathString = text.substring("now()".length());
        } else if (text.startsWith("now")) {
            time = now.getAsLong();
            mathString = text.substring("now".length());
        } else {
            // 处理包含 "||" 分隔符的字符串，分隔符前为日期时间，后为运算表达式
            int index = text.indexOf("||");
            if (index == -1) {
                return parseDateTime(text);
            }
            time = parseDateTime(text.substring(0, index));
            mathString = text.substring(index + 2);
        }

        // 解析运算表达式部分
        return parseMath(mathString, time);
    }

    /**
     * 解析日期时间运算表达式，返回计算后的毫秒时间戳。
     *
     * @param mathString 日期时间运算表达式字符串。
     * @param time 运算起点时间戳，单位为毫秒。
     * @return 计算后的时间戳，单位为毫秒。
     * @throws IllegalArgumentException 当表达式不合法时抛出。
     */
    private static long parseMath(final String mathString, final long time) {
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
        // 遍历表达式，执行加减运算
        for (int i = 0; i < mathString.length(); ) {
            char c = mathString.charAt(i++);
            final boolean round;
            final int sign;
            // 处理正则运算和取整情况
            if (c == '/') {
                round = true;
                sign = 1;
            } else {
                round = false;
                if (c == '+') {
                    sign = 1;
                } else if (c == '-') {
                    sign = -1;
                } else {
                    throw new IllegalArgumentException("不支持的表达式:" + mathString);
                }
            }

            // 数字解析逻辑
            final int num;
            if (!Character.isDigit(mathString.charAt(i))) {
                num = 1;
            } else {
                int numFrom = i;
                while (i < mathString.length() && Character.isDigit(mathString.charAt(i))) {
                    i++;
                }
                if (i >= mathString.length()) {
                    throw new IllegalArgumentException("不支持的表达式:" + mathString);
                }
                num = Integer.parseInt(mathString.substring(numFrom, i));
            }
            // 圆整检查
            if (round && num != 1) {
                throw new IllegalArgumentException("不支持的表达式:" + mathString);
            }
            char unit = mathString.charAt(i++);
            // 根据单位执行相应的日期时间加减运算
            switch (unit) {
                case 'y':
                    if (round) {
                        dateTime = dateTime.withDayOfYear(1).with(LocalTime.MIN);
                    } else {
                        dateTime = dateTime.plusYears(sign * num);
                    }

                    break;
                case 'M':
                    if (round) {
                        dateTime = dateTime.withDayOfMonth(1).with(LocalTime.MIN);
                    } else {
                        dateTime = dateTime.plusMonths(sign * num);
                    }

                    break;
                case 'w':
                    if (round) {
                        dateTime = dateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(LocalTime.MIN);
                    } else {
                        dateTime = dateTime.plusWeeks(sign * num);
                    }

                    break;
                case 'd':
                    if (round) {
                        dateTime = dateTime.with(LocalTime.MIN);
                    } else {
                        dateTime = dateTime.plusDays(sign * num);
                    }

                    break;
                case 'h':
                case 'H':
                    if (round) {
                        dateTime = dateTime.withMinute(0).withSecond(0).withNano(0);
                    } else {
                        dateTime = dateTime.plusHours(sign * num);
                    }

                    break;
                case 'm':
                    if (round) {
                        dateTime = dateTime.withSecond(0).withNano(0);
                    } else {
                        dateTime = dateTime.plusMinutes(sign * num);
                    }

                    break;
                case 's':
                    if (round) {
                        dateTime = dateTime.withNano(0);
                    } else {
                        dateTime = dateTime.plusSeconds(sign * num);
                    }

                    break;
                default:
                    throw new IllegalArgumentException("不支持的表达式:" + mathString);
            }
        }
        return dateTime.toInstant().toEpochMilli();
    }

    /**
     * 解析简单的日期时间字符串为毫秒时间戳。
     *
     * @param value 待解析的日期时间字符串。
     * @return 解析后的毫秒时间戳。
     * @throws IllegalArgumentException 当传入空字符串时抛出。
     */
    private static long parseDateTime(String value) {
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("cannot parse empty date");
        }
        // 使用 DateTimeType.GLOBAL 转换日期字符串，返回时间戳
        return DateTimeType.GLOBAL.convert(value).getTime();
    }
}

