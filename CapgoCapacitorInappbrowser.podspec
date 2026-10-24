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
  # cap sync rewrites this block. Default compiles the stub.
  # App Store guideline 2.5.2 can reject method_exchangeImplementations even
  # when the app never enables proxy.
  # plugins.CapgoInAppBrowser.httpProxySwizzle: true compiles the swizzle instead.
  # http-proxy-swizzle:begin
  # http-proxy-swizzle:disabled
  s.exclude_files = 'ios/Sources/InAppBrowserPlugin/HttpProxySchemeSwizzle.swift'
  # http-proxy-swizzle:end
end
