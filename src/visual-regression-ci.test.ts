import { describe, expect, it } from 'bun:test';
import { spawnSync } from 'node:child_process';
import {
  chmodSync,
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const rootDir = path.resolve(import.meta.dir, '..');

const copyTree = (relativePaths: string[], workspaceDir: string) => {
  for (const relativePath of relativePaths) {
    const source = path.join(rootDir, relativePath);
    const target = path.join(workspaceDir, relativePath);
    mkdirSync(path.dirname(target), { recursive: true });
    copyFileSync(source, target);
  }
};

const createWorkspace = () => {
  const workspaceDir = mkdtempSync(path.join(tmpdir(), 'maestro-vr-'));
  const stubLogPath = path.join(workspaceDir, 'maestro-stub.log');
  const stubBinDir = path.join(workspaceDir, 'bin');
  const emptyHome = path.join(workspaceDir, 'home');

  mkdirSync(stubBinDir, { recursive: true });
  mkdirSync(emptyHome, { recursive: true });
  mkdirSync(path.join(workspaceDir, '.maestro', 'visual-regression', 'baselines', 'android'), {
    recursive: true,
  });
  mkdirSync(path.join(workspaceDir, '.maestro', 'visual-regression', 'baselines', 'ios'), {
    recursive: true,
  });

  copyTree(
    [
      'scripts/run-maestro-visual-regression.sh',
      '.maestro/visual-regression-assert.yaml',
      '.maestro/visual-regression-record.yaml',
    ],
    workspaceDir,
  );

  const stubMaestro = path.join(stubBinDir, 'maestro');
  writeFileSync(
    stubMaestro,
    `#!/usr/bin/env sh
log="\${MAESTRO_STUB_LOG:-/dev/null}"
printf '%s\\n' "maestro $*" >> "$log"
for arg in "$@"; do
  case "$arg" in
    *visual-regression-record.yaml*) printf '%s\\n' RECORD_FLOW >> "$log" ;;
    *visual-regression-assert.yaml*) printf '%s\\n' ASSERT_FLOW >> "$log" ;;
  esac
done
exit 0
`,
  );
  chmodSync(stubMaestro, 0o755);
  chmodSync(path.join(workspaceDir, 'scripts', 'run-maestro-visual-regression.sh'), 0o755);

  return { workspaceDir, stubLogPath, stubBinDir, emptyHome };
};

const runScript = (
  workspace: ReturnType<typeof createWorkspace>,
  platform: 'android' | 'ios',
  mode: 'assert' | 'record' = 'assert',
) => {
  const scriptPath = path.join(workspace.workspaceDir, 'scripts', 'run-maestro-visual-regression.sh');
  return spawnSync('sh', [scriptPath, platform, mode], {
    cwd: workspace.workspaceDir,
    env: {
      ...process.env,
      HOME: workspace.emptyHome,
      PATH: `${workspace.stubBinDir}:${process.env.PATH ?? ''}`,
      MAESTRO_STUB_LOG: workspace.stubLogPath,
      APP_ID: 'app.capgo.inappbrowser',
    },
    encoding: 'utf8',
  });
};

const baselinePath = (workspaceDir: string, platform: 'android' | 'ios') =>
  path.join(workspaceDir, '.maestro', 'visual-regression', 'baselines', platform, 'blank-toolbar-layout.png');

describe('visual regression CI wiring (issue #722)', () => {
  it('ships Maestro flows, script, and committed screenshot baselines', () => {
    const requiredPaths = [
      '.maestro/visual-regression-assert.yaml',
      '.maestro/visual-regression-record.yaml',
      'example-app/src/visual-regression-probe.html',
      'example-app/src/js/maestro-native-harness.js',
      '.maestro/visual-regression/baselines/android/blank-toolbar-layout.png',
      '.maestro/visual-regression/baselines/ios/blank-toolbar-layout.png',
      'example-app/src/js/visual-regression.js',
      'scripts/run-maestro-visual-regression.sh',
      'scripts/run-maestro-native-ui-engine.sh',
      '.maestro/visual-regression/open-layout-probe.yaml',
      'example-app/.maestro/native-ui-engine-off-android.yaml',
      'example-app/.maestro/native-ui-engine-off-ios.yaml',
      'example-app/.maestro/native-ui-engine-on-android.yaml',
      'example-app/.maestro/native-ui-engine-on-ios.yaml',
      'example-app/.maestro/native-ui-engine-fail-android.yaml',
      'example-app/.maestro/native-ui-engine-fail-ios.yaml',
    ];

    for (const relativePath of requiredPaths) {
      expect(existsSync(path.join(rootDir, relativePath))).toBe(true);
    }

    for (const relativePath of [
      '.maestro/visual-regression/baselines/android/blank-toolbar-layout.png',
      '.maestro/visual-regression/baselines/ios/blank-toolbar-layout.png',
    ]) {
      expect(readFileSync(path.join(rootDir, relativePath)).length).toBeGreaterThan(500);
    }
  });

  it('exposes package scripts for visual regression Maestro runs', () => {
    const packageJson = JSON.parse(readFileSync(path.join(rootDir, 'package.json'), 'utf8')) as {
      scripts?: Record<string, string>;
    };

    expect(packageJson.scripts?.['maestro:test:android:visual-regression']).toContain(
      'prepare-maestro-visual-regression',
    );
    expect(packageJson.scripts?.['maestro:test:ios:visual-regression']).toContain('run-maestro-visual-regression.sh');
  });

  describe('run-maestro-visual-regression.sh (stub maestro)', () => {
    it('assert mode exits 1 when the baseline is missing', () => {
      const workspace = createWorkspace();
      try {
        const result = runScript(workspace, 'android', 'assert');
        expect(result.status).toBe(1);
        expect(result.stdout ?? result.stderr ?? '').toContain('Assert mode never records');
        expect(existsSync(workspace.stubLogPath)).toBe(false);
      } finally {
        rmSync(workspace.workspaceDir, { recursive: true, force: true });
      }
    });

    it('assert mode exits 1 when the baseline is smaller than 500 bytes', () => {
      const workspace = createWorkspace();
      const imagePath = baselinePath(workspace.workspaceDir, 'android');
      try {
        writeFileSync(imagePath, Buffer.alloc(100, 0));
        const beforeMtime = statSync(imagePath).mtimeMs;
        const result = runScript(workspace, 'android', 'assert');
        expect(result.status).toBe(1);
        expect(result.stdout ?? result.stderr ?? '').toContain('too small');
        expect(statSync(imagePath).mtimeMs).toBe(beforeMtime);
        expect(statSync(imagePath).size).toBe(100);
        const stubLog = existsSync(workspace.stubLogPath) ? readFileSync(workspace.stubLogPath, 'utf8') : '';
        expect(stubLog).not.toContain('RECORD_FLOW');
        expect(stubLog).not.toContain('ASSERT_FLOW');
      } finally {
        rmSync(workspace.workspaceDir, { recursive: true, force: true });
      }
    });

    it('assert mode never records and only runs the assert flow when the baseline is valid', () => {
      const workspace = createWorkspace();
      const imagePath = baselinePath(workspace.workspaceDir, 'ios');
      try {
        writeFileSync(imagePath, Buffer.alloc(600, 1));
        const beforeMtime = statSync(imagePath).mtimeMs;
        const result = runScript(workspace, 'ios', 'assert');
        expect(result.status).toBe(0);
        expect(statSync(imagePath).mtimeMs).toBe(beforeMtime);
        expect(statSync(imagePath).size).toBe(600);
        const stubLog = readFileSync(workspace.stubLogPath, 'utf8');
        expect(stubLog).toContain('ASSERT_FLOW');
        expect(stubLog).not.toContain('RECORD_FLOW');
        expect(stubLog).toContain('visual-regression-assert.yaml');
      } finally {
        rmSync(workspace.workspaceDir, { recursive: true, force: true });
      }
    });
  });
});
