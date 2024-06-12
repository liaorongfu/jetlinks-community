package org.jetlinks.community.device.message.transparent;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections4.MapUtils;
import org.jetlinks.community.PropertyConstants;
import org.jetlinks.community.utils.ObjectMappers;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.DirectDeviceMessage;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.core.message.function.ThingFunctionInvokeMessage;
import org.jetlinks.core.message.property.ReadThingPropertyMessage;
import org.jetlinks.core.message.property.ReportPropertyMessage;
import org.jetlinks.core.message.property.WriteThingPropertyMessage;
import org.jetlinks.core.utils.TopicUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 实现透明消息编解码器接口
 */
@Slf4j
public class SimpleTransparentMessageCodec implements TransparentMessageCodec {
    // 引用Codec接口，用于实际的消息编解码工作
    @NonNull
    protected final Codec codec;
    // 构造函数，初始化编解码器
    public SimpleTransparentMessageCodec(@NonNull Codec codec) {
        this.codec = codec;
    }


        /**
     * 对设备消息进行编码转换为DirectDeviceMessage对象。
     * <p>
     * 方法首先使用传入的DeviceMessage创建一个EncodeContext实例，随后调用编解码器的encode方法执行实际的编码操作。
     * 若编码成功且生成了有效载荷，该方法将构建一个DirectDeviceMessage对象，设置有效载荷、消息ID、设备ID及头部信息，并以Mono对象形式返回。
     * 若编码后未生成有效载荷，则返回一个空的Mono对象，表示无需发送任何消息。
     *
     * @param message 待编码的设备消息，包含消息内容、消息ID、设备ID等信息。
     * @return 编码后的DirectDeviceMessage封装在Mono对象中，如未生成消息则返回空Mono。
     */
    @Override
    public final Mono<DirectDeviceMessage> encode(DeviceMessage message) {

        // 延迟至Mono被订阅时才执行后续操作，实现惰性编码处理。
        return Mono.defer(() -> {

            // 创建编码上下文对象以存储消息及其编码结果。
            EncodeContext context = new EncodeContext(message);

            // 调用编解码器的encode方法执行实际的编码工作，编码结果保存于context中。

            // 检查编码上下文中是否生成了有效载荷。
            if (context.payload != null) {
                // 创建一个新的DirectDeviceMessage实例用于存放编码后的消息。
                DirectDeviceMessage msg = new DirectDeviceMessage();
                // 将编码上下文中的有效载荷转换为字节数组并设置到消息中。
                msg.setPayload(ByteBufUtil.getBytes(context.payload));
                // 释放已转换的有效载荷资源，避免内存泄漏。
                ReferenceCountUtil.safeRelease(context.payload);

                // 设置消息ID与设备ID。
                msg.setMessageId(message.getMessageId());
                msg.setDeviceId(message.getDeviceId());
                // 将原消息头信息复制到新消息中。
                if (null != message.getHeaders()) {
                    message.getHeaders().forEach(msg::addHeader);
                }
                // 添加编码过程中产生的头部信息。
                context.headers.forEach(msg::addHeader);
                // 返回包含编码后消息的Mono对象。
                return Mono.just(msg);

            } else {
                // 若无有效载荷产生，返回空Mono。
                return Mono.empty();
            }
        });
    }


    /**
     * 解码设备消息。
     *
     * 该方法接收一个DirectDeviceMessage对象，使用codec解码消息内容，并进行后续的转换和处理。
     * 主要包括以下步骤：
     * 1. 使用fromCallable将解码操作转换为Mono对象，以便利用Reactive Streams进行异步处理。
     * 2. 通过flatMapMany将解码后的单个消息转换为多个DeviceMessage对象，以便进一步处理。
     * 3. 使用doOnNext对每个转换后的DeviceMessage添加额外的头信息，如消息来源(from)和设备标识(thingId)。
     *
     * @param message 直接设备消息，包含了需要解码的消息内容和相关元数据。
     * @return 返回一个Flux对象，该对象将异步地提供解码后的设备消息。
     */
    @Override
    public Flux<DeviceMessage> decode(DirectDeviceMessage message) {

        // 使用fromCallable将解码操作包装为Callable，以便在Mono中执行。
        return Mono
            .fromCallable(() -> codec.decode(new DecodeContext(message)))
            // 将解码结果转换为DeviceMessage的Flux流。
            .flatMapMany(this::convert)
            // 对每个转换后的消息添加额外的头信息。
            .doOnNext(msg -> {
                // 尝试从消息ID或头信息中获取消息来源，并将其添加到消息头中。
                String from = message.getMessageId();
                if (from == null) {
                    from = message.getHeader(PropertyConstants.uid).orElse(null);
                }
                if (from != null) {
                    msg.addHeader("decodeFrom", from);
                }
                // 设置消息的设备类型和设备ID。
                msg.thingId(message.getThingType(), message.getThingId());
            });

    }


    /**
     * 将给定的消息对象转换为DeviceMessage的Flux流。
     * 支持的输入类型包括：DeviceMessage实例、Map、Collection和Publisher。
     * 对于Map，进一步根据映射中的内容决定是创建一个属性上报消息还是尝试根据类型转换。
     * 对于Collection和Publisher，将分别对其中的每个元素进行转换。
     * 不支持的类型将导致发出UnsupportedOperationException异常。
     *
     * @param msg 待转换的消息对象。
     * @return 转换后的DeviceMessage的Flux流。
     */
    @SuppressWarnings("all")
    protected Flux<DeviceMessage> convert(Object msg) {
        // 如果消息为空，则返回空的Flux流
        if (msg == null) {
            return Flux.empty();
        }
        // 如果消息已经是DeviceMessage实例，则直接返回
        if (msg instanceof DeviceMessage) {
            return Flux.just(((DeviceMessage) msg));
        }
        // 如果消息是Map类型
        if (msg instanceof Map) {
            // 如果Map为空，则返回空的Flux流
            if (MapUtils.isEmpty(((Map) msg))) {
                return Flux.empty();
            }
            // 尝试从Map中提取消息类型，若无法提取则视为UNKNOWN类型
            MessageType type = MessageType.of(((Map<String, Object>) msg)).orElse(MessageType.UNKNOWN);
            // 如果类型为UNKNOWN，则创建一个属性上报消息
            if (type == MessageType.UNKNOWN) {
                //返回map但是未设备未设备消息,则转为属性上报
                return Flux.just(new ReportPropertyMessage().properties(((Map) msg)));
            }
            // 根据类型转换Map，并将结果转换为DeviceMessage的Flux流
            return Mono
                    .justOrEmpty(type.convert(((Map) msg)))
                    .flux()
                    .cast(DeviceMessage.class);
        }
        // 如果消息是Collection类型，将其中的每个元素转换为DeviceMessage，并合并为一个Flux流
        if (msg instanceof Collection) {
            return Flux
                    .fromIterable(((Collection<?>) msg))
                    .flatMap(this::convert);
        }
        // 如果消息是Publisher类型，将其中的每个元素转换为DeviceMessage，并合并为一个Flux流
        if (msg instanceof Publisher) {
            return Flux
                    .from(((Publisher<?>) msg))
                    .flatMap(this::convert);
        }
        // 如果消息是不支持的类型，发出异常
        return Flux.error(new UnsupportedOperationException("unsupported data:" + msg));
    }


    /**
     * 解码上下文类，用于设备直接消息的解码过程。
     * 提供了对消息时间戳、负载、JSON数据解析及路径变量提取的方法。
     */
    public static class DecodeContext {
        final DirectDeviceMessage msg; // 指向原始设备消息的引用
        final ByteBuf buffer; // 消息内容的ByteBuf形式，用于高效数据访问

        /**
         * 构造函数初始化解码上下文。
         *
         * @param msg 直接设备消息，提供消息的基本信息和数据。
         */
        DecodeContext(DirectDeviceMessage msg) {
            this.msg = msg;
            this.buffer = msg.asByteBuf();
        }

        /**
         * 获取消息的时间戳。
         *
         * @return 消息的时间戳，以长整型数值表示。
         */
        public long timestamp() {
            return msg.getTimestamp();
        }

        /**
         * 获取消息的负载数据。
         *
         * @return 消息负载的ByteBuf对象，用于数据访问和处理。
         */
        public ByteBuf payload() {
            return buffer;
        }

        /**
         * 将消息负载解析为JSON对象。
         *
         * @return 解析后的JSON对象，表示消息的数据部分。
         */
        public Object json() {
            return ObjectMappers.parseJson(ByteBufUtil.getBytes(buffer), Object.class);
        }

        /**
         * 将消息负载解析为JSON数组。
         *
         * @return 解析后的JSON数组，表示消息的数据部分。
         */
        public Object jsonArray() {
            return ObjectMappers.parseJsonArray(ByteBufUtil.getBytes(buffer), Object.class);
        }

        /**
         * 根据模式和路径提取路径变量。
         *
         * @param pattern 路径模式，包含占位符。
         * @param path 实际的路径字符串。
         * @return 包含路径变量的映射，键为占位符名称，值为对应的路径片段。
         */
        public Map<String, String> pathVars(String pattern, String path) {
            return TopicUtils.getPathVariables(pattern, path);
        }

        /**
         * 获取消息中“url”头的信息。
         *
         * @return 消息头中“url”字段的值，如果不存在则为null。
         */
        public String url() {
            return msg.getHeader("url")
                      .map(String::valueOf)
                      .orElse(null);
        }

        /**
         * 获取消息中“topic”头的信息。
         *
         * @return 消息头中“topic”字段的值，如果不存在则为null。
         */
        public String topic() {
            return msg.getHeader("topic")
                      .map(String::valueOf)
                      .orElse(null);
        }

        /**
         * 获取原始设备消息。
         *
         * @return 直接设备消息对象，包含完整的消息信息。
         */
        public DirectDeviceMessage message() {
            return msg;
        }

    }


    /**
     * <pre>{@code
     *
     * context
     * .whenReadProperty("temp",()->return "0x0122")
     * .whenFunction("func",args->{
     *
     * })
     *
     * }</pre>
     */
    public static class EncodeContext {

        private final DeviceMessage source;
        private ByteBuf payload;
        private final Map<String, Object> headers = new HashMap<>();

        public EncodeContext(DeviceMessage source) {
            this.source = source;
        }

        public DeviceMessage message() {
            return source;
        }

        public EncodeContext topic(String topic) {
            headers.put("topic", topic);
            return this;
        }

        public ByteBuf payload() {
            return payload == null ? payload = Unpooled.buffer() : payload;
        }

        public ByteBuf newBuffer() {
            return Unpooled.buffer();
        }

        @SneakyThrows
        public EncodeContext setPayload(String strOrHex, String charset) {
            if (strOrHex.startsWith("0x")) {
                payload().writeBytes(Hex.decodeHex(strOrHex.substring(2)));
            } else {
                payload().writeBytes(strOrHex.getBytes(charset));
            }
            return this;
        }

        @SneakyThrows
        public EncodeContext setPayload(String strOrHex) {
            setPayload(strOrHex, "utf-8");
            return this;
        }

        public EncodeContext setPayload(Object data) {

            if (data instanceof String) {
                setPayload(((String) data));
            }

            if (data instanceof byte[]) {
                payload().writeBytes(((byte[]) data));
            }

            if (data instanceof ByteBuf) {
                this.payload = ((ByteBuf) data);
            }
            //todo 更多类型?

            return this;
        }

        /**
         * 根据函数ID和函数提供者更新编码上下文。
         * 当源消息为ThingFunctionInvokeMessage且函数ID匹配时，使用提供的函数处理输入参数，并更新编码上下文的负载。
         *
         * @param functionId 函数ID，用于匹配消息的函数ID。
         * @param supplier   一个函数提供者，用于处理匹配消息的输入参数并返回处理结果。
         * @return 当前编码上下文实例，支持方法链式调用。
         */
        public EncodeContext whenFunction(String functionId, Function<Object, Object> supplier) {
            // 检查源消息是否为ThingFunctionInvokeMessage类型
            if (source instanceof ThingFunctionInvokeMessage) {
                ThingFunctionInvokeMessage<?> msg = ((ThingFunctionInvokeMessage<?>) source);
                // 检查函数ID是否匹配，"*"表示匹配所有函数ID
                if ("*".equals(msg.getFunctionId()) || Objects.equals(functionId, msg.getFunctionId())) {
                    // 使用提供的函数处理消息的输入参数，并更新编码上下文的负载
                    setPayload(supplier.apply(msg.inputsToMap()));
                }
            }
            // 返回当前编码上下文实例，支持方法链式调用
            return this;
        }

        /**
         * 当需要写入属性时，配置编码上下文。
         *
         * 此方法用于处理特定的属性写入逻辑。它首先检查当前源是否为写入设备属性的消息，
         * 然后根据提供的属性名称和转换函数，对属性值进行转换并设置为负载。
         * 如果属性名称为"*"，则表示对所有属性应用转换函数。
         *
         * @param property 属性名称，用于指定需要处理的属性。
         * @param supplier 一个函数，用于转换属性值。它接受当前属性值并返回转换后的值。
         * @return 返回当前编码上下文，允许链式调用。
         */
        public EncodeContext whenWriteProperty(String property, Function<Object, Object> supplier) {
            // 检查当前源是否为写入设备属性的消息
            if (source instanceof WriteThingPropertyMessage) {
                // 如果属性名称为"*"，则对所有属性应用转换函数
                if ("*".equals(property)) {
                    // 获取所有属性并应用转换函数
                    setPayload(supplier.apply(((WriteThingPropertyMessage<?>) source).getProperties()));
                    return this;
                }
                // 获取指定属性的值并应用转换函数
                Object value = ((WriteThingPropertyMessage<?>) source).getProperties().get(property);
                if (value != null) {
                    setPayload(supplier.apply(value));
                }
            }
            // 返回当前编码上下文，无论是否进行了处理
            return this;
        }

        /**
         * 当处理读取设备属性的消息时，使用提供的函数来处理属性值。
         * 这个方法允许对读取到的设备属性值进行转换或加工，然后设置为负载(payload)。
         *
         * @param supplier 一个函数，接收设备属性列表作为输入，返回一个处理后的对象。
         *                 这个函数定义了如何处理读取到的设备属性值。
         * @return 返回EncodeContext实例，允许链式调用。
         */
        public EncodeContext whenReadProperties(Function<List<String>, Object> supplier) {
            // 判断当前源消息是否为读取设备属性的消息
            if (source instanceof ReadThingPropertyMessage) {
                // 如果是，应用提供的函数来处理属性值，并设置为负载
                setPayload(supplier.apply(((ReadThingPropertyMessage<?>) source).getProperties()));
            }
            // 返回当前EncodeContext实例，支持链式调用
            return this;
        }

        /**
         * 当读取特定属性时，处理相应的逻辑。
         *
         * 此方法用于在处理读取设备属性的消息时，判断是否需要对消息载荷进行设置。
         * 如果消息源是读取设备属性的消息，并且指定的属性是需要读取的属性之一，
         * 则使用提供的供应商来获取属性值，并设置为消息的载荷。
         *
         * @param property 需要读取的属性名称。支持通配符"*"表示读取所有属性。
         * @param supplier 属性值的供应商，用于在需要时获取属性值。
         * @return 返回EncodeContext实例，允许链式调用。
         */
        public EncodeContext whenReadProperty(String property, Supplier<Object> supplier) {
            // 判断消息源是否为读取设备属性的消息
            if (source instanceof ReadThingPropertyMessage) {
                // 判断是否请求读取所有属性或指定的属性在需要读取的属性列表中
                if ("*".equals(property) || ((ReadThingPropertyMessage<?>) source).getProperties().contains(property)) {
                    // 设置属性值为消息的载荷
                    setPayload(supplier.get());
                }
            }
            // 返回EncodeContext实例，用于进一步处理或配置
            return this;
        }
    }

    /**
     * 编码和解码接口。
     * <p>
     * 该接口定义了编码和解码的通用行为。具体的编码器和解码器实现类需要实现这两个方法，
     * 以支持对特定数据类型的编码和解码操作。
     */
    public interface Codec {
        /**
         * 解码方法。
         * <p>
         * 从给定的解码上下文中提取数据，并将其解码为Java对象。
         *
         * @param context 解码上下文，提供了解码所需的信息和环境。
         * @return 解码后的对象。对象的类型取决于具体的实现和解码上下文。
         */
        Object decode(DecodeContext context);

        /**
         * 编码方法。
         * <p>
         * 将给定的Java对象编码为特定格式的数据，并放入编码上下文中。
         *
         * @param context 编码上下文，提供了编码所需的信息和环境。
         * @return 编码后的对象。对象的类型取决于具体的实现和编码上下文。
         */
        Object encode(EncodeContext context);
    }
}
