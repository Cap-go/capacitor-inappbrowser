import Foundation

enum BlobDownloadFilenameCaptureSupport {
    static let blobDownloadNamesGlobal = "__capgoBlobDownloadNames"

    static let fileNameResolutionJS = """
    (function(blobUrl, matchingLink, fallbackFileName) {
      var names = window.\(blobDownloadNamesGlobal);
      var recorded = names && names[blobUrl];
      return recorded || (matchingLink && matchingLink.getAttribute('download')) || fallbackFileName;
    })
    """

    static let captureScript = """
    (function() {
      if (window.__capgoBlobDownloadFilenameCapture) {
        return;
      }
      window.__capgoBlobDownloadFilenameCapture = true;
      window.\(blobDownloadNamesGlobal) = window.\(blobDownloadNamesGlobal) || {};
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
          window.\(blobDownloadNamesGlobal)[href] = name;
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
    """
}