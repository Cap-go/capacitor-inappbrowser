package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        AtomicBoolean bound = new AtomicBoolean(false);
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
                bound.set(true);
                return true;
            }

            @Override
            public void unbindCustomTabsService() {
                unbound.set(true);
            }
        };

        support.onResume(binder);
        assertTrue(bindStarted.await(1, TimeUnit.SECONDS));

        support.onPause(binder);
        releaseBind.countDown();

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(bound.get());
        assertTrue(unbound.get());
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
