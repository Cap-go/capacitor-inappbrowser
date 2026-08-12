package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;

public class CustomTabsLifecycleSupportTest {

    private ExecutorService executor;

    @After
    public void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void pauseWhileBindInProgressUnbindsAfterBindCompletes() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        CustomTabsLifecycleSupport support = new CustomTabsLifecycleSupport(executor);
        CountDownLatch bindStarted = new CountDownLatch(1);
        CountDownLatch releaseBind = new CountDownLatch(1);
        AtomicBoolean unbound = new AtomicBoolean(false);

        CustomTabsLifecycleSupport.Binder binder = new CustomTabsLifecycleSupport.Binder() {
            @Override
            public boolean bindCustomTabsService() {
                bindStarted.countDown();
                try {
                    releaseBind.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return true;
            }

            @Override
            public void unbindCustomTabsService() {
                unbound.set(true);
            }
        };

        support.onResume(binder);
        assertTrue(bindStarted.await(1, TimeUnit.SECONDS));

        Thread pauseThread = new Thread(() -> support.onPause(binder));
        pauseThread.start();
        pauseThread.join(1000);

        assertFalse(unbound.get());

        releaseBind.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(unbound.get());
        assertFalse(support.isBound());
    }

    @Test
    public void resumePauseResumePauseWhileBindBlockedEndsUnbound() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        CustomTabsLifecycleSupport support = new CustomTabsLifecycleSupport(executor);
        CountDownLatch bindStarted = new CountDownLatch(1);
        CountDownLatch releaseBind = new CountDownLatch(1);
        AtomicInteger bindCount = new AtomicInteger();
        AtomicInteger unbindCount = new AtomicInteger();

        CustomTabsLifecycleSupport.Binder binder = new CustomTabsLifecycleSupport.Binder() {
            @Override
            public boolean bindCustomTabsService() {
                bindCount.incrementAndGet();
                bindStarted.countDown();
                try {
                    releaseBind.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return true;
            }

            @Override
            public void unbindCustomTabsService() {
                unbindCount.incrementAndGet();
            }
        };

        support.onResume(binder);
        assertTrue(bindStarted.await(1, TimeUnit.SECONDS));

        support.onPause(binder);
        support.onResume(binder);
        support.onPause(binder);

        releaseBind.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(2, bindCount.get());
        assertEquals(2, unbindCount.get());
        assertFalse(support.isBound());
    }

    @Test
    public void failedBindDoesNotMarkServiceAsBound() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        CustomTabsLifecycleSupport support = new CustomTabsLifecycleSupport(executor);
        AtomicBoolean unbound = new AtomicBoolean(false);

        CustomTabsLifecycleSupport.Binder binder = new CustomTabsLifecycleSupport.Binder() {
            @Override
            public boolean bindCustomTabsService() {
                return false;
            }

            @Override
            public void unbindCustomTabsService() {
                unbound.set(true);
            }
        };

        support.onResume(binder);
        support.onPause(binder);

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertFalse(unbound.get());
    }
}
