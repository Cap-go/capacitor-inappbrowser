import { describe, expect, it } from 'bun:test';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const rootDir = path.resolve(import.meta.dir, '..');

describe('visual regression CI wiring (issue #722)', () => {
  it('declares Android and iOS Maestro visual regression jobs in test.yml', () => {
    const workflowPath = path.join(rootDir, '.github', 'workflows', 'test.yml');
    const workflow = readFileSync(workflowPath, 'utf8');

    expect(workflow).toContain('maestro_android_visual_regression:');
    expect(workflow).toContain('maestro_ios_visual_regression:');
    expect(workflow).toContain('maestro:test:android:visual-regression');
    expect(workflow).toContain('run-ios-maestro-visual-regression.sh');
  });

  it('ships Maestro flows and committed screenshot baselines', () => {
    const requiredPaths = [
      '.maestro/visual-regression-android.yaml',
      '.maestro/visual-regression-ios.yaml',
      '.maestro/visual-regression-record-android.yaml',
      '.maestro/visual-regression-record-ios.yaml',
      '.maestro/visual-regression/baselines/android/blank-toolbar-layout.png',
      '.maestro/visual-regression/baselines/ios/blank-toolbar-layout.png',
      'example-app/src/js/visual-regression.js',
      'scripts/run-android-maestro-visual-regression.sh',
      'scripts/run-ios-maestro-visual-regression.sh',
    ];

    for (const relativePath of requiredPaths) {
      expect(existsSync(path.join(rootDir, relativePath))).toBe(true);
    }
  });

  it('exposes package scripts for visual regression Maestro runs', () => {
    const packageJson = JSON.parse(readFileSync(path.join(rootDir, 'package.json'), 'utf8')) as {
      scripts?: Record<string, string>;
    };

    expect(packageJson.scripts?.['maestro:test:android:visual-regression']).toContain(
      'run-android-maestro-visual-regression.sh',
    );
    expect(packageJson.scripts?.['maestro:test:ios:visual-regression']).toContain(
      'run-ios-maestro-visual-regression.sh',
    );
  });
});