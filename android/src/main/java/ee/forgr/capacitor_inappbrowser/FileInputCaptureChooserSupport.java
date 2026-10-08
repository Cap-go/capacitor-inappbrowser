package ee.forgr.capacitor_inappbrowser;

/**
 * Decides whether a file chooser request should use the direct camera capture path and
 * builds JavaScript used to detect {@code capture} on file inputs (including shadow DOM).
 */
final class FileInputCaptureChooserSupport {

    private FileInputCaptureChooserSupport() {}

    static boolean usesCameraCapturePath(boolean imageOnlyAcceptTypes, boolean captureEnabled) {
        return imageOnlyAcceptTypes && captureEnabled;
    }

    /**
     * Document-level click hook installed at document start (and on navigation fallback).
     */
    static String createFileInputCaptureHookScript() {
        return (
            """
            (function() {
              if (window.__capgoFileInputCaptureHook) {
                return;
              }
              window.__capgoFileInputCaptureHook = true;
              window.__capgoLastFileCapture = null;
              """ +
            captureDetectionHelpersJs() +
            """
              var notifyCapture = function(value) {
                var captureValue = value || 'environment';
                window.__capgoLastFileCapture = captureValue;
                try {
                  var bridge = window.AndroidInterface || window.mobileApp;
                  if (bridge && bridge.setFileInputCapture) {
                    bridge.setFileInputCapture(captureValue);
                  }
                } catch (err) {}
              };
              var register = function(doc) {
                if (!doc || doc.__capgoFileInputCaptureRegistered) {
                  return;
                }
                doc.__capgoFileInputCaptureRegistered = true;
                doc.addEventListener('click', function(e) {
                  var captureValue = capgoFindCaptureFromClickEvent(e);
                  if (captureValue) {
                    notifyCapture(captureValue);
                  }
                }, true);
              };
              register(document);
              window.addEventListener('load', function(e) {
                var el = e.target;
                try {
                  if (el && el.tagName === 'IFRAME' && el.contentDocument) {
                    register(el.contentDocument);
                  }
                } catch (err) {}
              }, true);
            })();
            """
        );
    }

    /**
     * Runs when the native chooser opens, after {@code consumeLastFileInputCaptureValue()}.
     */
    static String createChooserFallbackCaptureResolutionScript() {
        return (
            """
            (function() {
              try {
                """ +
            captureDetectionHelpersJs() +
            """
                var cached = window.__capgoLastFileCapture;
                window.__capgoLastFileCapture = null;
                if (cached) {
                  return cached;
                }
                var fromActive = capgoFindCaptureFromActiveElementChain();
                if (fromActive) {
                  return fromActive;
                }
                var inputs = document.querySelectorAll('input[type="file"][capture]');
                if (inputs && inputs.length === 1) {
                  return inputs[0].getAttribute('capture') || 'environment';
                }
                return 'environment';
              } catch (e) {
                return 'environment';
              }
            })();
            """
        );
    }

    private static String captureDetectionHelpersJs() {
        return """
          var capgoNormalizeCapture = function(value) {
            return value || 'environment';
          };
          var capgoIsCaptureFileInput = function(node) {
            return node && node.tagName === 'INPUT' && node.type === 'file' && node.hasAttribute('capture');
          };
          var capgoFindCaptureFromClickEvent = function(e) {
            if (!e) {
              return null;
            }
            var path = e.composedPath ? e.composedPath() : null;
            if (path && path.length) {
              for (var i = 0; i < path.length; i++) {
                var node = path[i];
                if (capgoIsCaptureFileInput(node)) {
                  return capgoNormalizeCapture(node.getAttribute('capture'));
                }
              }
            }
            if (capgoIsCaptureFileInput(e.target)) {
              return capgoNormalizeCapture(e.target.getAttribute('capture'));
            }
            return null;
          };
          var capgoFindCaptureFromActiveElementChain = function() {
            var el = document.activeElement;
            var depth = 0;
            while (el && depth < 32) {
              if (capgoIsCaptureFileInput(el)) {
                return capgoNormalizeCapture(el.getAttribute('capture'));
              }
              if (el.shadowRoot) {
                var inner = el.shadowRoot.activeElement;
                if (inner) {
                  el = inner;
                  depth++;
                  continue;
                }
                var shadowInput = el.shadowRoot.querySelector('input[type="file"][capture]');
                if (shadowInput) {
                  return capgoNormalizeCapture(shadowInput.getAttribute('capture'));
                }
              }
              break;
            }
            return null;
          };
        """;
    }
}
