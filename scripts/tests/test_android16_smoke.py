"""Regression coverage for IME states omitted from UIAutomator's active-window tree."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('process_death_smoke', Path(__file__).parents[1] / 'android16-smoke.py')
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class ImeControlTest(unittest.TestCase):
    def test_service_state_detects_visible_keyboard_without_accessibility_nodes(self):
        for state in (b'mShowRequested=true mInputShown=true', b'isInputViewShown: true', b'mImeWindowVis=0x3'):
            with self.subTest(state=state), patch.object(smoke, 'adb', return_value=state):
                self.assertTrue(smoke.ime_is_shown())

    def test_hidden_hardware_keyboard_does_not_trigger_back(self):
        for state in (b'mInputShown=false', b'mImeWindowVis=0x1', b'mImeWindowVis=0x0', b''):
            with self.subTest(state=state), patch.object(smoke, 'adb', return_value=state) as adb:
                self.assertFalse(smoke.hide_keyboard())
                self.assertEqual(adb.call_args_list, [unittest.mock.call('shell', 'dumpsys', 'input_method')])

    def test_dismisses_once_and_waits_for_service_to_report_hidden(self):
        with patch.object(smoke, 'ime_is_shown', side_effect=[True, False]), patch.object(smoke, 'adb') as adb:
            self.assertTrue(smoke.hide_keyboard())
            adb.assert_called_once_with('shell', 'input', 'keyevent', 'KEYCODE_BACK')

    def test_ime_failure_is_reported_instead_of_silently_skipping_process_death(self):
        with patch.object(smoke, 'ime_is_shown', return_value=True), patch.object(smoke, 'adb') as adb, patch.object(smoke.time, 'sleep'):
            with self.assertRaisesRegex(AssertionError, 'IME remained visible'):
                smoke.hide_keyboard()
            adb.assert_called_once_with('shell', 'input', 'keyevent', 'KEYCODE_BACK')

    def test_current_hidden_state_takes_priority_over_stale_window_visibility(self):
        with patch.object(smoke, 'adb', return_value=b'mInputShown=false mImeWindowVis=0x3'):
            self.assertFalse(smoke.ime_is_shown())


if __name__ == '__main__':
    unittest.main()
