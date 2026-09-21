package tacos.web.api;

import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class MdcContextLifterConfiguration {

    public static final String MDC_CONTEXT_REACTOR_KEY = MdcContextLifterConfiguration.class.getName();

    @PostConstruct
    public void contextOperatorHook() {
        Hooks.onEachOperator(MDC_CONTEXT_REACTOR_KEY,
            Operators.lift((sc, subscriber) -> new MdcContextLifter<>(subscriber))
        );
    }

    @PreDestroy
    public void cleanupHook() {
        Hooks.resetOnEachOperator(MDC_CONTEXT_REACTOR_KEY);
    }
}

class MdcContextLifter<T> implements CoreSubscriber<T> {

    private final CoreSubscriber<T> coreSubscriber;

    public MdcContextLifter(CoreSubscriber<T> coreSubscriber) {
        this.coreSubscriber = coreSubscriber;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        coreSubscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(T t) {
        copyToMdc(coreSubscriber.currentContext());
        try {
            coreSubscriber.onNext(t);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        copyToMdc(coreSubscriber.currentContext());
        try {
            coreSubscriber.onError(throwable);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void onComplete() {
        coreSubscriber.onComplete();
    }

    @Override
    public Context currentContext() {
        return coreSubscriber.currentContext();
    }

    private void copyToMdc(Context context) {
        if (!context.isEmpty() && context.hasKey(CorrelationIdFilter.CORRELATION_ID_KEY)) {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, context.get(CorrelationIdFilter.CORRELATION_ID_KEY));
        }
    }
}
