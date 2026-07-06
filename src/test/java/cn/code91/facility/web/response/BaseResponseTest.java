package cn.code91.facility.web.response;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseResponse - 统一响应封装")
class BaseResponseTest {

    @Test
    @DisplayName("ok():code=200,message=success,data null,description 空串")
    void ok_noData() {
        BaseResponse<Void> r = BaseResponse.ok();
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isNull();
        assertThat(r.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("ok(data):默认 message=success 且携带数据")
    void ok_withData() {
        BaseResponse<String> r = BaseResponse.ok("payload");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("payload");
        assertThat(r.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("ok(data, message):自定义消息,code 仍 200")
    void ok_dataAndMessage() {
        BaseResponse<String> r = BaseResponse.ok("payload", "已创建");
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("已创建");
        assertThat(r.getData()).isEqualTo("payload");
    }

    @Test
    @DisplayName("err(code, message):自定义错误码与消息,data null")
    void err_codeMessage() {
        BaseResponse<Void> r = BaseResponse.err(401, "未认证");
        assertThat(r.getCode()).isEqualTo(401);
        assertThat(r.getMessage()).isEqualTo("未认证");
        assertThat(r.getData()).isNull();
        assertThat(r.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("err(ErrorType, args):code=枚举 code,message=format(args),description=getDetailedDescription")
    void err_errorType_formatsAndDescribes() {
        BaseResponse<Void> r = BaseResponse.err(FacilityErrorType.FILE_NOT_FOUND, "/tmp/x.pdf");
        assertThat(r.getCode()).isEqualTo(500600);
        // 模板无占位符 → format(args) 返回模板原文(探针实证)
        assertThat(r.getMessage()).isEqualTo("文件不存在");
        // getDetailedDescription 探针实证精确串
        assertThat(r.getDescription()).isEqualTo(
                "ErrorType{fullCode='FACILITY-500600', messageKey='facility.file.not_found', "
                        + "defaultMessage='文件不存在'}");
    }

    @Test
    @DisplayName("fromResult(ok):桥接为 200 success + data")
    void fromResult_ok() {
        BaseResponse<String> r = BaseResponse.fromResult(Result.ok("v"));
        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("success");
        assertThat(r.getData()).isEqualTo("v");
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("fromResult(err):code=错误类型 code,message=getFormattedMessage")
    void fromResult_err() {
        Result<String, WrappedError> failed = Result.err(WrappedError.of(FacilityErrorType.FILE_NOT_FOUND));
        BaseResponse<String> r = BaseResponse.fromResult(failed);
        assertThat(r.getCode()).isEqualTo(500600);
        assertThat(r.getMessage()).isEqualTo("文件不存在");
        assertThat(r.getData()).isNull();
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("isSuccess:200 → true,非 200 → false")
    void isSuccess_trueFalse() {
        assertThat(BaseResponse.ok().isSuccess()).isTrue();
        assertThat(BaseResponse.err(500, "boom").isSuccess()).isFalse();
    }
}
