#!/usr/bin/env python3
"""Emulator-only process-death smoke test. Never opens a dialer or SMS application."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'pl.finrev.smssamsung'
COMPONENT = PACKAGE + '/.MainActivity'
MARKER = 'SyntheticProcessDraft'
REPORTS = Path('app/build/reports/process-death')
REPORTS.mkdir(parents=True, exist_ok=True)


def adb(*args, check=True):
    result = subprocess.run(['adb', *args], capture_output=True)
    if check and result.returncode:
        raise RuntimeError('ADB command failed: ' + result.stderr.decode(errors='replace')[:500])
    return result.stdout


def message_node():
    for _ in range(5):
        adb('shell', 'uiautomator', 'dump', '/sdcard/sms-samsung-smoke.xml')
        root = ET.fromstring(adb('exec-out', 'cat', '/sdcard/sms-samsung-smoke.xml'))
        for node in root.iter('node'):
            if node.get('resource-id') == PACKAGE + ':id/message':
                bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                if len(bounds) == 4 and bounds[3] > bounds[1] and bounds[2] > bounds[0]:
                    return node, bounds
        size = re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size').decode())[-1]
        width, height = map(int, size)
        adb('shell', 'input', 'swipe', str(width // 2), str(height * 8 // 10), str(width // 2), str(height * 3 // 10), '300')
    raise AssertionError('Message field not visible')


assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'This script requires an emulator'
assert adb('shell', 'getprop', 'ro.build.version.sdk').strip() == b'36', 'This script requires API 36'
# connectedAndroidTest removes its installations as part of device cleanup.
adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
adb('shell', 'am', 'force-stop', PACKAGE)
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'wm', 'dismiss-keyguard')
adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
baseline, bounds = message_node()
baseline_text = baseline.get('text')
adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
adb('shell', 'input', 'text', MARKER)
node, _ = message_node()
assert node.get('text') == MARKER, 'Synthetic draft was not entered'
old_pid = adb('shell', 'pidof', PACKAGE).strip()
assert old_pid, 'Application process missing'
adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
# am kill preserves the task and its saved state; force-stop alone would not test restoration.
for _ in range(20):
    adb('shell', 'am', 'kill', PACKAGE)
    if not adb('shell', 'pidof', PACKAGE, check=False).strip():
        break
    time.sleep(0.5)
else:
    raise AssertionError('Background process did not terminate')
adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
node, _ = message_node()
assert node.get('text') == baseline_text, 'Customer draft restored after process death'
assert adb('shell', 'pidof', PACKAGE).strip() != old_pid, 'Process was not recreated'
# FLAG_SECURE intentionally prevents capturing the app screen. Emit only pass/fail metadata.
(REPORTS / 'result.json').write_text(json.dumps({
    'device_api': adb('shell', 'getprop', 'ro.build.version.sdk').decode().strip(),
    'background_process_killed': True,
    'draft_not_restored': True,
    'sms_editor_launched': False,
}, indent=2) + '\n')
adb('shell', 'rm', '-f', '/sdcard/sms-samsung-smoke.xml')
adb('shell', 'am', 'force-stop', PACKAGE)
print('PASS: process death discards the in-memory draft; no SMS editor launched')
