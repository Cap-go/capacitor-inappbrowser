package ee.forgr.capacitor_inappbrowser;

/**
 * Decides whether a file chooser request should use the direct camera capture path.
 */
final class FileInputCaptureChooserSupport {

    private FileInputCaptureChooserSupport() {}

    static boolean usesCameraCapturePath(boolean imageOnlyAcceptTypes, boolean captureEnabled) {
        return imageOnlyAcceptTypes && captureEnabled;
    }
}
