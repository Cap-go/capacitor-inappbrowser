package ee.forgr.capacitor_inappbrowser;

import java.util.concurrent.Executor;

final class CustomTabsLifecycleSupport {

    interface Binder {
        boolean bindCustomTabsService();

        void unbindCustomTabsService();
    }

    private final Executor executor;
    private final Object lock = new Object();
    private boolean bound = false;
    private boolean bindInProgress = false;

    CustomTabsLifecycleSupport(Executor executor) {
        this.executor = executor;
    }

    void onResume(Binder binder) {
        executor.execute(() -> performBind(binder));
    }

    void onPause(Binder binder) {
        executor.execute(() -> performUnbind(binder));
    }

    boolean isBindingOrBound() {
        synchronized (lock) {
            return bindInProgress || bound;
        }
    }

    boolean isBound() {
        synchronized (lock) {
            return bound;
        }
    }

    private void performBind(Binder binder) {
        synchronized (lock) {
            if (bound || bindInProgress) {
                return;
            }
            bindInProgress = true;
        }

        boolean ok = false;
        try {
            ok = binder.bindCustomTabsService();
        } catch (RuntimeException ignored) {
            // Caller logs bind failures.
        }

        synchronized (lock) {
            bindInProgress = false;
            if (!ok) {
                return;
            }

            bound = true;
        }
    }

    private void performUnbind(Binder binder) {
        synchronized (lock) {
            if (bindInProgress) {
                return;
            }
            if (!bound) {
                return;
            }
            bound = false;
        }

        try {
            binder.unbindCustomTabsService();
        } catch (RuntimeException ignored) {
            // Service may already be unbound.
        }
    }
}
