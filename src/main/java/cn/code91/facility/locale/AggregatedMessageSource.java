package cn.code91.facility.locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

/**
 * A {@link MessageSource} that delegates to a list of others, returning the first one
 * that resolves a code. Module bundles (storage/database/document/facility) are
 * composed through this aggregator and exposed as the application's primary
 * {@code messageSource} bean.
 *
 * <p>Returns {@code null} from {@link #getMessageInternal} when no delegate resolves,
 * which makes {@link AbstractMessageSource} surface a {@link NoSuchMessageException}
 * (or a default message, if the caller supplied one).
 */
public class AggregatedMessageSource extends AbstractMessageSource {

    private final List<MessageSource> delegates;

    public AggregatedMessageSource(List<MessageSource> delegates) {
        // 按 @Order/Ordered 显式排序，使"首个命中"优先级确定（不依赖注入实现的隐式顺序）。RV2-21。
        List<MessageSource> sorted = new java.util.ArrayList<>(delegates);
        org.springframework.core.annotation.AnnotationAwareOrderComparator.sort(sorted);
        this.delegates = List.copyOf(sorted);
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        return null;
    }

    @Override
    protected String getMessageInternal(String code, Object[] args, Locale locale) {
        for (MessageSource delegate : delegates) {
            try {
                return delegate.getMessage(code, args, locale);
            } catch (NoSuchMessageException ignored) {
                // try next
            }
        }
        return null;
    }
}
