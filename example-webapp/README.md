# InAppBrowser Test Webapp

This PHP webapp is designed to test various navigation scenarios that might cause back button issues in the Capacitor InAppBrowser plugin.

## Setup

1. Place this folder in a web server with PHP support
2. Access `index.php` to start testing

## Test Scenarios

### 1. Basic Navigation (`index.php`, `page1.php`, `page2.php`, `page3.php`)
- Regular link navigation
- Tests basic back button functionality

### 2. Redirects (`redirect.php`, `redirect-chain.php`)
- 301 and 302 redirects
- Redirect chains that might confuse navigation history
- Tests if redirects affect back button availability

### 3. Form Submissions (`form-handler.php`)
- POST form submissions followed by redirects
- Tests back button behavior after form processing

### 4. AJAX & Dynamic Content (`page2.php`)
- AJAX content loading
- History API usage (pushState, replaceState)
- Dynamic URL changes without page reload

### 5. Complex Navigation (`page3.php`)
- Iframe navigation
- Multiple redirect scenarios
- Tests complex navigation patterns

### 6. Deep Links (`deep-link-test.php`)
- Deep linking with query parameters
- Fragment navigation (hash links)
- Simulated app-to-app deep linking

### 7. External Redirects (`external-redirect.php`)
- Redirects to external sites
- Tests back button behavior after external navigation

## Back Button Issue Testing

The webapp is specifically designed to reproduce the reported issue where:

1. Navigate through several pages
2. Back button becomes available
3. On certain URL changes, back button becomes greyed out unexpectedly
4. Further navigation makes it available again
5. You can then go back multiple steps

## Usage with InAppBrowser

Open this webapp in the InAppBrowser with `toolbarType: ToolBarType.NAVIGATION` to test the back button behavior.

## Files

- `index.php` - Home page with navigation options
- `page1.php` - Basic page with navigation tests
- `page2.php` - AJAX and dynamic content tests
- `page3.php` - Complex navigation and iframe tests
- `redirect.php` - Simple redirect handler
- `redirect-chain.php` - Multiple redirect chain handler
- `form-handler.php` - Form submission processor
- `external-redirect.php` - External site redirect test
- `deep-link-test.php` - Deep linking scenarios
- `README.md` - This documentation

## Testing Checklist

- [ ] Basic page-to-page navigation
- [ ] Back button availability after redirects
- [ ] Form submission and redirect back button behavior
- [ ] AJAX content loading effects on back button
- [ ] History API pushState/replaceState effects
- [ ] Iframe navigation interference
- [ ] Deep link navigation back button state
- [ ] External redirect back button behavior
- [ ] Fragment navigation effects
- [ ] Multiple redirect chain back button state
