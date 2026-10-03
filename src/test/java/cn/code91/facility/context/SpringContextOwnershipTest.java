package cn.code91.facility.context;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SpringContextOwnershipTest {
    private static GenericApplicationContext application(String name) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId(name);
        context.registerBean(SpringContextHolder.class);
        context.registerBean("service", String.class, () -> name);
        context.refresh();
        return context;
    }

    @Test
    void closingRejectedApplicationPreservesOwner() {
        try (var first = application("A")) {
            try (var second = application("B")) {
                assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("A");
            }
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("A");
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void refreshingApplicationIsNotPublishedBeforeItsServicesAreReady() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean(SpringContextHolder.class);
            context.registerBean("service", String.class, () -> {
                assertThat(SpringContextHolder.isNotInitialized()).isTrue();
                return "ready";
            });
            context.refresh();
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("ready");
        }
    }

    @Test
    void requestsDuringCloseDoNotResolveServicesBeingDestroyed() {
        var result = new java.util.concurrent.atomic.AtomicReference<Boolean>();
        try (var context = application("closing")) {
            context.addApplicationListener(event -> {
                if (event instanceof org.springframework.context.event.ContextClosedEvent) {
                    result.set(SpringContextHolder.getBean("service").isErr());
                }
            });
            context.close();
            assertThat(result.get()).isTrue();
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void rejectedHolderForSameApplicationCannotDestroyTheOwningInstance() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean("owner", SpringContextHolder.class);
            context.registerBean("rejected", SpringContextHolder.class);
            context.registerBean("service", String.class, () -> "A");
            context.refresh();
            context.getBean("rejected", SpringContextHolder.class).destroy();
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("A");
        }
    }

    @Test
    void manualCompatibilityRegistrationDoesNotReplaceOrOutliveApplication() {
        try (var first = application("A"); var second = new GenericApplicationContext()) {
            second.registerBean("service", String.class, () -> "B");
            second.refresh();
            SpringContextHolder.setApplicationContextManually(second);
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("A");
        }
        try (var next = new GenericApplicationContext()) {
            next.registerBean("service", String.class, () -> "next");
            next.refresh();
            SpringContextHolder.setApplicationContextManually(next);
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("next");
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void lookupRacingWithCloseReturnsAnErrorInsteadOfThrowing() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var context = new GenericApplicationContext() {
            @Override
            public Object getBean(String name) {
                if (name.equals("service")) {
                    entered.countDown();
                    await(release);
                }
                return super.getBean(name);
            }
        }; var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            context.registerBean(SpringContextHolder.class);
            context.registerBean("service", String.class, () -> "A");
            context.refresh();
            var request = executor.submit(() -> SpringContextHolder.getBean("service"));
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                context.close();
            } finally {
                release.countDown();
            }
            assertThat(request.get(5, java.util.concurrent.TimeUnit.SECONDS).isErr()).isTrue();
        }
    }


    @Test
    void constructorInjectedConsumerKeepsItsOwnServiceWhenTheOwnerClosesFirst() {
        try (var first = consumerApplication("A"); var second = consumerApplication("B")) {
            var a = first.getBean(Consumer.class);
            var b = second.getBean(Consumer.class);
            assertThat(a.read()).isEqualTo("A");
            assertThat(b.read()).isEqualTo("B");
            first.close();
            assertThat(SpringContextHolder.isNotInitialized()).isTrue();
            assertThat(b.read()).isEqualTo("B");
            assertThat(a.resource.closed).isTrue();
            assertThat(b.resource.closed).isFalse();
        }
    }

    @Test
    void failedStartupCleansItsResourcesWithoutDisturbingAnExistingOwner() {
        for (boolean hasOwner : new boolean[]{false, true}) {
            try (var first = hasOwner ? application("A") : null;
                 var failed = new GenericApplicationContext()) {
                var resource = new Resource("failed");
                failed.registerBean(SpringContextHolder.class);
                failed.registerBean(Resource.class, () -> resource);
                failed.registerBean("failure", String.class, () -> {
                    throw new IllegalStateException("controlled startup failure");
                });
                org.assertj.core.api.Assertions.assertThatThrownBy(failed::refresh)
                        .hasRootCauseMessage("controlled startup failure");
                assertThat(resource.closed).isTrue();
                if (hasOwner) {
                    assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("A");
                } else {
                    assertThat(SpringContextHolder.isNotInitialized()).isTrue();
                }
            }
        }
        try (var restarted = application("restart")) {
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("restart");
        }
    }

    @Test
    void parentAndChildLifecycleEventsDoNotWithdrawEachOthersRegistrations() {
        for (boolean parentClosesFirst : new boolean[]{false, true}) {
            try (var parent = consumerApplication("parent"); var child = new GenericApplicationContext(parent)) {
                child.registerBean(SpringContextHolder.class);
                child.registerBean(Resource.class, () -> new Resource("child"));
                child.registerBean(Consumer.class);
                child.refresh();
                assertThat(child.getBean(Consumer.class).read()).isEqualTo("child");
                if (parentClosesFirst) {
                    parent.close();
                    assertThat(child.getBean(Consumer.class).read()).isEqualTo("child");
                    assertThat(SpringContextHolder.isNotInitialized()).isTrue();
                } else {
                    child.close();
                    assertThat(SpringContextHolder.getBean(Consumer.class).get().read()).isEqualTo("parent");
                }
            }
        }
    }

    @Test
    void parallelInitializationSelectsOneOwnerWithoutCouplingInjectedConsumers() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var a = pendingApplication("A", ready, start);
             var b = pendingApplication("B", ready, start);
             var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(a::refresh);
            var second = executor.submit(b::refresh);
            try {
                assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(SpringContextHolder.isNotInitialized()).isTrue();
            } finally {
                start.countDown();
            }
            first.get(5, java.util.concurrent.TimeUnit.SECONDS);
            second.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.getBean(Consumer.class).read()).isEqualTo("A");
            assertThat(b.getBean(Consumer.class).read()).isEqualTo("B");
            var owner = SpringContextHolder.getApplicationContext();
            assertThat(owner).isIn(a, b);
            var rejected = owner == a ? b : a;
            rejected.close();
            assertThat(SpringContextHolder.getBean(Consumer.class).get().read())
                    .isEqualTo(owner == a ? "A" : "B");
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void refreshingAnExistingApplicationReplacesAndClosesThePreviousGeneration() {
        var generation = new java.util.concurrent.atomic.AtomicInteger();
        try (var context = new org.springframework.context.support.AbstractRefreshableApplicationContext() {
            @Override
            protected void loadBeanDefinitions(org.springframework.beans.factory.support.DefaultListableBeanFactory factory) {
                int current = generation.incrementAndGet();
                factory.registerBeanDefinition("holder", new org.springframework.beans.factory.support.RootBeanDefinition(SpringContextHolder.class));
                var resource = new org.springframework.beans.factory.support.RootBeanDefinition(Resource.class);
                resource.setDestroyMethodName("close");
                resource.setInstanceSupplier(() -> {
                    assertThat(SpringContextHolder.isNotInitialized()).isTrue();
                    return new Resource("generation-" + current);
                });
                factory.registerBeanDefinition("resource", resource);
            }
        }) {
            context.refresh();
            var first = SpringContextHolder.getBean(Resource.class).get();
            context.refresh();
            assertThat(first.closed).isTrue();
            assertThat(SpringContextHolder.getBean(Resource.class).get().read()).isEqualTo("generation-2");
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void repeatedAndLateCallbacksCannotWithdrawOrRepublishAnotherGeneration() {
        SpringContextHolder retired;
        GenericApplicationContext old;
        try (var first = application("old")) {
            old = first;
            retired = first.getBean(SpringContextHolder.class);
        }
        try (var next = application("next")) {
            retired.destroy();
            retired.destroy();
            retired.onApplicationEvent(new org.springframework.context.event.ContextRefreshedEvent(old));
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("next");
        }
        retired.onApplicationEvent(new org.springframework.context.event.ContextRefreshedEvent(old));
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void fixedSeedLifecycleSequenceRetainsOnlyTheCurrentOwnerAndClosesAllServices() {
        var random = new java.util.Random(20261003L);
        var contexts = new java.util.ArrayList<GenericApplicationContext>();
        var resources = new java.util.ArrayList<Resource>();
        GenericApplicationContext expected = null;
        try {
            for (int step = 0; step < 120; step++) {
                if (contexts.isEmpty() || contexts.size() < 4 && random.nextBoolean()) {
                    var added = consumerApplication("app-" + step);
                    contexts.add(added);
                    resources.add(added.getBean(Resource.class));
                    if (expected == null) expected = added;
                } else {
                    var closed = contexts.remove(random.nextInt(contexts.size()));
                    closed.close();
                    closed.close();
                    if (expected == closed) expected = null;
                }
                assertThat(SpringContextHolder.getApplicationContext()).as("seed=20261003 step=%s", step).isSameAs(expected);
                for (var active : contexts) {
                    assertThat(active.getBean(Consumer.class).read()).startsWith("app-");
                }
            }
        } finally {
            contexts.forEach(GenericApplicationContext::close);
        }
        assertThat(resources).allMatch(resource -> resource.closed);
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    private static GenericApplicationContext consumerApplication(String name) {
        var context = new GenericApplicationContext();
        context.registerBean(cn.code91.facility.autoconfigure.FacilityCoreAutoConfiguration.class);
        new org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context);
        context.registerBean(Resource.class, () -> new Resource(name));
        context.registerBean(Consumer.class);
        context.refresh();
        return context;
    }

    private static GenericApplicationContext pendingApplication(String name,
            java.util.concurrent.CountDownLatch ready, java.util.concurrent.CountDownLatch start) {
        var context = new GenericApplicationContext();
        context.registerBean(SpringContextHolder.class);
        context.registerBean(Resource.class, () -> {
            ready.countDown();
            await(start);
            return new Resource(name);
        });
        context.registerBean(Consumer.class);
        return context;
    }

    public static final class Resource implements AutoCloseable {
        private final String name;
        private boolean closed;
        public Resource(String name) { this.name = name; }
        public String read() {
            if (closed) throw new IllegalStateException("resource already closed");
            return name;
        }
        @Override public void close() { closed = true; }
    }

    public record Consumer(Resource resource) {
        public String read() { return resource.read(); }
    }

    @Test
    void requiredTypesFailFastWhileNullNamesHaveAbsenceSemantics() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> SpringContextHolder.getBean((Class<?>) null));
        try (var context = application("A")) {
            assertThat(SpringContextHolder.getBean((String) null).isErr()).isTrue();
            assertThat(SpringContextHolder.getBean(null, String.class).isErr()).isTrue();
            assertThat(SpringContextHolder.containsBean(null)).isFalse();
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> SpringContextHolder.getBean("service", null));
            org.assertj.core.api.Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> SpringContextHolder.getBeanNamesForType(null));
        }
    }

    @Test
    void invalidManualRegistrationDoesNotOccupyTheCompatibilitySlot() {
        SpringContextHolder.setApplicationContextManually(null);
        try (var unrefreshed = new GenericApplicationContext()) {
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(() -> SpringContextHolder.setApplicationContextManually(unrefreshed));
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    @Test
    void manualRegistrationDuringDestructionCannotLeaveAStaleOwner() throws Exception {
        var destroying = new java.util.concurrent.CountDownLatch(1);
        var finish = new java.util.concurrent.CountDownLatch(1);
        try (var context = new GenericApplicationContext();
             var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            context.registerBean(org.springframework.beans.factory.DisposableBean.class, () -> () -> {
                destroying.countDown();
                await(finish);
            });
            context.refresh();
            var closing = executor.submit(context::close);
            try {
                assertThat(destroying.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                        .isThrownBy(() -> SpringContextHolder.setApplicationContextManually(context));
            } finally {
                finish.countDown();
                closing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        try (var next = application("next")) {
            assertThat(SpringContextHolder.getBean("service").get()).isEqualTo("next");
        }
    }

    @Test
    void manualRegistrationIsWithdrawnOnRefreshAndDoesNotAccumulateListeners() {
        try (var context = new org.springframework.context.support.AbstractRefreshableApplicationContext() {
            @Override
            protected void loadBeanDefinitions(org.springframework.beans.factory.support.DefaultListableBeanFactory factory) {
                var resource = new org.springframework.beans.factory.support.RootBeanDefinition(Resource.class);
                resource.setDestroyMethodName("close");
                resource.setInstanceSupplier(() -> {
                    assertThat(SpringContextHolder.isNotInitialized()).isTrue();
                    return new Resource("manual");
                });
                factory.registerBeanDefinition("resource", resource);
            }
        }) {
            for (int generation = 0; generation < 12; generation++) {
                context.refresh();
                assertThat(SpringContextHolder.isNotInitialized()).isTrue();
                SpringContextHolder.setApplicationContextManually(context);
                SpringContextHolder.setApplicationContextManually(context);
                assertThat(SpringContextHolder.getBean(Resource.class).get().read()).isEqualTo("manual");
                assertThat(context.getApplicationListeners()).hasSize(1);
            }
        }
        assertThat(SpringContextHolder.isNotInitialized()).isTrue();
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for lifecycle barrier");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
