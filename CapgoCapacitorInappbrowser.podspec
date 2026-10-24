require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name = 'CapgoCapacitorInappbrowser'
  s.version = package['version']
  s.summary = package['description']
  s.license = package['license']
  s.homepage = package['repository']['url']
  s.author = package['author']
  s.source = { :git => package['repository']['url'], :tag => s.version.to_s }
  s.source_files = 'ios/Sources/**/*.{swift,h,m,c,cc,mm,cpp}'
  s.resources = ['ios/Sources/InAppBrowserPlugin/Resources/proxy-bridge.js']
  s.ios.deployment_target = '15.0'
  s.dependency 'Capacitor'
  s.swift_version = '5.1'
  # HTTP/HTTPS proxy scheme registration calls method_exchangeImplementations.
  # Keep it out of the binary unless the app opts in at pod install:
  #   CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE=1 pod install
  if ENV['CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE'] == '1'
    s.pod_target_xcconfig = {
      'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited) CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE'
    }
  end
end
