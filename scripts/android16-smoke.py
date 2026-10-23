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


def adb(*args, check=True):
    result = subprocess.run(['adb', *args], capture_output=True)
    if check and result.returncode:
        raise RuntimeError('ADB command failed: ' + result.stderr.decode(errors='replace')[:500])
    return result.stdout


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/sms-samsung-smoke.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/sms-samsung-smoke.xml'))


def rectangle(node):
    values = list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
    return values if len(values) == 4 and values[3] > values[1] and values[2] > values[0] else None


def ime_is_shown():
    # UIAutomator's active-window tree can omit the IME window altogether.
    state = adb('shell', 'dumpsys', 'input_method').decode(errors='replace')
    shown = re.search(r'\b(?:mInputShown|mIsInputViewShown|isInputViewShown)\s*[=:]\s*(true|false)\b', state)
    if shown:
        return shown.group(1) == 'true'
    visibility = re.search(r'\bmImeWindowVis=(0x[0-9a-fA-F]+|[0-9]+)\b', state)
    return bool(visibility and int(visibility.group(1), 0) & 2)


def hide_keyboard():
    # A BACK without an IME could finish the Activity, so check current service state.
    if not ime_is_shown():
        return False
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    for _ in range(10):
        if not ime_is_shown():
            return True
        time.sleep(0.2)
    raise AssertionError('IME remained visible after dismissal')


def message_node():
    root = None
    for attempt in range(10):
        root = hierarchy()
        for node in root.iter('node'):
            if node.get('resource-id') == PACKAGE + ':id/message':
                bounds = rectangle(node)
                if bounds:
                    return node, bounds
        # Stay inside the app's scroll view rather than swiping over a keyboard.
        viewport = next((rectangle(node) for node in root.iter('node')
                         if node.get('resource-id') == PACKAGE + ':id/composer_scroll'), None)
        if not viewport:
            break
        left, top, right, bottom = viewport
        x = (left + right) // 2
        lower = top + (bottom - top) * 8 // 10
        upper = top + (bottom - top) * 3 // 10
        start, end = (lower, upper) if attempt < 5 else (upper, lower)
        adb('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '300')
    # Structural diagnostics only: no contacts, private text or screenshots.
    if root is not None:
        (REPORTS / 'visibility-diagnostic.json').write_text(json.dumps([
            {key: node.get(key) for key in ('resource-id', 'class', 'bounds', 'scrollable')}
            for node in root.iter('node') if node.get('package') == PACKAGE
        ], indent=2) + '\n')
    raise AssertionError('Message field not visible')


def main():
    REPORTS.mkdir(parents=True, exist_ok=True)
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
    keyboard_dismissed = hide_keyboard()
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
        'keyboard_dismissed': keyboard_dismissed,
        'sms_editor_launched': False,
    }, indent=2) + '\n')
    adb('shell', 'rm', '-f', '/sdcard/sms-samsung-smoke.xml')
    adb('shell', 'am', 'force-stop', PACKAGE)
    print('PASS: process death discards the in-memory draft; no SMS editor launched')


if __name__ == '__main__':
    main()
