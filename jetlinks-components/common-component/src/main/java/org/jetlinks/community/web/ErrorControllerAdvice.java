package org.jetlinks.community.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.exception.DuplicateKeyException;
import org.hswebframework.ezorm.rdb.metadata.RDBColumnMetadata;
import org.hswebframework.web.crud.web.ResponseMessage;
import org.hswebframework.web.i18n.LocaleUtils;
import org.jetlinks.community.reference.DataReferenceInfo;
import org.jetlinks.community.reference.DataReferencedException;
import org.jetlinks.core.enums.ErrorCode;
import org.jetlinks.core.exception.DeviceOperationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
/**
 * 错误处理控制器建议类，用于处理全局异常并返回恰当的响应。
 */
@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorControllerAdvice {

    /**
     * 处理DecodingException异常，这通常发生在请求解码期间。
     *
     * @param decodingException 解码异常实例。
     * @return 返回一个包含错误信息的ResponseMessage的Mono对象。
     */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(DecodingException decodingException) {
        Throwable cause = decodingException.getCause();
        if (cause != null) {
            if (cause instanceof JsonMappingException) {
                // 如果异常原因是JsonMappingException，则进一步处理
                return handleException(((JsonMappingException) cause));
            }

            // 返回一个通用的非法参数错误响应
            return Mono.just(ResponseMessage.error(400, "illegal_argument", cause.getMessage()));
        }
        // 如果没有具体的异常原因，返回一个包含解码异常信息的响应
        return Mono.just(ResponseMessage.error(400, "illegal_argument", decodingException.getMessage()));
    }

    /**
     * 处理JsonMappingException异常，这通常由JSON映射错误引起。
     *
     * @param decodingException JSON映射异常实例。
     * @return 返回一个包含错误信息的ResponseMessage的Mono对象。
     */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(JsonMappingException decodingException) {
        Throwable cause = decodingException.getCause();
        if (cause != null) {
            // 如果异常原因是IllegalArgumentException，返回特定的错误响应
            if (cause instanceof IllegalArgumentException) {
                return Mono.just(ResponseMessage.error(400, "illegal_argument", cause.getMessage()));
            }
            // 否则，返回一个通用的非法参数错误响应
            return Mono.just(ResponseMessage.error(400, "illegal_argument", cause.getMessage()));
        }
        // 如果没有具体的异常原因，返回一个包含JSON映射异常信息的响应
        return Mono.just(ResponseMessage.error(400, "illegal_argument", decodingException.getMessage()));
    }

    /**
     * 处理DuplicateKeyException异常，这发生在尝试插入已存在的数据库键时。
     *
     * @param e 独特键异常实例。
     * @return 返回一个包含错误信息的ResponseMessage的Mono对象，信息具体到重复的键。
     */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ResponseMessage<?>> handleException(DuplicateKeyException e) {
        List<String> columns = e
            .getColumns()
            .stream()
            .map(RDBColumnMetadata::getAlias)
            .collect(Collectors.toList());
        if (columns.isEmpty()) {
            // 如果无法确定具体的列，返回一个通用的重复键错误消息
            return LocaleUtils
                .resolveMessageReactive("error.duplicate_key")
                .map(msg -> ResponseMessage.error(400, "duplicate_key", msg));
        }
        // 如果能确定具体的列，返回包含列信息的重复键错误消息
        return LocaleUtils
            .resolveMessageReactive("error.duplicate_key_detail", columns)
            .map(msg -> ResponseMessage.error(400, "duplicate_key", msg).result(columns));
    }

    /**
     * 处理自定义的DeviceOperationException异常，根据错误代码返回不同的状态码和消息。
     *
     * @param e 设备操作异常实例。
     * @return 返回一个包含错误信息的ResponseEntity的Mono对象，状态码和消息根据异常代码定制。
     */
    @ExceptionHandler
    public Mono<ResponseEntity<ResponseMessage<Object>>> handleException(DeviceOperationException e) {
        // 根据错误代码定制响应状态码和消息
        if (e.getCode() == ErrorCode.REQUEST_HANDLING) {
            // 特定的错误处理逻辑
            return LocaleUtils
                .resolveMessageReactive("message.device_message_handing")
                .map(msg -> ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ResponseMessage.error(200, "request_handling", msg).result(msg))
                );
        }
        HttpStatus _status = HttpStatus.INTERNAL_SERVER_ERROR;

        // 根据不同的错误代码调整响应状态码
        if (e.getCode() == ErrorCode.FUNCTION_UNDEFINED
            || e.getCode() == ErrorCode.PARAMETER_UNDEFINED) {
            _status = HttpStatus.NOT_FOUND;
        } else if (e.getCode() == ErrorCode.PARAMETER_UNDEFINED) {
            _status = HttpStatus.BAD_REQUEST;
        }

        HttpStatus status = _status;
        // 返回根据错误代码定制的响应
        return LocaleUtils
            .resolveMessageReactive(e.getCode().getText())
            .map(msg -> ResponseEntity
                .status(status)
                .body(ResponseMessage.error(status.value(), e.getCode().name().toLowerCase(), msg))
            );
    }

    /**
     * 处理数据引用异常，返回有关数据引用错误的信息。
     *
     * @param e 数据引用异常实例。
     * @return 返回一个包含错误信息和引用数据列表的ResponseMessage的Mono对象。
     */
    @ExceptionHandler
    public Mono<ResponseMessage<List<DataReferenceInfo>>> handleException(DataReferencedException e) {
        // 返回包含数据引用错误信息和引用列表的响应
        return e
            .getLocalizedMessageReactive()
            .map(msg -> {
                return ResponseMessage
                    .<List<DataReferenceInfo>>error(400,"error.data.referenced", msg)
                    .result(e.getReferenceList());
            });
    }

}

