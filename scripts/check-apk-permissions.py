#!/usr/bin/env python3
"""Enforce least permissions for both APK variants."""
import argparse
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--aapt', required=True)
parser.add_argument('--debug', required=True)
parser.add_argument('--release', required=True)
args = parser.parse_args()
for variant in ('debug', 'release'):
    output = subprocess.check_output([args.aapt, 'dump', 'permissions', getattr(args, variant)], text=True)
    permissions = set(re.findall(r"uses-permission: name='([^']+)'", output))
    forbidden = {'android.permission.SEND_SMS', 'android.permission.READ_SMS', 'android.permission.RECEIVE_SMS', 'android.permission.READ_CONTACTS', 'android.permission.WRITE_CONTACTS', 'android.permission.READ_CALL_LOG', 'android.permission.RECORD_AUDIO'}
    assert not permissions & forbidden, 'SMS permission found'
    assert ('android.permission.INTERNET' in permissions) , 'Missing catalog networking permission'
    print(f'PASS: {variant} permissions')
