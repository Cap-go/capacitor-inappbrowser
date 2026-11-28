package ee.forgr.capacitor_inappbrowser;

/**
 * Records {@code download} attribute names for detached blob URLs at click time and provides
 * the resolution snippet used when a blob download is handed off to native code.
 */
final class BlobDownloadFilenameCaptureSupport {

    static final String BLOB_DOWNLOAD_NAMES_GLOBAL = "__capgoBlobDownloadNames";
    static final String FILE_NAME_RESOLUTION_JS = String.format(
        """
        (function(blobUrl, matchingLink, fallbackFileName) {
          var names = window.%s;
          var recorded = names && names[blobUrl];
          return recorded || (matchingLink && matchingLink.getAttribute('download')) || fallbackFileName;
        })
        """,
        BLOB_DOWNLOAD_NAMES_GLOBAL
    );

    private BlobDownloadFilenameCaptureSupport() {}

    /**
     * Document-level hook installed at document start (and on navigation fallback).
     */
    static String createBlobDownloadFilenameCaptureScript() {
        return String.format(
            """
            (function() {
              if (window.__capgoBlobDownloadFilenameCapture) {
                return;
              }
              window.__capgoBlobDownloadFilenameCapture = true;
              window.%s = window.%s || {};
              var recordBlobDownloadName = function(anchor) {
                if (!anchor || !anchor.href || !anchor.hasAttribute('download')) {
                  return;
                }
                var href = anchor.href;
                if (href.indexOf('blob:') !== 0) {
                  return;
                }
                var name = anchor.getAttribute('download');
                if (name) {
                  window.%s[href] = name;
                }
              };
              var anchorPrototype = HTMLAnchorElement.prototype;
              var originalClick = anchorPrototype.click;
              anchorPrototype.click = function() {
                recordBlobDownloadName(this);
                return originalClick.call(this);
              };
              var originalDispatchEvent = anchorPrototype.dispatchEvent;
              anchorPrototype.dispatchEvent = function(event) {
                if (event && event.type === 'click') {
                  recordBlobDownloadName(this);
                }
                return originalDispatchEvent.call(this, event);
              };
              var registerClickCapture = function(doc) {
                if (!doc || doc.__capgoBlobDownloadFilenameCaptureRegistered) {
                  return;
                }
                doc.__capgoBlobDownloadFilenameCaptureRegistered = true;
                doc.addEventListener('click', function(event) {
                  var target = event.target;
                  var anchor = target && target.closest ? target.closest('a[download]') : null;
                  if (anchor) {
                    recordBlobDownloadName(anchor);
                  }
                }, true);
              };
              registerClickCapture(document);
              window.addEventListener('load', function(event) {
                var el = event.target;
                try {
                  if (el && el.tagName === 'IFRAME' && el.contentDocument) {
                    registerClickCapture(el.contentDocument);
                  }
                } catch (err) {}
              }, true);
            })();
            """,
            BLOB_DOWNLOAD_NAMES_GLOBAL,
            BLOB_DOWNLOAD_NAMES_GLOBAL,
            BLOB_DOWNLOAD_NAMES_GLOBAL
        );
    }
}
