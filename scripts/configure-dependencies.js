#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const pluginRoot = process.cwd();
const configJson = process.env.CAPACITOR_CONFIG;
const gradlePropertiesPath = path.join(pluginRoot, 'android', 'gradle.properties');
const defaultGeckoVersion = '148.0.20260309125808';

function normalizeDependencyType(value) {
  if (value === true) {
    return 'implementation';
  }

  if (value === false || value == null) {
    return 'none';
  }

  if (typeof value === 'string') {
    const normalized = value.trim();
    if (normalized === 'implementation' || normalized === 'compileOnly' || normalized === 'none') {
      return normalized;
    }
  }

  return 'none';
}

function getGeckoConfig() {
  if (!configJson) {
    return {
      dependencyType: 'none',
      version: defaultGeckoVersion,
    };
  }

  try {
    const config = JSON.parse(configJson);
    const engines = config.plugins?.InAppBrowser?.engines;
    const gecko = engines?.gecko;

    if (typeof gecko === 'object' && gecko !== null && !Array.isArray(gecko)) {
      return {
        dependencyType: normalizeDependencyType(gecko.dependencyType ?? gecko.type ?? gecko.include),
        version: typeof gecko.version === 'string' && gecko.version.trim() ? gecko.version.trim() : defaultGeckoVersion,
      };
    }

    return {
      dependencyType: normalizeDependencyType(gecko),
      version: defaultGeckoVersion,
    };
  } catch (error) {
    console.warn(`[InAppBrowser] Failed to parse CAPACITOR_CONFIG: ${error.message}`);
    return {
      dependencyType: 'none',
      version: defaultGeckoVersion,
    };
  }
}

function updateGradleProperties() {
  const geckoConfig = getGeckoConfig();
  const include = geckoConfig.dependencyType === 'implementation' || geckoConfig.dependencyType === 'compileOnly';

  let existingContent = '';
  if (fs.existsSync(gradlePropertiesPath)) {
    existingContent = fs.readFileSync(gradlePropertiesPath, 'utf8');
  }

  const filteredLines = existingContent
    .split('\n')
    .filter(line => !line.trim().startsWith('# InAppBrowser GeckoView') && !line.trim().startsWith('inAppBrowser.gecko.'));

  while (filteredLines.length > 0 && filteredLines[filteredLines.length - 1].trim() === '') {
    filteredLines.pop();
  }

  filteredLines.push('');
  filteredLines.push('# InAppBrowser GeckoView Dependencies (auto-generated)');
  filteredLines.push(`inAppBrowser.gecko.include=${include ? 'true' : 'false'}`);
  filteredLines.push(`inAppBrowser.gecko.dependencyType=${geckoConfig.dependencyType}`);
  filteredLines.push(`inAppBrowser.gecko.version=${geckoConfig.version}`);
  filteredLines.push('');

  fs.writeFileSync(gradlePropertiesPath, filteredLines.join('\n'), 'utf8');
}

updateGradleProperties();
