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
    private boolean unbindPending = false;

    CustomTabsLifecycleSupport(Executor executor) {
        this.executor = executor;
    }

    void onResume(Binder binder) {
        executor.execute(() -> performBind(binder));
    }

    void onPause(Binder binder) {
        synchronized (lock) {
            if (bindInProgress) {
                unbindPending = true;
                return;
            }
        }
        executor.execute(() -> performUnbind(binder));
    }

    boolean isBindingOrBound() {
        synchronized (lock) {
            return bindInProgress || bound;
        }
    }

    boolean isUnbindPending() {
        synchronized (lock) {
            return unbindPending;
        }
    }

    private void performBind(Binder binder) {
        synchronized (lock) {
            if (bound || bindInProgress) {
                return;
            }
            bindInProgress = true;
            unbindPending = false;
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

            if (unbindPending) {
                unbindPending = false;
                try {
                    binder.unbindCustomTabsService();
                } catch (RuntimeException ignored) {
                    // Service may already be unbound.
                }
                bound = false;
                return;
            }

            bound = true;
        }
    }

    private void performUnbind(Binder binder) {
        synchronized (lock) {
            if (bindInProgress) {
                unbindPending = true;
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
